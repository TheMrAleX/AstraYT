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
import okhttp3.*
import java.io.*
import java.nio.ByteBuffer
import javax.inject.*
@Singleton class MediaStorage @Inject constructor(@ApplicationContext private val context:Context,private val dao:AstraDao,private val client:OkHttpClient) {
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
    suspend fun finish(row:DownloadEntity,options:DownloadOptions,details:VideoDetails,plan:QualityOption,video:File?,audio:File?,requestedCuts:List<Cut> = emptyList())=withContext(Dispatchers.IO) {
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
                catch(e:Exception){val temp=File(context.cacheDir,"mux-${row.id}.${plan.container}");try{mux(if(audioOnly)audio!! else video!!,if(audioOnly)null else audio,plan.container,null,temp.absolutePath,cuts,audioOnly,options.audioLanguage);copyToStore(temp,uri,"wt")}finally{temp.delete()}}
            }
            withContext(NonCancellable){
            if(Build.VERSION.SDK_INT>=29)context.contentResolver.update(uri,ContentValues().apply{put(MediaStore.MediaColumns.IS_PENDING,0)},null,null)
            val thumb=thumbTask.await();val avatar=avatarTask.await()
            val color=thumb?.let{BitmapFactory.decodeFile(it.absolutePath)}?.let{bitmap->try{Palette.from(bitmap).generate().getDominantColor(0)}finally{bitmap.recycle()}} ?: 0
            val size=context.contentResolver.openFileDescriptor(uri,"r")?.use{it.statSize}?.coerceAtLeast(0) ?: 0
            val previous=dao.byYoutubeId(row.ytId)
            dao.insertVideo(VideoEntity(id=previous?.id ?: 0,positionMs=previous?.positionMs?.let{CutTimeline.outputTime(it*1000,cuts)?.div(1000) ?: 0}?.coerceAtMost((details.durationMs-removedMs).coerceAtLeast(0)) ?: 0,ytId=row.ytId,title=details.title,channelId=details.channelId,channelName=details.channelName,channelAvatarPath=avatar?.absolutePath.orEmpty(),thumbnailPath=thumb?.absolutePath.orEmpty(),durationMs=(details.durationMs-removedMs).coerceAtLeast(0),viewCount=details.viewCount,uploadDateMs=details.uploadDateMs,description=details.description,contentUri=uri.toString(),fileSizeBytes=size,mimeType=mime,height=plan.height,kind=row.kind,audioLangTag=options.audioLanguage ?: details.tracks.firstOrNull{it.id==options.trackId}?.locale.orEmpty(),audioLabel=options.audioLabel ?: details.tracks.firstOrNull{it.id==options.trackId}?.displayName.orEmpty(),dominantColor=color,likeCount=details.likeCount,removedDurationMs=removedMs))
            previous?.takeIf{it.contentUri!=uri.toString()}?.let{old->runCatching{context.contentResolver.delete(Uri.parse(old.contentUri),null,null)}}
            }
        }}catch(e:Exception){withContext(NonCancellable){
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
    private suspend fun mux(video:File,audio:File?,container:String,fd:FileDescriptor?,path:String?,cuts:List<Cut>,audioOnly:Boolean=false,audioLanguage:String?=null) {
        val format=if(container=="webm")MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        val mux=if(fd!=null)MediaMuxer(fd,format)else MediaMuxer(path!!,format)
        val extractors=mutableListOf<MediaExtractor>();var started=false
        try {
            // One extractor per selected track permits timestamp ordered interleaving.
            val sources=if(audio==null)listOf(video)else listOf(video,audio)
            val tracks=mutableListOf<Pair<MediaExtractor,Int>>()
            var capacity=if(Build.VERSION.SDK_INT>=28)1024*1024 else 4*1024*1024
            sources.forEachIndexed { source,file->
                val probe=MediaExtractor()
                val indices=try{probe.setDataSource(file.absolutePath);(0 until probe.trackCount).filter { index->
                    val mime=probe.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty()
                    if(audioOnly)mime.startsWith("audio/") else if(audio==null)mime.startsWith("video/")||mime.startsWith("audio/") else mime.startsWith(if(source==0)"video/"else"audio/")
                }}finally{probe.release()}
                indices.forEach { index->
                    val ex=MediaExtractor();extractors.add(ex);ex.setDataSource(file.absolutePath);ex.selectTrack(index)
                    val track=ex.getTrackFormat(index)
                    if(track.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/") && !audioLanguage.isNullOrBlank())track.setString(MediaFormat.KEY_LANGUAGE,runCatching{java.util.Locale.forLanguageTag(audioLanguage).isO3Language}.getOrDefault(audioLanguage))
                    if(track.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE))capacity=maxOf(capacity,track.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                    tracks.add(ex to mux.addTrack(track))
                }
            }
            check(tracks.isNotEmpty())
            mux.start();started=true
            var buffer=ByteBuffer.allocateDirect(capacity);val info=MediaCodec.BufferInfo()
            while(true) {
                currentCoroutineContext().ensureActive()
                var next:Pair<MediaExtractor,Int>?=null
                var earliest=Long.MAX_VALUE
                for(track in tracks){val time=track.first.sampleTime;if(time>=0 && time<earliest){next=track;earliest=time}}
                if(next==null)break
                val (extractor,index)=next
                val original=extractor.sampleTime
                val output=CutTimeline.outputTime(original,cuts)
                if(output==null) {
                    // Seek over excluded spans instead of reading every removed sample.
                    val end=cuts.first{original>=it.startUs && original<it.endUs}.endUs
                    extractor.seekTo(end,MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    while(extractor.sampleTime in 0 until end){currentCoroutineContext().ensureActive();if(!extractor.advance())break}
                    continue
                }
                val size=if(Build.VERSION.SDK_INT>=28)extractor.sampleSize else -1L
                if(size>buffer.capacity())buffer=ByteBuffer.allocateDirect(Math.toIntExact(size))
                buffer.clear();val length=extractor.readSampleData(buffer,0);if(length<0)break
                val flags=extractor.sampleFlags
                if(flags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED!=0)throw IOException("Encrypted sample")
                var outputFlags=0
                if(flags and MediaExtractor.SAMPLE_FLAG_SYNC!=0)outputFlags=outputFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
                if(flags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME!=0)outputFlags=outputFlags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
                info.set(0,length,output,outputFlags);mux.writeSampleData(index,buffer,info);extractor.advance()
            }
            mux.stop();started=false
        }finally{if(started)runCatching{mux.stop()};mux.release();extractors.forEach{it.release()}}
    }
    private suspend fun saveArt(url:String,file:File,width:Int):File?=runCatching {
        if(url.isBlank())return null
        currentCoroutineContext().ensureActive()
        val call=client.newCall(Request.Builder().url(url).build()).apply{timeout().timeout(5,java.util.concurrent.TimeUnit.SECONDS)}
        call.execute().use{r->if(!r.isSuccessful)return null;val data=r.body.bytes();val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(data,0,data.size,bounds);var sample=1;while(bounds.outWidth/sample>width*2)sample*=2;val bitmap=BitmapFactory.decodeByteArray(data,0,data.size,BitmapFactory.Options().apply{inSampleSize=sample}) ?: return null;val scaled=if(bitmap.width>width)Bitmap.createScaledBitmap(bitmap,width,(bitmap.height.toLong()*width/bitmap.width).toInt().coerceAtLeast(1),true)else bitmap
            file.outputStream().use{out->@Suppress("DEPRECATION") scaled.compress(Bitmap.CompressFormat.WEBP,85,out)};if(scaled!==bitmap)scaled.recycle();bitmap.recycle();file}
    }.getOrNull()
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
    suspend fun delete(video:VideoEntity)=withContext(Dispatchers.IO){File(context.filesDir,"subtitles/${video.ytId}").deleteRecursively();context.contentResolver.delete(Uri.parse(video.contentUri),null,null);File(video.thumbnailPath).takeIf{it.isFile}?.delete();dao.deleteVideo(video.id)}
}
