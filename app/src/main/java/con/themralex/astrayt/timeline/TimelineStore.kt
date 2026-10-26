package con.themralex.astrayt.timeline

import android.content.Context
import con.themralex.astrayt.data.db.VideoEntity
import con.themralex.astrayt.data.prefs.Preferences
import con.themralex.astrayt.download.Cut
import con.themralex.astrayt.youtube.VideoDetails
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.*

data class SkipNotice(val mediaId:String,val category:String,val serial:Long=System.nanoTime())
@Singleton class TimelineStore @Inject constructor(@ApplicationContext private val context:Context,private val prefs:Preferences,private val client:OkHttpClient) {
    private val json=Json{ignoreUnknownKeys=true;encodeDefaults=true}
    private val lock=Mutex()
    private val _changes=MutableSharedFlow<String>(extraBufferCapacity=16);val changes=_changes.asSharedFlow()
    private val _skips=MutableSharedFlow<SkipNotice>(extraBufferCapacity=16);val skips=_skips.asSharedFlow()
    fun skipped(id:String,category:String){_skips.tryEmit(SkipNotice(id,category))}
    private fun file(id:String):File {require(id.matches(Regex("[A-Za-z0-9_-]{11}")));return File(context.filesDir,"timelines/$id.json")}
    suspend fun read(video:VideoEntity):VideoTimeline=withContext(Dispatchers.IO){lock.withLock{
        val fallback=VideoTimeline(video.contentUri,chapters=if(video.removedDurationMs==0L)TimelinePolicy.fromDescription(video.description,video.durationMs)else emptyList())
        runCatching{val f=file(video.ytId);require(f.length() in 1..4_000_000);json.decodeFromString<VideoTimeline>(f.readText()).takeIf{it.mediaUri==video.contentUri} ?: fallback}.getOrDefault(fallback)
    }}
    suspend fun recognize(details:VideoDetails):TimelineCapture {
        val settings=prefs.flow.first()
        val chapters=if(settings.chapterRecognitionEnabled)details.chapters else emptyList()
        if(!settings.sponsorRecognitionEnabled)return TimelineCapture(chapters,emptyList(),details.metadataLanguage,chaptersRequested=settings.chapterRecognitionEnabled)
        return try{TimelineCapture(chapters,fetchSponsors(details.ytId,details.durationMs),details.metadataLanguage,chaptersRequested=settings.chapterRecognitionEnabled,sponsorsRequested=true)}
        catch(e:CancellationException){throw e}catch(_:Exception){TimelineCapture(chapters,emptyList(),details.metadataLanguage,true,settings.chapterRecognitionEnabled,true)}
    }
    private suspend fun fetchSponsors(id:String,duration:Long):List<SponsorSegment> = withContext(Dispatchers.IO){
        val url="https://sponsor.ajay.app/api/skipSegments".toHttpUrl().newBuilder().addQueryParameter("videoID",id)
            .addQueryParameter("categories",JsonArray(TimelinePolicy.colors.keys.map{JsonPrimitive(it)}).toString()).build()
        val call=client.newCall(Request.Builder().url(url).build()).apply{timeout().timeout(8,java.util.concurrent.TimeUnit.SECONDS)}
        try{runInterruptible{call.execute().use{r->
            if(r.code==404)return@runInterruptible emptyList()
            if(!r.isSuccessful || r.body.contentLength()>2_000_000)throw IOException("TIMELINE_DOWNLOAD")
            val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(16384)
            r.body.byteStream().use{input->while(true){if(Thread.currentThread().isInterrupted)throw InterruptedException();val n=input.read(buffer);if(n<0)break;if(output.size()+n>2_000_000)throw IOException("TIMELINE_DOWNLOAD");output.write(buffer,0,n)}}
            TimelinePolicy.parseSponsors(output.toString(Charsets.UTF_8.name()),duration)
        }}}finally{call.cancel()}
    }
    suspend fun save(id:String,uri:String,capture:TimelineCapture,cuts:List<Cut>,duration:Long)=withContext(Dispatchers.IO){lock.withLock{
        val f=file(id);f.parentFile!!.mkdirs();val part=File(f.parentFile,f.name+".part")
        // A failed optional refresh must not erase a previously downloaded SponsorBlock cache.
        val previous=runCatching{json.decodeFromString<VideoTimeline>(f.readText())}.getOrNull()?.takeIf{it.mediaUri==uri}
        val mapped=TimelinePolicy.retime(capture,cuts,duration,uri).let{new->
            new.copy(chapters=if(!capture.chaptersRequested)previous?.chapters ?: new.chapters else new.chapters,sponsors=if(capture.sponsorFailed || !capture.sponsorsRequested)previous?.sponsors ?: new.sponsors else new.sponsors)
        }
        try{FileOutputStream(part).use{it.write(json.encodeToString(mapped).toByteArray());it.fd.sync()};if(!part.renameTo(f))throw IOException("STORAGE")}
        finally{part.delete()}
        _changes.tryEmit(id)
    }}
    suspend fun delete(id:String)=withContext(Dispatchers.IO){lock.withLock{file(id).delete();Unit}}
}
