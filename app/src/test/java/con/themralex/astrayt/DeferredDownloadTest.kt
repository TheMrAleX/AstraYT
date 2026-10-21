package con.themralex.astrayt

import con.themralex.astrayt.download.DownloadOptions
import con.themralex.astrayt.youtube.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.VideoStream

class DeferredDownloadTest {
    private val audio=MediaStream("140","https://example.test/audio","m4a","mp4a.40.2",128000)
    private val track=AudioTrackOption("original","es","ORIGINAL","",listOf(audio),true)
    private fun details(vararg heights:Int)=VideoDetails("dQw4w9WgXcQ","Title","channel","Channel","","",60000,0,0,"",heights.map{MediaStream("$it","https://example.test/$it","mp4","avc1",1000000,it,30,videoOnly=true)},listOf(track))

    @Test fun queuedRequestSurvivesWithoutMetadata() {
        val request=DownloadOptions(height=2160,audioOnly=false)
        val restored=Json.decodeFromString<DownloadOptions>(Json.encodeToString(request))
        assertNull(restored.details)
        val d=details(144,360,720)
        assertEquals(720,StreamPlanner.default(StreamPlanner.options(d,track),restored.height)?.height)
    }
    @Test fun audioCanBeQueuedBeforeFormatsExist() {
        val restored=Json.decodeFromString<DownloadOptions>(Json.encodeToString(DownloadOptions(audioOnly=true)))
        assertNull(restored.details)
        assertEquals("140",StreamPlanner.options(details(720),track,restored.audioOnly).single().audio?.id)
    }
    @Test fun higherRequestsUseMaximumAndIntermediateRequestsUseNearestLower() {
        val options=StreamPlanner.options(details(144,360,720,1080),track)
        assertEquals(1080,StreamPlanner.default(options,2160)?.height)
        assertEquals(1080,StreamPlanner.default(options,1080)?.height)
        assertEquals(360,StreamPlanner.default(options,480)?.height)
        assertEquals(144,StreamPlanner.default(options,100)?.height)
        assertNull(StreamPlanner.default(emptyList(),2160))
    }
    @Test fun oldQueueJsonRemainsReadable() {
        val d=details(720)
        val json="""{"details":${Json.encodeToString(d)},"trackId":"original","height":720,"container":"mp4","audioOnly":false}"""
        val request=Json.decodeFromString<DownloadOptions>(json)
        assertEquals(d,request.details)
        assertEquals("mp4",request.container)
        assertEquals(720,request.height)
    }
    @Test fun extractorResolutionAndKnownItagWorkWithoutCodecMetadata() {
        val stream=VideoStream.Builder().setId("137").setContent("https://example.test/video",true).setMediaFormat(MediaFormat.MPEG_4).setIsVideoOnly(true).setResolution("1080p60").build()
        val mapped=ExtractorStreams.video(stream)
        assertEquals(1080,mapped.height)
        assertEquals(60,mapped.fps)
        val plan=StreamPlanner.options(details().copy(videos=listOf(mapped)),track).single()
        assertEquals(1080,plan.height)
        assertEquals("mp4",plan.container)
        assertEquals("140",plan.audio?.id)
    }
    @Test fun unknownMp4CodecDoesNotPretendToBeAvc() {
        val d=details(720).copy(videos=listOf(MediaStream("99999","https://example.test/v","mp4",height=720,videoOnly=true)))
        assertTrue(StreamPlanner.options(d,track).isEmpty())
        assertTrue(StreamPlanner.options(d.copy(videos=d.videos.map{it.copy(codec="av01.0.08M.08")}),track).isEmpty())
    }
    @Test fun fourKUsesCompatibleWebmAudio() {
        val opus=MediaStream("251","https://example.test/a","webm","opus",160000)
        val t=track.copy(streams=listOf(audio,opus))
        val d=details(720).copy(videos=details(720).videos+MediaStream("313","https://example.test/4k","webm","vp09",8000000,2160,30,videoOnly=true),tracks=listOf(t))
        val selected=StreamPlanner.default(StreamPlanner.options(d,t),2160)!!
        assertEquals(2160,selected.height)
        assertEquals("webm",selected.container)
        assertEquals("251",selected.audio?.id)
        assertEquals(720,StreamPlanner.default(StreamPlanner.options(d,t),1440)?.height)
    }
    @Test fun missingAacFallsBackToCompatibleVp9AndOpus() {
        val opus=MediaStream("251","https://example.test/a","webm","opus",160000)
        val t=track.copy(streams=listOf(opus))
        val d=details(720).copy(videos=details(720).videos+MediaStream("247","https://example.test/v","webm","vp09",1000000,720,30,videoOnly=true),tracks=listOf(t))
        val selected=StreamPlanner.options(d,t).single()
        assertEquals("247",selected.video?.id)
        assertEquals("251",selected.audio?.id)
        assertEquals("webm",selected.container)
    }
    @Test fun audioWithoutM4aCanStillDownloadAsWebm() {
        val opus=MediaStream("251","https://example.test/a","webm","opus",160000)
        val t=track.copy(streams=listOf(opus))
        val selected=StreamPlanner.options(details(720),t,true).single()
        assertNull(selected.video)
        assertEquals("251",selected.audio?.id)
        assertEquals("webm",selected.container)
        assertEquals("m4a",StreamPlanner.options(details(720),track,true).single().container)
    }
}
