package con.themralex.astrayt

import con.themralex.astrayt.youtube.*
import con.themralex.astrayt.download.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class SubtitlePolicyTest {
    private fun track(language:String,auto:Boolean=false)=SubtitleOption(language+if(auto)":auto"else":manual",language,language,"https://www.youtube.com/api/timedtext?v=sample",auto)
    @Test fun rememberedLanguagePrefersExactRegionThenHumanCaptions(){
        val tracks=listOf(track("en"),track("es",true),track("es"),track("es-MX",true))
        assertEquals("es:manual",SubtitlePolicy.select(tracks,"es")?.id)
        assertEquals("es-MX:auto",SubtitlePolicy.select(tracks,"es-MX")?.id)
        assertEquals("es:manual",SubtitlePolicy.select(tracks,"es-ES")?.id)
        assertNull(SubtitlePolicy.select(tracks,"fr"))
    }
    @Test fun parsesWebVttSettingsIdentifiersMultilineAndInlineTimestamps(){
        val input="WEBVTT\n\nintro\n00:01.200 --> 00:03.400 align:start position:0%\n<c>Hola &amp; adiós</c>\n<00:02.000>Otra línea\n\n2\n00:00:05.000 --> 00:00:06.000\nFinal\n"
        val cues=SubtitlePolicy.parseVtt(input)
        assertEquals(2,cues.size);assertEquals(1200000,cues[0].startUs);assertEquals("Hola & adiós\nOtra línea",cues[0].text)
        assertEquals(6000000,cues[1].endUs)
    }
    @Test fun ignoresMalformedEmptyAndBackwardsCues(){
        assertTrue(SubtitlePolicy.parseVtt("<html>Error</html>").isEmpty())
        assertTrue(SubtitlePolicy.parseVtt("00:03.000 --> 00:02.000\nBackwards\n\n00:90.000 --> 00:99.000\nInvalid").isEmpty())
        assertNull(SubtitlePolicy.timestamp("999999999999999999:00:00.000"))
    }
    @Test fun duplicateCuesDoNotAppearTwice(){assertEquals(1,SubtitlePolicy.parseVtt("00:01.000 --> 00:02.000\nHello\n\n00:01.000 --> 00:02.000\nHello").size)}
    @Test fun srtHasSequentialIndicesAndMillisecondTiming(){
        val cues=listOf(SubtitleCue(1200000,3400000,"Hola\nMundo"),SubtitleCue(3601000000,3602500000,"Otra"))
        assertEquals("1\n00:00:01,200 --> 00:00:03,400\nHola\nMundo\n\n2\n01:00:01,000 --> 01:00:02,500\nOtra\n",SubtitlePolicy.srt(cues))
    }
    @Test fun sponsorCutsRemoveCuesInsideAndShiftFollowingCues(){
        val cues=listOf(SubtitleCue(500000,1000000,"Antes"),SubtitleCue(2000000,3000000,"Sponsor"),SubtitleCue(6000000,7000000,"Después"))
        assertEquals(listOf(cues[0],SubtitleCue(3000000,4000000,"Después")),SubtitlePolicy.retime(cues,listOf(Cut(2000000,5000000))))
    }
    @Test fun aCueAcrossACutIsSplitAndBothPartsRemainSynchronized(){
        val result=SubtitlePolicy.retime(listOf(SubtitleCue(1000000,8000000,"Continúa")),listOf(Cut(2000000,5000000)))
        assertEquals(listOf(SubtitleCue(1000000,2000000,"Continúa"),SubtitleCue(2000000,5000000,"Continúa")),result)
    }
    @Test fun multipleCutsAndExactBoundariesDoNotAccumulateDrift(){
        val cuts=listOf(Cut(1000000,2000000),Cut(4000000,6000000))
        assertEquals(listOf(SubtitleCue(1000000,3000000,"Between"),SubtitleCue(3000000,4000000,"After")),SubtitlePolicy.retime(listOf(SubtitleCue(2000000,4000000,"Between"),SubtitleCue(6000000,7000000,"After")),cuts))
        assertEquals(emptyList<SubtitleCue>(),SubtitlePolicy.retime(listOf(SubtitleCue(1000000,2000000,"Removed")),cuts))
    }
    @Test fun subtitleChoiceSurvivesQueueSerializationAndOldQueueEntriesStayDisabled(){
        val options=DownloadOptions(subtitleId="es:manual",subtitleLanguage="es",height=1080,trackId="es.3",planPinned=true)
        assertEquals(options,Json.decodeFromString<DownloadOptions>(Json.encodeToString(options)))
        assertNull(Json.decodeFromString<DownloadOptions>("{\"height\":720}").subtitleLanguage)
        assertEquals("es.3",Json.decodeFromString<DownloadOptions>(Json.encodeToString(options)).trackId)
    }

    @Test fun explicitOffAndAMissingExplicitTrackDoNotReenableRememberedCaptions(){
        val tracks=listOf(track("es"),track("en"))
        assertNull(SubtitlePolicy.restore(tracks,"es",true,null))
        assertNull(SubtitlePolicy.restore(tracks,"es",true,"fr:manual"))
        assertEquals("es:manual",SubtitlePolicy.restore(tracks,"es",false,null)?.id)
    }
    @Test fun complementaryPlayerResponsesExposeManualAndAutomaticCaptions(){
        val response=Json.parseToJsonElement("""{"playabilityStatus":{"status":"OK"},"videoDetails":{"videoId":"F0OkwXKcPSE","title":"Video"},"streamingData":{"formats":[{"itag":18,"url":"https://example.test/video","mimeType":"video/mp4","height":360}]},"captions":{"playerCaptionsTracklistRenderer":{"captionTracks":[{"languageCode":"es","baseUrl":"https://www.youtube.com/api/timedtext?v=F0OkwXKcPSE","name":{"simpleText":"Español"},"vssId":".es"},{"languageCode":"en","baseUrl":"https://www.youtube.com/api/timedtext?v=F0OkwXKcPSE&lang=en","name":{"simpleText":"English"},"kind":"asr","vssId":"a.en"}]}}}""").jsonObject
        val details=InnertubeFallback.parse("F0OkwXKcPSE",response,null,decode={it})
        assertEquals(listOf("es:manual","en:auto"),details.subtitles.map{it.id})
        assertFalse(details.subtitles[0].autoGenerated);assertTrue(details.subtitles[1].autoGenerated)
        val merged=StreamRecovery.merge(details,details.copy(subtitles=listOf(track("fr"))))
        assertEquals(3,merged.subtitles.size)
    }
    @Test fun htmlEntitiesAndTabSeparatedVttSettingsAreDecoded(){
        val cue=SubtitlePolicy.parseVtt("00:01.000 --> 00:02.000\talign:start\n&#161;Qu&#233; bien! &lt;3").single()
        assertEquals("¡Qué bien! <3",cue.text)
    }
}
