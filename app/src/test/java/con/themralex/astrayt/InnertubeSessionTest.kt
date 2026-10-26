package con.themralex.astrayt

import con.themralex.astrayt.youtube.*
import java.io.IOException
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

/** Request/response logic only; no external requests and no Android runtime.
 * Fixture reduced from NewPipeExtractor v0.26.5, stream/audiotracklanguage/generated_mock_3.json.
 * It is a recorded response for kX3nB4PpJko, not a live reproduction of F0OkwXKcPSE.
 */
class InnertubeSessionTest {
    private val fixture=Json.parseToJsonElement(javaClass.getResource("/youtube-multilingual-player.json")!!.readText()).jsonObject
    private val videoId=fixture.getValue("videoDetails").jsonObject.getValue("videoId").jsonPrimitive.content
    private val visitor="anonymous-test-session"
    private val bootstrap=buildJsonObject{put("responseContext",buildJsonObject{put("visitorData",visitor)})}
    private fun body(request:Request):JsonObject {val buffer=Buffer();request.body!!.writeTo(buffer);return Json.parseToJsonElement(buffer.readUtf8()).jsonObject}

    @Test fun visionSessionBootstrapsBeforePlayerAndReturnsAllRecordedMrBeastLanguages() {
        val calls=mutableListOf<Request>()
        val recovered=InnertubeFallback(OkHttpClient()){request,remaining->
            assertTrue(remaining in 1..8000);calls.add(request)
            if(request.url.encodedPath.endsWith("visitor_id"))bootstrap else {
                val payload=body(request);val client=payload.getValue("context").jsonObject.getValue("client").jsonObject
                assertEquals(visitor,client.getValue("visitorData").jsonPrimitive.content)
                assertEquals(visitor,request.header("X-Goog-Visitor-Id"))
                assertEquals("VISIONOS",client.getValue("clientName").jsonPrimitive.content)
                assertEquals("WATCH",client.getValue("clientScreen").jsonPrimitive.content)
                assertEquals("es-419",client.getValue("hl").jsonPrimitive.content)
                assertEquals(16,payload.getValue("cpn").jsonPrimitive.content.length)
                assertEquals(videoId,payload.getValue("videoId").jsonPrimitive.content)
                assertEquals("youtubei.googleapis.com",request.url.host)
                fixture
            }
        }.details(videoId,null,language="es-419",discoverAudio=true)
        assertEquals(2,calls.size)
        assertEquals("www.youtube.com",calls.first().url.host)
        assertEquals(25,recovered.tracks.size)
        val selection=AudioTrackSelector.select(recovered.tracks,"es-419")
        assertTrue(selection.prompt);assertEquals("es.3",selection.track?.id)
        val plan=StreamPlanner.default(StreamPlanner.options(recovered,selection.track),1080)!!
        assertEquals(1080,plan.height)
        assertEquals("/audio/es.3",java.net.URI(plan.audio!!.url).path)
        assertNotNull(plan.audio.url.toHttpUrl().queryParameter("cpn"))
        assertEquals(InnertubeSession.vision.userAgent,plan.audio.headers["User-Agent"])
    }
    @Test fun unavailableVisionClientFallsBackToASeparateAndroidVisitorAndNestedReelResponse() {
        val clients=mutableListOf<String>()
        val recovered=InnertubeFallback(OkHttpClient()){request,_->
            val client=body(request).getValue("context").jsonObject.getValue("client").jsonObject
            val name=client.getValue("clientName").jsonPrimitive.content
            clients.add(name)
            if(name=="VISIONOS")throw IOException("HTTP 503")
            if(request.url.encodedPath.endsWith("visitor_id"))bootstrap else {
                val payload=body(request)
                assertEquals(visitor,client.getValue("visitorData").jsonPrimitive.content)
                assertEquals("/youtubei/v1/reel/reel_item_watch",request.url.encodedPath)
                assertEquals(videoId,payload.getValue("playerRequest").jsonObject.getValue("videoId").jsonPrimitive.content)
                assertFalse(payload.getValue("disablePlayerResponse").jsonPrimitive.boolean)
                buildJsonObject{put("playerResponse",fixture)}
            }
        }.details(videoId,null,language="es",discoverAudio=true)
        assertEquals(listOf("VISIONOS","ANDROID","ANDROID"),clients)
        assertEquals("es.3",AudioTrackSelector.select(recovered.tracks,"es").track?.id)
    }
    @Test fun missingVisitorNeverSendsAnUninitializedPlayerRequest() {
        val urls=mutableListOf<String>()
        val original=InnertubeFallback.parse(videoId,fixture,null){it}.let{d->d.copy(tracks=d.tracks.filter{it.type=="ORIGINAL"})}
        val recovered=InnertubeFallback(OkHttpClient()){request,_->urls.add(request.url.encodedPath);buildJsonObject{put("responseContext",buildJsonObject{})}}
            .details(videoId,original,language="es",discoverAudio=true)
        assertTrue(urls.all{it.endsWith("visitor_id")});assertEquals(4,urls.size)
        assertTrue(StreamRecovery.needsAudioDiscovery(recovered,"es"))
    }
    @Test fun playbackContextVisitorAndHeadersMatchForEveryProfile() {
        listOf(InnertubeSession.vision,InnertubeSession.android,InnertubeSession.ios,InnertubeSession.web).forEach { profile->
            val boot=body(profile.visitorRequest("es"));val request=profile.playerRequest(videoId,"es",visitor,"abcdefghijklmnop","test",12345)
            val payload=body(request);val client=payload.getValue("context").jsonObject.getValue("client").jsonObject
            assertEquals(boot.getValue("context").jsonObject.getValue("client").jsonObject.getValue("clientName"),client.getValue("clientName"))
            assertEquals(profile.id,request.header("X-YouTube-Client-Name"))
            assertEquals(profile.version,request.header("X-YouTube-Client-Version"))
            assertEquals(profile.userAgent,request.header("User-Agent"))
            assertEquals(visitor,client.getValue("visitorData").jsonPrimitive.content)
            assertEquals("application/json; charset=utf-8",request.body!!.contentType().toString())
        }
    }
    @Test fun interfaceMetadataLanguageDoesNotChangePreferredAudioLanguage() {
        val requested=mutableListOf<String>()
        val recovered=InnertubeFallback(OkHttpClient()){request,_->
            val payload=body(request)
            requested.add(payload.getValue("context").jsonObject.getValue("client").jsonObject.getValue("hl").jsonPrimitive.content)
            if(request.url.encodedPath.endsWith("visitor_id"))bootstrap else fixture
        }.details(videoId,null,language="en",metadataLanguage="es",discoverAudio=true)
        assertTrue(requested.isNotEmpty());assertTrue(requested.all{it=="es"})
        assertEquals("es",recovered.metadataLanguage)
        assertEquals("en",AudioTrackSelector.select(recovered.tracks,"en").track?.locale?.substringBefore('-'))
    }
}
