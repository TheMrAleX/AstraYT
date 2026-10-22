package con.themralex.astrayt
import con.themralex.astrayt.youtube.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
class InnertubeFallbackTest {
    private fun response(formats:String)=Json.parseToJsonElement("""{"playabilityStatus":{"status":"OK"},"videoDetails":{"videoId":"abcdefghijk","title":"Real title","author":"Creator","channelId":"UC123","lengthSeconds":"60","viewCount":"150"},"streamingData":{"adaptiveFormats":$formats}}""").jsonObject
    @Test fun mobileResponseRetainsAudioIdentityCodecAndByteLength() {
        val data=response("""[{"itag":137,"url":"https://example.test/v?n=x","mimeType":"video/mp4; codecs=\"avc1.640028\"","height":1080,"fps":60,"contentLength":"2000"},{"itag":140,"url":"https://example.test/a","mimeType":"audio/mp4; codecs=\"mp4a.40.2\"","bitrate":128000,"audioTrack":{"id":"es.4","displayName":"Español","audioIsDefault":true}}]""")
        val details=InnertubeFallback.parse("abcdefghijk",data,null){it.replace("n=x","n=solved")}
        assertEquals("Real title",details.title);assertEquals(60000L,details.durationMs);assertEquals(-1L,details.likeCount)
        val plan=StreamPlanner.options(details,details.tracks.single()).single()
        assertEquals("mp4",plan.container);assertEquals(1080,plan.height);assertEquals(60,plan.fps)
        assertEquals("es.4",details.tracks.single().id);assertEquals("es",details.tracks.single().locale)
        assertEquals(2000L,details.videos.single().bytes);assertTrue(details.videos.single().url.endsWith("n=solved"))
    }
    @Test(expected=NoStreams::class) fun drmAndCipherWithoutUsableUrlsAreNeverOffered() {
        InnertubeFallback.parse("abcdefghijk",response("""[{"itag":137,"url":"https://example.test/v","mimeType":"video/mp4","drmFamilies":["WIDEVINE"]},{"itag":140,"signatureCipher":"s=secret"}]"""),null){it}
    }
    @Test(expected=NoStreams::class) fun playerResponseMustMatchRequestedVideo() {
        InnertubeFallback.parse("differentId",response("[]"),null){it}
    }
    @Test fun progressiveStreamAlreadyHasAudio() {
        val details=InnertubeFallback.parse("abcdefghijk",response("""[{"itag":18,"url":"https://example.test/v","mimeType":"video/mp4; codecs=\"avc1.42001E, mp4a.40.2\"","height":360}]"""),null){it}
        assertFalse(details.videos.single().videoOnly)
        assertNull(StreamPlanner.options(details,null).single().audio)
    }
    @Test fun oneBrokenAdaptiveUrlDoesNotDiscardOtherAudioAndVideoFormats() {
        val data=response("""[{"itag":137,"url":"https://example.test/bad","mimeType":"video/mp4; codecs=\"avc1\"","height":1080},{"itag":136,"url":"https://example.test/good","mimeType":"video/mp4; codecs=\"avc1\"","height":720},{"itag":140,"url":"https://example.test/audio","mimeType":"audio/mp4; codecs=\"mp4a.40.2\""}]""")
        val details=InnertubeFallback.parse("abcdefghijk",data,null){if(it.endsWith("bad"))throw IllegalArgumentException("Unusable signature") else it}
        assertEquals(720,StreamPlanner.options(details,details.tracks.single()).single().height)
        assertFalse(StreamPlanner.options(details,details.tracks.single(),true).single().extractAudio)
    }

}
