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
    suspend fun finish(row:DownloadEntity,options:DownloadOptions,details:VideoDetails,plan:QualityOption,video:File?,audio:File?)=withContext(Dispatchers.IO) {
        val audioOnly=options.audioOnly;val mime=if(audioOnly)"audio/mp4" else if(plan.container=="webm")"video/webm"else"video/mp4"
        val name=details.title.replace(Regex("[\\/:*?\"<>|]"),"_").take(120)+"."+plan.container
        var legacy:File?=null
        val values=ContentValues().apply{put(MediaStore.MediaColumns.DISPLAY_NAME,name);put(MediaStore.MediaColumns.TITLE,details.title);put(MediaStore.MediaColumns.MIME_TYPE,mime);put(MediaStore.Audio.Media.ARTIST,details.channelName);if(Build.VERSION.SDK_INT>=29){put(MediaStore.MediaColumns.RELATIVE_PATH,(if(audioOnly)Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES)+"/AstraYT");put(MediaStore.MediaColumns.IS_PENDING,1)}else{@Suppress("DEPRECATION") val folder=File(Environment.getExternalStoragePublicDirectory(if(audioOnly)Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES),"AstraYT").apply{mkdirs()};legacy=File(folder,"${System.currentTimeMillis()}-$name");put(MediaStore.MediaColumns.DATA,legacy!!.absolutePath)}}
        val collection=if(audioOnly)MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val uri=context.contentResolver.insert(collection,values) ?: throw IOException("STORAGE")
        try {
            if(audioOnly || plan.audio==null){context.contentResolver.openOutputStream(uri,"w")!!.use{output->FileInputStream(if(audioOnly)audio ?: throw IOException("Missing audio")else video ?: throw IOException("Missing video")).use{it.copyTo(output,1024*1024)}}}
            else {
                try {context.contentResolver.openFileDescriptor(uri,"rw")!!.use{fd->mux(video!!,audio!!,plan.container,fd.fileDescriptor,null)}}
                catch(e:kotlinx.coroutines.CancellationException){throw e}
                catch(e:Exception){val temp=File(context.cacheDir,"mux-${row.id}.${plan.container}");try{mux(video!!,audio!!,plan.container,null,temp.absolutePath);context.contentResolver.openOutputStream(uri,"wt")!!.use{out->temp.inputStream().use{it.copyTo(out,1024*1024)}}}finally{temp.delete()}}
            }
            if(Build.VERSION.SDK_INT>=29)context.contentResolver.update(uri,ContentValues().apply{put(MediaStore.MediaColumns.IS_PENDING,0)},null,null)
            val art=File(context.filesDir,"art").apply{mkdirs()};val thumb=saveArt(details.thumbnail,File(art,"${row.ytId}.webp"),720);val avatar=saveArt(details.channelAvatar,File(art,"channel-${details.channelId.hashCode()}.webp"),192)
            val color=thumb?.let{BitmapFactory.decodeFile(it.absolutePath)}?.let{bitmap->try{Palette.from(bitmap).generate().getDominantColor(0)}finally{bitmap.recycle()}} ?: 0
            val size=context.contentResolver.openFileDescriptor(uri,"r")?.use{it.statSize}?.coerceAtLeast(0) ?: 0
            val previous=dao.byYoutubeId(row.ytId)
            dao.insertVideo(VideoEntity(id=previous?.id ?: 0,positionMs=previous?.positionMs?.coerceAtMost(details.durationMs) ?: 0,ytId=row.ytId,title=details.title,channelId=details.channelId,channelName=details.channelName,channelAvatarPath=avatar?.absolutePath.orEmpty(),thumbnailPath=thumb?.absolutePath.orEmpty(),durationMs=details.durationMs,viewCount=details.viewCount,uploadDateMs=details.uploadDateMs,description=details.description,contentUri=uri.toString(),fileSizeBytes=size,mimeType=mime,height=plan.height,kind=row.kind,audioLangTag=details.tracks.firstOrNull{it.id==options.trackId}?.locale.orEmpty(),audioLabel=details.tracks.firstOrNull{it.id==options.trackId}?.displayName.orEmpty(),dominantColor=color))
            previous?.takeIf{it.contentUri!=uri.toString()}?.let{old->runCatching{context.contentResolver.delete(Uri.parse(old.contentUri),null,null)}}
        }catch(e:Exception){context.contentResolver.delete(uri,null,null);legacy?.delete();throw e}
    }
    private suspend fun mux(video:File,audio:File,container:String,fd:FileDescriptor?,path:String?) {
        val mux=if(fd!=null)MediaMuxer(fd,if(container=="webm")MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4) else MediaMuxer(path!!,if(container=="webm")MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val extractors=mutableListOf<MediaExtractor>();var started=false
        try {
            val tracks=listOf(video to "video/",audio to "audio/").map{(file,prefix)->val ex=MediaExtractor().apply{setDataSource(file.absolutePath)};extractors.add(ex);val index=(0 until ex.trackCount).firstOrNull{ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith(prefix)==true} ?: throw IOException("Missing media track");ex.selectTrack(index);ex to mux.addTrack(ex.getTrackFormat(index))}
            mux.start();started=true;val buffer=ByteBuffer.allocate(1024*1024);val info=MediaCodec.BufferInfo()
            tracks.forEach{(extractor,index)->while(true){currentCoroutineContext().ensureActive();buffer.clear();val length=extractor.readSampleData(buffer,0);if(length<0)break;val sampleFlags=extractor.sampleFlags;if(sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED != 0)throw IOException("Encrypted sample");var outputFlags=0;if(sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)outputFlags=outputFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME;if(sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0)outputFlags=outputFlags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME;info.set(0,length,extractor.sampleTime,outputFlags);mux.writeSampleData(index,buffer,info);extractor.advance()}}
            mux.stop();started=false
        }finally{if(started)runCatching{mux.stop()};mux.release();extractors.forEach{it.release()}}
    }
    private fun saveArt(url:String,file:File,width:Int):File?=runCatching {
        if(url.isBlank())return null
        client.newCall(Request.Builder().url(url).build()).execute().use{r->if(!r.isSuccessful)return null;val data=r.body.bytes();val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(data,0,data.size,bounds);var sample=1;while(bounds.outWidth/sample>width*2)sample*=2;val bitmap=BitmapFactory.decodeByteArray(data,0,data.size,BitmapFactory.Options().apply{inSampleSize=sample}) ?: return null;val scaled=if(bitmap.width>width)Bitmap.createScaledBitmap(bitmap,width,(bitmap.height.toLong()*width/bitmap.width).toInt().coerceAtLeast(1),true)else bitmap
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
    suspend fun delete(video:VideoEntity)=withContext(Dispatchers.IO){context.contentResolver.delete(Uri.parse(video.contentUri),null,null);File(video.thumbnailPath).takeIf{it.isFile}?.delete();dao.deleteVideo(video.id)}
}
