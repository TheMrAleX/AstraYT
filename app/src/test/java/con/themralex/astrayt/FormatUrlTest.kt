package con.themralex.astrayt

import con.themralex.astrayt.youtube.*
import java.net.URLEncoder
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class FormatUrlTest {
    private fun cipher(url:String,extra:String)=buildJsonObject{put("signatureCipher","url="+URLEncoder.encode(url,"UTF-8")+"&"+extra)}
    @Test fun signatureCipherRecoversHdUrlAndPreservesOtherParameters() {
        val format=cipher("https://example.test/video?n=encrypted&itag=137&token=a%2Bb","s=encrypted%2Bsignature&sp=sig")
        val url=FormatUrl.resolve(format){assertEquals("encrypted+signature",it);"signed/+="}!!.toHttpUrl()
        assertEquals("signed/+=",url.queryParameter("sig"))
        assertEquals("encrypted",url.queryParameter("n"))
        assertEquals("137",url.queryParameter("itag"))
        assertEquals("a+b",url.queryParameter("token"))
    }
    @Test fun missingSpUsesTheSignatureParameter() {
        val url=FormatUrl.resolve(cipher("https://example.test/v","s=cipher")){"clear"}!!.toHttpUrl()
        assertEquals("clear",url.queryParameter("signature"))
    }
    @Test fun unsafeOrIncompleteUrlsAreRejected() {
        assertNull(FormatUrl.resolve(buildJsonObject{put("url","javascript:bad")}){it})
        assertNull(FormatUrl.resolve(cipher("https://example.test/v","sp=sig")){it})
    }
    @Test fun cipherFormatsPassSignatureThenThrottlingAndKeepClientHeaders() {
        val hd=cipher("https://example.test/v?n=cipher","s=signature&sp=sig").toMutableMap().apply{
            put("itag",JsonPrimitive(137));put("qualityLabel",JsonPrimitive("1080p60"));put("mimeType",JsonPrimitive("video/mp4; codecs=\"avc1\""))
        }
        val response=buildJsonObject{
            put("playabilityStatus",buildJsonObject{put("status","OK")})
            put("videoDetails",buildJsonObject{put("videoId","abcdefghijk")})
            put("streamingData",buildJsonObject{put("adaptiveFormats",JsonArray(listOf(JsonObject(hd))))})
        }
        val result=InnertubeFallback.parse("abcdefghijk",response,null,mapOf("User-Agent" to "embedded"),signature={"signed"}){url->assertTrue(url.contains("sig=signed"));url.replace("n=cipher","n=clear")}
        val stream=result.videos.single()
        assertEquals(1080,stream.height);assertTrue(stream.url.contains("n=clear"))
        assertEquals("embedded",stream.headers["User-Agent"])
    }
}
