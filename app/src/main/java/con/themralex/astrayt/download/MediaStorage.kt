package con.themralex.astrayt.download
import android.content.*
import android.graphics.*
import android.media.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.palette.graphics.Palette
import con.themralex.astrayt.data.db.*
import con.themralex.astrayt.youtube.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import java.io.*
import java.nio.ByteBuffer
import javax.inject.*
@Singleton class MediaStorage @Inject constructor(@ApplicationContext private val context:Context,private val dao:AstraDao,private val client:OkHttpClient,private val subtitles:SubtitleDownloads,private val timelines:con.themralex.astrayt.timeline.TimelineStore) {
    private val artLocks=Array(16){Mutex()}
    private fun validArt(file:File):Boolean {if(!file.isFile || file.length()==0L)return false;val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(file.absolutePath,bounds);return bounds.outWidth>0 && bounds.outHeight>0}
    suspend fun prefetchArt(details:VideoDetails)=coroutineScope {
        val art=File(context.filesDir,"art").apply{mkdirs()}
        val thumb=async{saveArt(details.thumbnail,File(art,"${details.ytId}.webp"),720)}
        val avatar=async{saveArt(details.channelAvatar,File(art,"channel-${details.channelId.hashCode()}.webp"),192)}
        thumb.await();avatar.await();Unit
    }
    // A killed mux can leave a pending MediaStore item. Reconcile it before retrying.
    suspend fun recover(row:DownloadEntity,dir:File):Boolean=withContext(Dispatchers.IO){
        val journal=File(dir,"output.uri")
        if(!journal.exists())return@withContext false
        val uri=journal.readText().trim()
        val committed=dao.byYoutubeId(row.ytId)?.contentUri==uri
        if(!committed && uri.startsWith("content://media/"))context.contentResolver.delete(Uri.parse(uri),null,null)
        if(!committed)journal.delete()
        File(context.cacheDir,"mux-${row.id}.mp4").delete()
        File(context.cacheDir,"mux-${row.id}.webm").delete()
        File(context.cacheDir,"mux-${row.id}.m4a").delete()
        committed
    }
    suspend fun finish(row:DownloadEntity,options:DownloadOptions,details:VideoDetails,plan:QualityOption,video:File?,audio:File?,requestedCuts:List<Cut> = emptyList(),annotations:con.themralex.astrayt.timeline.TimelineCapture=con.themralex.astrayt.timeline.TimelineCapture(emptyList(),emptyList(),"")):List<Cut> =withContext(Dispatchers.IO) {
        val audioOnly=options.audioOnly;val mime=if(audioOnly)if(plan.container=="webm")"audio/webm"else"audio/mp4" else if(plan.container=="webm")"video/webm"else"video/mp4"
        val name=details.title.replace(Regex("[\\/:*?\"<>|]"),"_").take(120)+"."+plan.container
        var legacy:File?=null
        val values=ContentValues().apply{put(MediaStore.MediaColumns.DISPLAY_NAME,name);put(MediaStore.MediaColumns.TITLE,details.title);put(MediaStore.MediaColumns.MIME_TYPE,mime);put(MediaStore.Audio.Media.ARTIST,details.channelName);if(Build.VERSION.SDK_INT>=29){put(MediaStore.MediaColumns.RELATIVE_PATH,(if(audioOnly)Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES)+"/AstraYT");put(MediaStore.MediaColumns.IS_PENDING,1)}else{@Suppress("DEPRECATION") val folder=File(Environment.getExternalStoragePublicDirectory(if(audioOnly)Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES),"AstraYT").apply{mkdirs()};legacy=File(folder,"${System.currentTimeMillis()}-$name");put(MediaStore.MediaColumns.DATA,legacy!!.absolutePath)}}
        val collection=if(audioOnly)MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val uri=context.contentResolver.insert(collection,values) ?: throw IOException("STORAGE")
        val journal=File(context.noBackupFilesDir,"dl/${row.id}/output.uri")
        try {
            journal.parentFile!!.mkdirs()
            FileOutputStream(journal).use{it.write(uri.toString().toByteArray(Charsets.UTF_8));it.fd.sync()}
            val cuts=alignedCuts(if(audioOnly)audio!! else video!!,requestedCuts,details.durationMs*1000,!audioOnly)
            val removedMs=cuts.sumOf{it.endUs-it.startUs}/1000
            if(removedMs>=details.durationMs && details.durationMs>0)throw IOException("SPONSORBLOCK_EMPTY")
            // Fetch artwork while copying samples, so a slow thumbnail is not added to mux time.
            coroutineScope {
            val art=File(context.filesDir,"art").apply{mkdirs()}
            val thumbTask=async {saveArt(details.thumbnail,File(art,"${row.ytId}.webp"),720)}
            val avatarTask=async {saveArt(details.channelAvatar,File(art,"channel-${details.channelId.hashCode()}.webp"),192)}
            if(cuts.isEmpty() && ((audioOnly && !plan.extractAudio) || (!audioOnly && plan.audio==null))){copyToStore(if(audioOnly)audio ?: throw IOException("Missing audio")else video ?: throw IOException("Missing video"),uri,"w")}
            else {
                try {context.contentResolver.openFileDescriptor(uri,"rw")!!.use{fd->mux(if(audioOnly)audio!! else video!!,if(audioOnly)null else audio,plan.container,fd.fileDescriptor,null,cuts,audioOnly,options.audioLanguage)}}
                catch(e:kotlinx.coroutines.CancellationException){throw e}
                catch(e:Exception){val temp=File(context.cacheDir,"mux-${row.id}.${plan.container}");try{mux(if(audioOnly)audio!! else video!!,if(audioOnly)null else audio,plan.container,null,temp.absolutePath,cuts,audioOnly,options.audioLanguage,batched=false);copyToStore(temp,uri,"wt")}finally{temp.delete()}}
            }
            withContext(NonCancellable){
            if(Build.VERSION.SDK_INT>=29)context.contentResolver.update(uri,ContentValues().apply{put(MediaStore.MediaColumns.IS_PENDING,0)},null,null)
            val thumb=thumbTask.await();val avatar=avatarTask.await()
            val color=thumb?.let{BitmapFactory.decodeFile(it.absolutePath)}?.let{bitmap->try{Palette.from(bitmap).generate().getDominantColor(0)}finally{bitmap.recycle()}} ?: 0
            val size=context.contentResolver.openFileDescriptor(uri,"r")?.use{it.statSize}?.coerceAtLeast(0) ?: 0
            val previous=dao.byYoutubeId(row.ytId)
            dao.insertVideo(VideoEntity(id=previous?.id ?: 0,positionMs=previous?.positionMs?.let{CutTimeline.outputTime(it*1000,cuts)?.div(1000) ?: 0}?.coerceAtMost((details.durationMs-removedMs).coerceAtLeast(0)) ?: 0,ytId=row.ytId,title=details.title,channelId=details.channelId,channelName=details.channelName,channelAvatarPath=avatar?.absolutePath.orEmpty(),thumbnailPath=thumb?.absolutePath.orEmpty(),durationMs=(details.durationMs-removedMs).coerceAtLeast(0),viewCount=details.viewCount,uploadDateMs=details.uploadDateMs,description=details.description,contentUri=uri.toString(),fileSizeBytes=size,mimeType=mime,height=plan.height,kind=row.kind,audioLangTag=options.audioLanguage ?: details.tracks.firstOrNull{it.id==options.trackId}?.locale.orEmpty(),audioLabel=options.audioLabel ?: details.tracks.firstOrNull{it.id==options.trackId}?.displayName.orEmpty(),dominantColor=color,likeCount=details.likeCount,removedDurationMs=removedMs))
            subtitles.saveTimeline(row.ytId,cuts)
            timelines.save(row.ytId,uri.toString(),annotations,cuts,details.durationMs)
            previous?.takeIf{it.contentUri!=uri.toString()}?.let{old->runCatching{context.contentResolver.delete(Uri.parse(old.contentUri),null,null)}}
            }
        };cuts}catch(e:Exception){withContext(NonCancellable){
            if(dao.byYoutubeId(row.ytId)?.contentUri!=uri.toString()){context.contentResolver.delete(uri,null,null);legacy?.delete();journal.delete()}
        };throw e}
    }
    private suspend fun copyToStore(file:File,uri:Uri,mode:String) {
        context.contentResolver.openOutputStream(uri,mode)?.use { output->
            file.inputStream().use { input->
                val buffer=ByteArray(1024*1024)
                while(true){currentCoroutineContext().ensureActive();val count=input.read(buffer);if(count<0)break;output.write(buffer,0,count)}
            }
        } ?: throw IOException("STORAGE")
    }
    private fun alignedCuts(file:File,cuts:List<Cut>,duration:Long,video:Boolean):List<Cut> {
        val normalized=CutTimeline.normalize(cuts,duration)
        if(!video || normalized.isEmpty())return normalized
        val extractor=MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val track=(0 until extractor.trackCount).firstOrNull{extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/")==true} ?: throw IOException("Missing video track")
            extractor.selectTrack(track)
            return CutTimeline.normalize(normalized.map { cut->
                extractor.seekTo(cut.startUs,MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                val start=extractor.sampleTime.coerceAtLeast(0)
                extractor.seekTo(cut.endUs,MediaExtractor.SEEK_TO_NEXT_SYNC)
                val end=extractor.sampleTime.takeIf{it>=cut.endUs} ?: duration
                Cut(start,end)
            },duration)
        }finally{extractor.release()}
    }
    private suspend fun mux(video:File,audio:File?,container:String,fd:FileDescriptor?,path:String?,cuts:List<Cut>,audioOnly:Boolean=false,audioLanguage:String?=null,batched:Boolean=true) {
        val mux=SampleMuxer(container,fd,path,batched)
        val extractors=mutableListOf<MediaExtractor>();var started=false
        try {
            data class Input(val extractor:MediaExtractor,val tracks:Map<Int,Int>,var time:Long)
            val inputs=mutableListOf<Input>()
            var capacity=if(Build.VERSION.SDK_INT>=28)1024*1024 else 4*1024*1024
            val sources=if(audio==null)listOf(video)else listOf(video,audio)
            sources.forEachIndexed { source,file->
                // A progressive file is opened once, selecting its tracks together in file order.
                val ex=MediaExtractor();extractors.add(ex);ex.setDataSource(file.absolutePath)
                val indices=mutableMapOf<Int,Int>()
                for(index in 0 until ex.trackCount){
                    val track=ex.getTrackFormat(index);val mime=track.getString(MediaFormat.KEY_MIME).orEmpty()
                    val select=if(audioOnly)mime.startsWith("audio/")else if(audio==null)mime.startsWith("video/")||mime.startsWith("audio/")else mime.startsWith(if(source==0)"video/"else"audio/")
                    if(!select)continue
                    if(mime.startsWith("audio/") && !audioLanguage.isNullOrBlank())track.setString(MediaFormat.KEY_LANGUAGE,runCatching{java.util.Locale.forLanguageTag(audioLanguage).isO3Language}.getOrDefault(audioLanguage))
                    if(track.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE))capacity=maxOf(capacity,track.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                    indices[index]=mux.addTrack(track);ex.selectTrack(index)
                }
                if(indices.isNotEmpty())inputs.add(Input(ex,indices,ex.sampleTime))
            }
            check(inputs.isNotEmpty())
            mux.start();started=true
            var buffer=ByteBuffer.allocateDirect(capacity);val info=MediaCodec.BufferInfo();var samples=0
            while(true){
                currentCoroutineContext().ensureActive()
                val next=inputs.minByOrNull{if(it.time>=0)it.time else Long.MAX_VALUE}?.takeIf{it.time>=0} ?: break
                val other=inputs.firstOrNull{it!==next && it.time>=0}?.time
                val limit=MuxSchedule.batchEnd(next.time,other)
                val extractor=next.extractor
                do {
                    if(++samples%64==0)currentCoroutineContext().ensureActive()
                    val original=next.time
                    val output=if(cuts.isEmpty())original else CutTimeline.outputTime(original,cuts)
                    if(output==null){
                        val end=cuts.first{original>=it.startUs && original<it.endUs}.endUs
                        extractor.seekTo(end,MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                        while(extractor.sampleTime in 0 until end){currentCoroutineContext().ensureActive();if(!extractor.advance())break}
                    }else{
                        val index=next.tracks[extractor.sampleTrackIndex] ?: throw IOException("Unexpected mux track")
                        val size=if(Build.VERSION.SDK_INT>=28)extractor.sampleSize else -1L
                        if(size>buffer.capacity())buffer=ByteBuffer.allocateDirect(Math.toIntExact(size))
                        buffer.clear();val length=extractor.readSampleData(buffer,0)
                        if(length<0){next.time=-1;break}
                        val flags=extractor.sampleFlags
                        if(flags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED!=0)throw IOException("Encrypted sample")
                        var outputFlags=0
                        if(flags and MediaExtractor.SAMPLE_FLAG_SYNC!=0)outputFlags=outputFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
                        if(flags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME!=0)outputFlags=outputFlags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
                        info.set(0,length,output,outputFlags);mux.writeSampleData(index,buffer,info);extractor.advance()
                    }
                    next.time=extractor.sampleTime
                }while(next.time>=0 && next.time<limit)
            }
            mux.stop();started=false
        }finally{if(started)runCatching{mux.stop()};try{mux.release()}finally{extractors.forEach{it.release()}}}
    }
    private suspend fun saveArt(url:String,file:File,width:Int):File?=artLocks[(file.absolutePath.hashCode() and Int.MAX_VALUE)%artLocks.size].withLock {
        if(validArt(file))return@withLock file
        if(url.isBlank())return@withLock null
        try {
            currentCoroutineContext().ensureActive()
            runInterruptible {
                val call=client.newCall(Request.Builder().url(url).build()).apply{timeout().timeout(5,java.util.concurrent.TimeUnit.SECONDS)}
                try{call.execute().use{r->
                    if(!r.isSuccessful || r.body.contentLength()>8*1024*1024)return@runInterruptible null
                    val output=ByteArrayOutputStream();val buffer=ByteArray(16384)
                    r.body.byteStream().use{input->while(true){if(Thread.currentThread().isInterrupted)throw InterruptedException();val n=input.read(buffer);if(n<0)break;if(output.size()+n>8*1024*1024)throw IOException("Artwork size");output.write(buffer,0,n)}}
                    val data=output.toByteArray();val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(data,0,data.size,bounds)
                    var sample=1;while(bounds.outWidth/sample>width*2 || bounds.outHeight/sample>width*2)sample*=2
                    val bitmap=BitmapFactory.decodeByteArray(data,0,data.size,BitmapFactory.Options().apply{inSampleSize=sample}) ?: return@runInterruptible null
                    val scaled=if(bitmap.width>width)Bitmap.createScaledBitmap(bitmap,width,(bitmap.height.toLong()*width/bitmap.width).toInt().coerceAtLeast(1),true)else bitmap
                    val temp=File.createTempFile("art-",".part",file.parentFile)
                    try{temp.outputStream().use{out->@Suppress("DEPRECATION") if(!scaled.compress(Bitmap.CompressFormat.WEBP,85,out))throw IOException("Artwork encoding")};if(!temp.renameTo(file))throw IOException("Artwork storage");file}
                    finally{temp.delete();if(scaled!==bitmap)scaled.recycle();bitmap.recycle()}
                }}finally{call.cancel()}
            }
        }catch(e:CancellationException){throw e}catch(_:Exception){null}
    }
    suspend fun repairMetadata(id:Long,details:VideoDetails)=withContext(Dispatchers.IO){
        val old=dao.video(id) ?: return@withContext
        require(old.ytId==details.ytId)
        val merged=MetadataMerge.fill(old,details)
        val annotations=timelines.recognize(details)
        val repairId=System.currentTimeMillis()
        val art=File(context.filesDir,"art").apply{mkdirs()}
        coroutineScope {
            val thumb=async{if(validArt(File(old.thumbnailPath)))File(old.thumbnailPath)else saveArt(details.thumbnail,File(art,"${old.ytId}-repair-$repairId.webp"),720)}
            val avatar=async{if(validArt(File(old.channelAvatarPath)))File(old.channelAvatarPath)else saveArt(details.channelAvatar,File(art,"channel-${merged.channelId.hashCode()}-repair-$repairId.webp"),192)}
            dao.patchMetadata(id,merged.title,merged.channelId,merged.channelName,merged.description,merged.viewCount,merged.uploadDateMs,merged.likeCount,thumb.await()?.absolutePath ?: old.thumbnailPath,avatar.await()?.absolutePath ?: old.channelAvatarPath,old.dominantColor)
        }
        if(old.removedDurationMs==0L || subtitles.timeline(old.ytId).isNotEmpty())timelines.save(old.ytId,old.contentUri,annotations,subtitles.timeline(old.ytId),details.durationMs)
    }
    suspend fun pruneMissing()=withContext(Dispatchers.IO) {
        val videos=dao.allVideos()
        videos.groupBy { video->Uri.parse(video.contentUri).buildUpon().path(Uri.parse(video.contentUri).path.orEmpty().substringBeforeLast('/')).build() }.forEach { (collection, entries) ->
            entries.chunked(200).forEach { batch ->
                val ids=batch.mapNotNull { video->runCatching{ContentUris.parseId(Uri.parse(video.contentUri))}.getOrNull()?.let{it to video} }.toMap()
                if(ids.isNotEmpty())try {
                    val found=mutableSetOf<Long>()
                    context.contentResolver.query(collection,arrayOf(MediaStore.MediaColumns._ID),"_id IN ("+ids.keys.joinToString(","){"?"}+")",ids.keys.map{it.toString()}.toTypedArray(),null)?.use { cursor->while(cursor.moveToNext())found.add(cursor.getLong(0)) } ?: return@forEach
                    ids.filterKeys{it !in found}.values.forEach{dao.deleteVideo(it.id)}
                }catch(_:SecurityException){}catch(_:FileNotFoundException){}
            }
        }
    }
    suspend fun delete(video:VideoEntity)=withContext(Dispatchers.IO){timelines.delete(video.ytId);File(context.filesDir,"subtitles/${video.ytId}").deleteRecursively();context.contentResolver.delete(Uri.parse(video.contentUri),null,null);File(video.thumbnailPath).takeIf{it.isFile}?.delete();dao.deleteVideo(video.id)}
}
