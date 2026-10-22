package con.themralex.astrayt.youtube

import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import java.io.IOException

/** Independent mobile client for empty/failed VisionOS responses. Client revision from yt-dlp. */
internal class InnertubeFallback(private val client:OkHttpClient) {
    fun details(id:String,metadata:VideoDetails?):VideoDetails {
        val userAgent=USER_AGENT
        val body=buildJsonObject {
            put("context",buildJsonObject {put("client",buildJsonObject {
                put("clientName","ANDROID_VR");put("clientVersion","1.65.10");put("deviceMake","Oculus");put("deviceModel","Quest 3");put("androidSdkVersion",32);put("osName","Android");put("osVersion","12L");put("hl",java.util.Locale.getDefault().language);put("userAgent",userAgent)
            })})
            put("videoId",id);put("contentCheckOk",true);put("racyCheckOk",true)
        }
        val request=Request.Builder().url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            .header("User-Agent",userAgent).header("X-YouTube-Client-Name","28").header("X-YouTube-Client-Version","1.65.10")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        val response=client.newCall(request).execute().use {
            if(!it.isSuccessful)throw IOException("HTTP ${it.code}")
            Json.parseToJsonElement(it.body.string()).jsonObject
        }
        return parse(id,response,metadata) {url->YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(id,url)}
    }
    companion object {
        private const val USER_AGENT="com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
        internal fun parse(id:String,response:JsonObject,metadata:VideoDetails?,decode:(String)->String):VideoDetails {
            val status=response.obj("playabilityStatus").text("status")
            if(status!="OK")throw IOException("YouTube: $status")
            val v=response.obj("videoDetails")
            if(v.text("videoId")!=id)throw NoStreams()
            val data=response.obj("streamingData")
            val formats=listOf("adaptiveFormats","formats").flatMap { (data[it] as? JsonArray).orEmpty() }
            val audio=mutableMapOf<String,MutableList<MediaStream>>()
            val trackData=mutableMapOf<String,JsonObject>()
            val videos=mutableListOf<MediaStream>()
            for(element in formats) {
                val f=element as? JsonObject ?: continue
                if(f["drmFamilies"]!=null || f["drmTrackType"]!=null)continue
                val raw=f.text("url");if(raw.isBlank())continue
                val url=decode(raw)
                val mime=f.text("mimeType")
                val codec=Regex("codecs=\"([^\"]+)\"").find(mime)?.groupValues?.get(1).orEmpty()
                val isAudio=mime.startsWith("audio/")
                val stream=MediaStream(f.text("itag"),url,if(mime.contains("webm"))"webm"else if(isAudio)"m4a"else"mp4",codec,f.number("bitrate").toInt(),f.number("height").toInt(),f.number("fps").toInt(),f.number("contentLength"),!isAudio && !codec.contains(","),f.text("qualityLabel").contains("HDR"),headers=mapOf("User-Agent" to USER_AGENT))
                if(isAudio){val track=f.obj("audioTrack");val key=track.text("id").ifBlank{"original"};audio.getOrPut(key){mutableListOf()}.add(stream);trackData[key]=track}
                else if(mime.startsWith("video/"))videos.add(stream)
            }
            val tracks=audio.map { (key,streams)->val t=trackData.getValue(key);val original=t["audioIsDefault"]?.jsonPrimitive?.booleanOrNull ?: (key=="original");AudioTrackOption(key,key.substringBefore('.').takeUnless{it=="original"}.orEmpty(),if(original)"ORIGINAL"else"DUBBED",t.text("displayName"),streams,original) }
            if(videos.isEmpty() && tracks.isEmpty())throw NoStreams()
            val thumbs=v.obj("thumbnail")["thumbnails"] as? JsonArray
            val base=metadata ?: VideoDetails(id,v.text("title"),"https://www.youtube.com/channel/${v.text("channelId")}",v.text("author"),"",(thumbs?.lastOrNull() as? JsonObject)?.text("url").orEmpty(),v.number("lengthSeconds")*1000,v.number("viewCount"),0,v.text("shortDescription").take(2000),emptyList(),emptyList())
            return base.copy(videos=videos,tracks=tracks,fetchedAt=System.currentTimeMillis())
        }
        private fun JsonObject.obj(key:String)=this[key] as? JsonObject ?: JsonObject(emptyMap())
        private fun JsonObject.text(key:String)=(this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
        private fun JsonObject.number(key:String)=text(key).toLongOrNull() ?: 0
    }
}
