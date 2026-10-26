package con.themralex.astrayt.youtube

import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import java.io.IOException

/** Bounded complementary clients for partial responses. Revisions verified against yt-dlp. */
internal class InnertubeFallback(private val client:OkHttpClient,private val transport:((Request,Long)->JsonObject)?=null) {
    private fun send(request:Request,deadline:Long):JsonObject {
        val remaining=java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime()).coerceAtLeast(1)
        if(System.nanoTime()>=deadline)throw java.net.SocketTimeoutException("Player session timed out")
        transport?.let{return it(request,remaining)}
        return client.newCall(request).apply{timeout().timeout(remaining,java.util.concurrent.TimeUnit.MILLISECONDS)}.execute().use{
            if(!it.isSuccessful)throw IOException("HTTP ${it.code}")
            Json.parseToJsonElement(it.body.string()).jsonObject
        }
    }
    fun details(id:String,metadata:VideoDetails?,forceAll:Boolean=false,language:String=java.util.Locale.getDefault().language,discoverAudio:Boolean=false,deadlineNanos:Long=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(24),metadataLanguage:String=language):VideoDetails {
        var best=metadata
        var failure:Exception?=null
        // VR 1.65.10 stopped serving media; use valid anonymous sessions, not that retired profile.
        val profiles=if(discoverAudio)listOf(InnertubeSession.vision,InnertubeSession.android,InnertubeSession.ios,InnertubeSession.web)else listOf(InnertubeSession.android,InnertubeSession.vision,InnertubeSession.ios,InnertubeSession.web)
        val overallDeadline=minOf(deadlineNanos,System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(24))
        for(profile in profiles){
            if(System.nanoTime()>=overallDeadline)break
            if(Thread.currentThread().isInterrupted)throw InterruptedException()
            try {
                val deadline=minOf(overallDeadline,System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8))
                val boot=send(profile.visitorRequest(metadataLanguage),deadline)
                val visitor=boot.obj("responseContext").text("visitorData").takeIf{it.isNotBlank()} ?: throw IOException("Missing visitor session")
                val cpn=org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.generateContentPlaybackNonce()
                val t=org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.generateTParameter()
                val timestamp=if(profile==InnertubeSession.web)try{YoutubeJavaScriptPlayerManager.getSignatureTimestamp(id)}catch(e:InterruptedException){throw e}catch(_:Exception){null}else null
                val response=send(profile.playerRequest(id,metadataLanguage,visitor,cpn,t,timestamp),deadline)
                val result=parse(id,response,best,profile.mediaHeaders,signature={YoutubeJavaScriptPlayerManager.deobfuscateSignature(id,it)}){url->
                    val decoded=YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(id,url)
                    decoded.toHttpUrl().newBuilder().setQueryParameter("cpn",cpn).build().toString()
                }
                val localized=result.copy(metadataLanguage=metadataLanguage,chapters=result.chapters.ifEmpty{con.themralex.astrayt.timeline.TimelinePolicy.fromDescription(result.description,result.durationMs)})
                best=best?.let{StreamRecovery.merge(it,localized)} ?: localized
                val preferredFound=best.tracks.any{it.type!="DESCRIPTIVE" && it.locale.substringBefore('-').equals(language.substringBefore('-'),true)}
                if(!forceAll && !StreamRecovery.needsAlternate(best) && (!discoverAudio || (result.tracks.size>1 && preferredFound)))return best
            }catch(e:InterruptedException){throw e}catch(e:java.util.concurrent.CancellationException){throw e}catch(e:Exception){if(e.message?.contains("429")==true)throw e;failure=e}
        }
        return best?.takeIf{it.videos.isNotEmpty() || it.tracks.isNotEmpty()} ?: throw (failure ?: NoStreams())
    }
    companion object {
        private val USER_AGENT=InnertubeSession.vision.userAgent
        internal fun parse(id:String,envelope:JsonObject,metadata:VideoDetails?,headers:Map<String,String> = mapOf("User-Agent" to USER_AGENT),signature:(String)->String={throw NoStreams()},decode:(String)->String):VideoDetails {
            val response=(envelope["playerResponse"] as? JsonObject) ?: envelope
            val status=response.obj("playabilityStatus").text("status")
            if(status!="OK")throw IOException("YouTube: $status")
            val v=response.obj("videoDetails")
            if(v.text("videoId")!=id)throw NoStreams()
            val data=response.obj("streamingData")
            val formats=listOf("adaptiveFormats","formats").flatMap { (data[it] as? JsonArray).orEmpty() }
            val audio=mutableMapOf<String,MutableList<MediaStream>>()
            val trackData=mutableMapOf<String,JsonObject>()
            val trackTypes=mutableMapOf<String,String>()
            val videos=mutableListOf<MediaStream>()
            for(element in formats) {
                val f=element as? JsonObject ?: continue
                if((f["drmFamilies"] as? JsonArray)?.isNotEmpty()==true || f.text("drmTrackType").isNotBlank() || f.text("type")=="FORMAT_STREAM_TYPE_OTF")continue
                val url=try{decode(FormatUrl.resolve(f,signature) ?: continue)}catch(e:InterruptedException){throw e}catch(e:java.util.concurrent.CancellationException){throw e}catch(_:Exception){continue}
                val mime=f.text("mimeType")
                val codec=Regex("codecs=\"([^\"]+)\"").find(mime)?.groupValues?.get(1).orEmpty()
                val isAudio=mime.startsWith("audio/")
                val stream=MediaStream(f.text("itag"),url,if(mime.contains("webm"))"webm"else if(isAudio)"m4a"else"mp4",codec,f.number("bitrate").toInt(),f.number("height").toInt().takeIf{it>0} ?: Regex("(\\d+)p").find(f.text("qualityLabel"))?.groupValues?.get(1)?.toIntOrNull() ?: 0,f.number("fps").toInt(),f.number("contentLength"),!isAudio && !codec.contains(","),f.text("qualityLabel").contains("HDR"),headers=headers)
                if(isAudio){val track=f.obj("audioTrack");val key=track.text("id").ifBlank{"original"};audio.getOrPut(key){mutableListOf()}.add(stream);trackData[key]=track
                    val tagged=runCatching{org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.extractAudioTrackType(f.text("xtags"))?.name}.getOrNull()
                    val name=track.text("displayName").lowercase(java.util.Locale.ROOT)
                    val type=tagged ?: when { "descriptive" in name || "descriptiv" in name->"DESCRIPTIVE"; "original" in name->"ORIGINAL"; key=="original"->"ORIGINAL"; else->"UNKNOWN" }
                    if(type!="UNKNOWN" || key !in trackTypes)trackTypes[key]=type
                }
                else if(mime.startsWith("video/"))videos.add(stream)
            }
            val tracks=audio.map { (key,streams)->val t=trackData.getValue(key);val isDefault=t["audioIsDefault"]?.jsonPrimitive?.booleanOrNull ?: (key=="original");val type=trackTypes.getValue(key).let{if(it=="UNKNOWN" && trackTypes.values.any{value->value=="ORIGINAL"})"DUBBED"else it};AudioTrackOption(key,key.substringBefore('.').takeUnless{it=="original"}.orEmpty(),type,t.text("displayName"),streams,isDefault) }
            if(videos.isEmpty() && tracks.isEmpty())throw NoStreams()
            val thumbs=v.obj("thumbnail")["thumbnails"] as? JsonArray
            val base=metadata ?: VideoDetails(id,v.text("title"),"https://www.youtube.com/channel/${v.text("channelId")}",v.text("author"),"",(thumbs?.lastOrNull() as? JsonObject)?.text("url").orEmpty(),v.number("lengthSeconds")*1000,v.number("viewCount"),0,v.text("shortDescription").take(20000),emptyList(),emptyList())
            val subtitles=response.obj("captions").obj("playerCaptionsTracklistRenderer")["captionTracks"] as? JsonArray
            val captions=subtitles.orEmpty().mapNotNull { element->
                val item=element as? JsonObject ?: return@mapNotNull null
                val language=item.text("languageCode");val url=item.text("baseUrl")
                if(language.isBlank() || url.isBlank())return@mapNotNull null
                val auto=item.text("kind")=="asr" || item.text("vssId").startsWith("a.")
                val label=item.obj("name").text("simpleText").ifBlank{language}
                SubtitleOption(language+if(auto)":auto"else":manual",language,label,url,auto)
            }
            return base.copy(videos=videos,tracks=tracks,subtitles=(base.subtitles+captions).distinctBy{it.id},fetchedAt=System.currentTimeMillis())
        }
        private fun JsonObject.obj(key:String)=this[key] as? JsonObject ?: JsonObject(emptyMap())
        private fun JsonObject.text(key:String)=(this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
        private fun JsonObject.number(key:String)=text(key).toLongOrNull() ?: 0
    }
}
