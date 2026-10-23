package con.themralex.astrayt

import con.themralex.astrayt.youtube.*
import org.junit.Assert.*
import org.junit.Test

class StreamRecoveryTest {
    private val progressive=MediaStream("18","https://example.test/360","mp4","avc1.42001E",height=360)
    private val audio=MediaStream("140","https://example.test/audio","m4a","mp4a.40.2",128000)
    private val hd=MediaStream("137","https://example.test/1080","mp4","avc1.640028",height=1080,videoOnly=true)
    private fun details(videos:List<MediaStream>,tracks:List<AudioTrackOption> = emptyList())=VideoDetails("abcdefghijk","Title","channel","Creator","","",60000,100,0,"",videos,tracks)
    private fun original(stream:MediaStream=audio)=AudioTrackOption("original","en","ORIGINAL","Original",listOf(stream),true)

    @Test fun progressiveOnlyResponseTriggersRecoveryEvenWhenItIsPlayable() {
        val primary=details(listOf(progressive))
        assertTrue(StreamPlanner.options(primary,null).isNotEmpty())
        assertTrue(StreamRecovery.needsAlternate(primary))
    }
    @Test fun recoveryRestoresAudioAndSelectedHighDefinitionQuality() {
        val primary=details(listOf(progressive)).copy(likeCount=345)
        val recovered=StreamRecovery.merge(primary,details(listOf(hd),listOf(original())))
        val choices=StreamPlanner.options(recovered,recovered.tracks.single())
        assertEquals(1080,StreamPlanner.default(choices,1080)?.height)
        assertEquals("137",StreamPlanner.default(choices,1080)?.video?.id)
        assertEquals("140",StreamPlanner.options(recovered,recovered.tracks.single(),true).single().audio?.id)
        assertEquals(345L,recovered.likeCount)
        assertFalse(StreamRecovery.needsAlternate(recovered))
    }
    @Test fun audioCanBeExtractedFromProgressiveMp4WithoutStandaloneTrack() {
        val plan=StreamPlanner.options(details(listOf(progressive)),null,true).single()
        assertTrue(plan.extractAudio);assertEquals("m4a",plan.container)
        assertEquals("18",plan.audio?.id);assertNull(plan.video);assertEquals(0,plan.height)
    }
    @Test fun standaloneAudioIsPreferredOverDownloadingAWholeVideo() {
        val track=original()
        val plan=StreamPlanner.options(details(listOf(progressive),listOf(track)),track,true).single()
        assertFalse(plan.extractAudio);assertEquals("140",plan.audio?.id)
    }
    @Test fun progressiveOriginalDoesNotReplaceAnUnavailableDubbedTrack() {
        val dub=AudioTrackOption("es","es","DUBBED","Español",emptyList())
        assertTrue(StreamPlanner.options(details(listOf(progressive),listOf(dub)),dub,true).isEmpty())
    }
    @Test fun audioMp4SuffixAndCaseAreAccepted() {
        val track=original(audio.copy(format="MP4",codec="MP4A.40.2"))
        assertEquals("140",track.bestStreamForMp4?.id)
        assertEquals("m4a",StreamPlanner.options(details(emptyList(),listOf(track)),track,true).single().container)
    }
    @Test fun alternateHeadersAreRetainedAndDuplicateStreamIdsDoNotHideQuality() {
        val alternate=hd.copy(url="https://example.test/fresh",headers=mapOf("User-Agent" to "mobile"))
        val merged=StreamRecovery.merge(details(listOf(hd,progressive)),details(listOf(alternate),listOf(original())))
        assertEquals(2,merged.videos.size)
        val selected=StreamPlanner.default(StreamPlanner.options(merged,merged.tracks.single()),1080)!!
        assertEquals(alternate,selected.video)
        assertEquals(1080,selected.height)
    }
    @Test fun lowResolutionAdaptiveResponseStillTriesToRecoverHigherFormats() {
        assertTrue(StreamRecovery.needsAlternate(details(listOf(hd.copy(height=360)),listOf(original()))))
    }
    @Test fun sequentialSelectionsDoNotReuseThePreviousVideosQuality() {
        val track=original()
        val first=details(listOf(progressive))
        assertEquals(360,StreamPlanner.default(StreamPlanner.options(first,null),1080)?.height)
        val second=details(listOf(hd,hd.copy(id="136",height=720)),listOf(track))
        assertEquals(1080,StreamPlanner.default(StreamPlanner.options(second,track),1080)?.height)
        assertEquals(720,StreamPlanner.default(StreamPlanner.options(second,track),720)?.height)
    }

    @Test fun resumedProgressiveAudioKeepsItsSourceWhenStandaloneAudioAppears() {
        val track=original()
        val refreshed=details(listOf(progressive,hd),listOf(track))
        val resumed=StreamPlanner.options(refreshed,track,true,pinnedAudioId="18").single()
        assertTrue(resumed.extractAudio)
        assertEquals("18",resumed.audio?.id)
    }

    @Test fun originalAudioAliasesCombineAacAndOpusWithoutChangingTheSavedTrackId() {
        val mp4Track=original().copy(id="en-original",locale="en-US")
        val opus=audio.copy(id="251",format="webm",codec="opus")
        val vp9=hd.copy(id="313",format="webm",codec="vp09",height=2160)
        val combined=StreamRecovery.merge(details(listOf(progressive),listOf(mp4Track)),details(listOf(vp9),listOf(original(opus).copy(id="en.4",locale="en"))))
        assertEquals("en-original",combined.tracks.single().id)
        assertEquals(setOf("140","251"),combined.tracks.single().streams.map{it.id}.toSet())
        assertEquals(2160,StreamPlanner.options(combined,combined.tracks.single()).single().height)
    }
    @Test fun distinctDubbedLanguagesAndRegionsAreNeverMerged() {
        val es=original().copy(id="es",locale="es-ES",type="DUBBED")
        val lat=es.copy(id="es-419",locale="es-419")
        val combined=StreamRecovery.merge(details(emptyList(),listOf(es)),details(emptyList(),listOf(lat)))
        assertEquals(2,combined.tracks.size)
    }
    @Test fun hdCanUseAudioFromProgressiveMp4WhenIndependentAudioIsAbsent() {
        val plans=StreamPlanner.options(details(listOf(progressive,hd)),null)
        assertEquals(1080,plans.single().height)
        assertEquals("18",plans.single().audio?.id)
        assertEquals("137",plans.single().video?.id)
    }
    @Test fun pinnedProgressiveAudioForHdSurvivesWhenStandaloneAudioAppears() {
        val track=original()
        val plan=StreamPlanner.options(details(listOf(progressive,hd),listOf(track)),track,pinnedAudioId="18").single()
        assertEquals("18",plan.audio?.id)
        assertEquals(1080,plan.height)
    }
    @Test fun h264Above1080IsAvailableWhenThereIsNoCompatibleWebmAlternative() {
        val track=original()
        val plan=StreamPlanner.options(details(listOf(hd.copy(height=1440)),listOf(track)),track).single()
        assertEquals(1440,plan.height);assertEquals("mp4",plan.container)
    }

    @Test fun resumedHdKeepsItsPinnedProgressiveAudioEvenIfPreferencesNowPickADub() {
        val dub=original().copy(id="es",locale="es",type="DUBBED",default=false)
        val plan=StreamPlanner.options(details(listOf(progressive,hd),listOf(dub)),dub,pinnedAudioId="18").single()
        assertEquals("18",plan.audio?.id)
        assertEquals(1080,plan.height)
    }

}
