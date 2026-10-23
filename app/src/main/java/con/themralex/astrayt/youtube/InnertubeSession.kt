package con.themralex.astrayt.youtube

import java.util.Locale
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Client identity stays identical during visitor bootstrap, player and media requests. */
internal data class InnertubeSession(val name:String,val version:String,val id:String,val userAgent:String,val device:Map<String,JsonPrimitive> = emptyMap(),val reel:Boolean=false) {
    private val embedded get()=name=="WEB_EMBEDDED_PLAYER"
    private val base get()=if(embedded)"https://www.youtube.com/youtubei/v1/"else"https://youtubei.googleapis.com/youtubei/v1/"
    val mediaHeaders get()=mapOf("User-Agent" to userAgent,"Referer" to "https://www.youtube.com/","Origin" to "https://www.youtube.com")
    private fun context(language:String,visitor:String?=null)=buildJsonObject {
        put("client",buildJsonObject {
            put("clientName",name);put("clientVersion",version);put("userAgent",userAgent)
            put("hl",Locale.forLanguageTag(language).toLanguageTag());put("gl",Locale.getDefault().country.takeIf{it.length==2} ?: "US")
            put("clientScreen",if(embedded)"EMBED"else"WATCH");put("platform",if(embedded)"DESKTOP"else"MOBILE");put("utcOffsetMinutes",0)
            visitor?.let{put("visitorData",it)};device.forEach{(key,value)->put(key,value)}
        })
        put("request",buildJsonObject{put("useSsl",true);put("internalExperimentFlags",JsonArray(emptyList()))})
        put("user",buildJsonObject{put("lockedSafetyMode",false)})
    }
    private fun request(url:String,body:JsonObject,visitor:String?=null):Request = Request.Builder().url(url)
        .apply{mediaHeaders.forEach{(key,value)->header(key,value)}}
        .header("X-YouTube-Client-Name",id).header("X-YouTube-Client-Version",version)
        .header("X-Goog-Api-Format-Version","2").apply{visitor?.let{header("X-Goog-Visitor-Id",it)}}
        .post(body.toString().toRequestBody("application/json".toMediaType())).build()
    fun visitorRequest(language:String):Request {
        // Match NewPipe: mobile visitor bootstrap on youtube.com, except Android reel on googleapis.
        val visitorBase=if(reel)base else "https://www.youtube.com/youtubei/v1/"
        return request(visitorBase+"visitor_id?prettyPrint=false",buildJsonObject{put("context",context(language))})
    }
    fun playerRequest(videoId:String,language:String,visitor:String,cpn:String,t:String,timestamp:Int?=null):Request {
        require(visitor.isNotBlank());require(cpn.length==16)
        val player=buildJsonObject {
            put("videoId",videoId);put("cpn",cpn);put("contentCheckOk",true);put("racyCheckOk",true)
            if(embedded && timestamp!=null)put("playbackContext",buildJsonObject{put("contentPlaybackContext",buildJsonObject{put("signatureTimestamp",timestamp);put("html5Preference","HTML5_PREF_WANTS")})})
        }
        val body=buildJsonObject {
            val ctx=context(language,visitor)
            put("context",if(embedded)JsonObject(ctx+mapOf("thirdParty" to buildJsonObject{put("embedUrl","https://www.youtube.com/embed/$videoId")}))else ctx)
            if(reel){put("playerRequest",player);put("disablePlayerResponse",false)}else player.forEach{(key,value)->put(key,value)}
        }
        val url=(base+if(reel)"reel/reel_item_watch"else"player").toHttpUrl().newBuilder().addQueryParameter("prettyPrint","false")
        if(!embedded)url.addQueryParameter("t",t).addQueryParameter("id",videoId)
        if(reel)url.addQueryParameter("\$fields","playerResponse")
        return request(url.build().toString(),body,visitor)
    }
    companion object {
        // Versions/device identities verified against NewPipe v0.26.5 and yt-dlp's client definitions.
        val vision=InnertubeSession("VISIONOS","1.02","101","Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",mapOf("deviceMake" to JsonPrimitive("Apple"),"deviceModel" to JsonPrimitive("RealityDevice17,1"),"osName" to JsonPrimitive("visionOS"),"osVersion" to JsonPrimitive("26.5.23O471")))
        val android=InnertubeSession("ANDROID","21.26.364","3","com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip",mapOf("androidSdkVersion" to JsonPrimitive(30),"osName" to JsonPrimitive("Android"),"osVersion" to JsonPrimitive("11")),reel=true)
        val ios=InnertubeSession("IOS","21.26.4","5","com.google.ios.youtube/21.26.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",mapOf("deviceMake" to JsonPrimitive("Apple"),"deviceModel" to JsonPrimitive("iPhone16,2"),"osName" to JsonPrimitive("iPhone"),"osVersion" to JsonPrimitive("18.3.2.22D82")))
        val web=InnertubeSession("WEB_EMBEDDED_PLAYER","2.20260708.00.00","56","Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.5 Safari/605.1.15")
    }
}
