package con.themralex.astrayt.youtube
import con.themralex.astrayt.data.prefs.Preferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.*
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.*
import org.schabi.newpipe.extractor.downloader.*
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.*
import java.util.LinkedHashMap
import javax.inject.*

@Singleton class NewPipeBackend @Inject constructor(private val client:OkHttpClient,private val preferences:Preferences):YoutubeBackend {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val mutex=Mutex()
    private val cache=object:LinkedHashMap<String,VideoDetails>(32,.75f,true){override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String,VideoDetails>?)=size>32}
    private val flights=mutableMapOf<String,Deferred<VideoDetails>>()
    @Volatile private var initialized=false
    private suspend fun init() {
        val language=preferences.flow.first().preferredAudioLanguage
        mutex.withLock {
            if(!initialized){NewPipe.init(object:Downloader(){override fun execute(request:Request):Response {
                val builder=okhttp3.Request.Builder().url(request.url())
                request.headers().forEach{(key,values)->values.forEach{builder.addHeader(key,it)}}
                if(request.headers().keys.none{it.equals("User-Agent",true)})builder.header("User-Agent","Mozilla/5.0")
                val method=request.httpMethod();val data=request.dataToSend();builder.method(method,if(method=="GET"||method=="HEAD")null else (data ?: byteArrayOf()).toRequestBody())
                client.newCall(builder.build()).execute().use { response->
                    if(response.code==429)throw ReCaptchaException("Rate limited",request.url())
                    return Response(response.code,response.message,response.headers.toMultimap(),response.body.string(),response.request.url.toString())
                }
            }},Localization(language));initialized=true}
            NewPipe.setPreferredLocalization(Localization(language))
        }
    }
    override suspend fun details(videoId:String):VideoDetails {
        init()
        val deferred=mutex.withLock {
            cache[videoId]?.takeIf{System.currentTimeMillis()-it.fetchedAt<600000}?.let{return it}
            flights[videoId] ?: scope.async {
                try { val details=withTimeout(20000){runInterruptible{extract(videoId)}};mutex.withLock{cache[videoId]=details};details }
                finally {mutex.withLock{flights.remove(videoId)}}
            }.also{flights[videoId]=it}
        }
        return deferred.await()
    }
    override suspend fun refreshStreams(videoId:String):VideoDetails {mutex.withLock{cache.remove(videoId)};return details(videoId)}
    override suspend fun resolve(url:String):ResolvedLink {
        return when(val link=UrlParser.parse(url)) {
            is YoutubeLink.Video->ResolvedLink.Video(details(link.id))
            is YoutubeLink.Playlist->{init();withContext(Dispatchers.IO){withTimeout(20000){runInterruptible{
                val canonical="https://www.youtube.com/playlist?list=${link.id}";val info=PlaylistInfo.getInfo(NewPipe.getService(0),canonical)
                ResolvedLink.Playlist(canonical,info.name,info.thumbnails.maxByOrNull{it.height}?.url.orEmpty(),info.relatedItems.take(200).mapNotNull{item->val id=(UrlParser.parse(item.url) as? YoutubeLink.Video)?.id ?: return@mapNotNull null;PlaylistEntry(id,item.name,item.uploaderName,item.thumbnails.maxByOrNull{it.height}?.url.orEmpty(),item.duration*1000)})
            }}}}
            null->throw IllegalArgumentException("UNSUPPORTED_URL")
        }
    }
    private fun extract(id:String):VideoDetails {
        val info=StreamInfo.getInfo(NewPipe.getService(0),UrlParser.canonical(id))
        if(info.streamType==StreamType.LIVE_STREAM || info.streamType==StreamType.AUDIO_LIVE_STREAM)throw IllegalStateException("LIVE_STREAM")
        fun video(s:VideoStream)=MediaStream(s.id,s.content,s.format?.suffix.orEmpty(),s.codec.orEmpty(),s.bitrate,s.height,s.fps,videoOnly=s.isVideoOnly,hdr=s.quality.orEmpty().contains("HDR",true))
        val tracks=info.audioStreams.filter{it.isUrl && it.deliveryMethod==DeliveryMethod.PROGRESSIVE_HTTP}.groupBy{it.audioTrackId ?: "original"}.map{(key,streams)->val first=streams.first();AudioTrackOption(key,first.audioLocale?.toLanguageTag().orEmpty(),first.audioTrackType?.name ?: "ORIGINAL",first.audioTrackName.orEmpty(),streams.map{s->MediaStream(s.id,s.content,s.format?.suffix.orEmpty(),s.codec.orEmpty(),if(s.bitrate>0)s.bitrate else s.averageBitrate*1000)},first.audioTrackType==AudioTrackType.ORIGINAL)}
        return VideoDetails(id,info.name,info.uploaderUrl,info.uploaderName,info.uploaderAvatars.maxByOrNull{it.height}?.url.orEmpty(),info.thumbnails.maxByOrNull{it.height}?.url.orEmpty(),info.duration*1000,info.viewCount,info.uploadDate?.instant?.toEpochMilli() ?: 0,info.description.content.take(2000),(info.videoOnlyStreams+info.videoStreams).filter{it.isUrl && it.deliveryMethod==DeliveryMethod.PROGRESSIVE_HTTP}.map(::video),tracks)
    }
}
