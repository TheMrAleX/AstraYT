package con.themralex.astrayt.youtube

import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import java.io.IOException

/** Bounded complementary clients for partial responses. Revisions verified against yt-dlp. */
internal class InnertubeFallback(private val client:OkHttpClient) {
    fun details(id:String,metadata:VideoDetails?,forceAll:Boolean=false,language:String=java.util.Locale.getDefault().language,discoverAudio:Boolean=false):VideoDetails {
        var best=metadata
        var failure:Exception?=null
        val profiles=if(discoverAudio)listOf("IOS","ANDROID_VR","WEB_EMBEDDED_PLAYER")else listOf("ANDROID_VR","WEB_EMBEDDED_PLAYER","IOS")
        var audioChecked=false
        for(profile in profiles){
            if(Thread.currentThread().isInterrupted)throw InterruptedException()
            try {
                val embedded=profile=="WEB_EMBEDDED_PLAYER"
                val version=when(profile){"ANDROID_VR"->"1.65.10";"IOS"->"21.26.4";else->"2.20260708.00.00"}
                val clientId=when(profile){"ANDROID_VR"->"28";"IOS"->"5";else->"56"}
                val userAgent=when(profile){"ANDROID_VR"->USER_AGENT;"IOS"->"com.google.ios.youtube/21.26.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)";else->"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.5 Safari/605.1.15"}
                val timestamp=if(embedded)try{YoutubeJavaScriptPlayerManager.getSignatureTimestamp(id)}catch(e:InterruptedException){throw e}catch(_:Exception){null}else null
                val body=buildJsonObject {
                    put("context",buildJsonObject {
                        put("client",buildJsonObject {
                            put("clientName",profile);put("clientVersion",version);put("hl",java.util.Locale.forLanguageTag(language).language);put("userAgent",userAgent)
                            if(profile=="ANDROID_VR"){put("deviceMake","Oculus");put("deviceModel","Quest 3");put("androidSdkVersion",32);put("osName","Android");put("osVersion","12L")}
                            if(profile=="IOS"){put("deviceMake","Apple");put("deviceModel","iPhone16,2");put("osName","iPhone");put("osVersion","18.3.2.22D82")}
                        })
                        if(embedded)put("thirdParty",buildJsonObject{put("embedUrl","https://www.youtube.com/embed/$id")})
                    })
                    put("videoId",id);put("contentCheckOk",true);put("racyCheckOk",true)
                    if(embedded)put("playbackContext",buildJsonObject{put("contentPlaybackContext",buildJsonObject{put("html5Preference","HTML5_PREF_WANTS");timestamp?.let{put("signatureTimestamp",it)}})})
                }
                val headers=mapOf("User-Agent" to userAgent,"Referer" to "https://www.youtube.com/","Origin" to "https://www.youtube.com")
                val request=Request.Builder().url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false").apply{headers.forEach{(key,value)->header(key,value)}}
                    .header("X-YouTube-Client-Name",clientId).header("X-YouTube-Client-Version",version)
                    .post(body.toString().toRequestBody("application/json".toMediaType())).build()
                val call=client.newCall(request).apply{timeout().timeout(8,java.util.concurrent.TimeUnit.SECONDS)}
                val response=call.execute().use{if(!it.isSuccessful)throw IOException("HTTP ${it.code}");Json.parseToJsonElement(it.body.string()).jsonObject}
                val result=parse(id,response,best,headers,signature={YoutubeJavaScriptPlayerManager.deobfuscateSignature(id,it)}){YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(id,it)}
                best=best?.let{StreamRecovery.merge(it,result)} ?: result
                // IOS enumerates multilingual audio even when another client only returned the default.
                if(profile=="IOS" && result.tracks.isNotEmpty())audioChecked=true
                val preferredFound=best.tracks.any{it.type!="DESCRIPTIVE" && it.locale.substringBefore('-').equals(language.substringBefore('-'),true)}
                if(!forceAll && !StreamRecovery.needsAlternate(best) && (!discoverAudio || (audioChecked && preferredFound)))return best
            }catch(e:InterruptedException){throw e}catch(e:java.util.concurrent.CancellationException){throw e}catch(e:Exception){if(e.message?.contains("429")==true)throw e;failure=e}
        }
        return best?.takeIf{it.videos.isNotEmpty() || it.tracks.isNotEmpty()} ?: throw (failure ?: NoStreams())
    }
    companion object {
        private const val USER_AGENT="com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
        internal fun parse(id:String,response:JsonObject,metadata:VideoDetails?,headers:Map<String,String> = mapOf("User-Agent" to USER_AGENT),signature:(String)->String={throw NoStreams()},decode:(String)->String):VideoDetails {
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
            val base=metadata ?: VideoDetails(id,v.text("title"),"https://www.youtube.com/channel/${v.text("channelId")}",v.text("author"),"",(thumbs?.lastOrNull() as? JsonObject)?.text("url").orEmpty(),v.number("lengthSeconds")*1000,v.number("viewCount"),0,v.text("shortDescription").take(2000),emptyList(),emptyList())
            return base.copy(videos=videos,tracks=tracks,fetchedAt=System.currentTimeMillis())
        }
        private fun JsonObject.obj(key:String)=this[key] as? JsonObject ?: JsonObject(emptyMap())
        private fun JsonObject.text(key:String)=(this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
        private fun JsonObject.number(key:String)=text(key).toLongOrNull() ?: 0
    }
}
