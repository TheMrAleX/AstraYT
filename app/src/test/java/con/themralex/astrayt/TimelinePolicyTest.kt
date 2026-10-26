package con.themralex.astrayt

import con.themralex.astrayt.timeline.*
import con.themralex.astrayt.download.Cut
import con.themralex.astrayt.data.prefs.Settings
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class TimelinePolicyTest {
    @Test fun chaptersEnabledByDefaultButSponsorRecognitionAndSkippingAreOptional(){
        val settings=Json.decodeFromString<Settings>("{}")
        assertTrue(settings.chapterRecognitionEnabled);assertFalse(settings.sponsorRecognitionEnabled);assertFalse(settings.sponsorSkipEnabled)
    }
    @Test fun metadataFollowsSupportedInterfaceLanguageRatherThanAudio(){
        assertEquals("es",TimelinePolicy.language("es-MX"));assertEquals("en",TimelinePolicy.language("en-US"));assertEquals("es",TimelinePolicy.language("fr"))
    }
    @Test fun explicitChaptersAreBoundedSortedAndUnique(){
        assertEquals(listOf(Chapter(0,"Inicio"),Chapter(10000,"Otro")),TimelinePolicy.chapters(listOf(Chapter(-1,"Bad"),Chapter(10000,"Otro"),Chapter(0," Inicio "),Chapter(10000,"Duplicate"),Chapter(20000,"End"),Chapter(5000," ")),20000))
    }
    @Test fun creatorTimestampsCanBeReadFromLocalizedHtmlDescription(){
        val input="<p>0:00 Inicio<br>0:15 Herramientas &amp; trucos<br>1:05 Prueba</p>"
        assertEquals(listOf(Chapter(0,"Inicio"),Chapter(15000,"Herramientas & trucos"),Chapter(65000,"Prueba")),TimelinePolicy.fromDescription(input,120000))
    }
    @Test fun randomLinksAndInvalidTimestampListsAreNotChapters(){
        assertTrue(TimelinePolicy.fromDescription("Mira 1:00 esto\n2:00 Otro",200000).isEmpty())
        assertTrue(TimelinePolicy.fromDescription("0:00 A\n0:03 B\n0:09 C",200000).isEmpty())
        assertTrue(TimelinePolicy.fromDescription("0:00 A\n1:99 B\n2:10 C",200000).isEmpty())
    }
    @Test fun chapterBoundariesUseHalfOpenIntervals(){
        val chapters=listOf(Chapter(0,"A"),Chapter(10000,"B"),Chapter(20000,"C"))
        assertEquals(0,TimelinePolicy.chapterAt(chapters,9999));assertEquals(1,TimelinePolicy.chapterAt(chapters,10000));assertEquals(2,TimelinePolicy.chapterAt(chapters,20000))
    }
    @Test fun sponsorApiKeepsCategoryAndMillisecondBoundariesButRejectsUnsupportedActions(){
        val response="""[{"category":"sponsor","segment":[1.25,2.75],"actionType":"skip"},{"category":"intro","segment":[3,8]},{"category":"sponsor","segment":[4,6],"actionType":"mute"},{"category":"unknown","segment":[5,7]},{"category":"sponsor","segment":[7,3]},{"category":"outro","segment":[9,12]}]"""
        assertEquals(listOf(SponsorSegment(1250,2750,"sponsor"),SponsorSegment(3000,8000,"intro"),SponsorSegment(9000,10000,"outro")),TimelinePolicy.parseSponsors(response,10000))
    }
    @Test fun malformedApiEntriesDoNotDiscardValidSegments(){
        val response="""[null,{}, {"category":[],"segment":[0,3]}, {"category":"sponsor","segment":[{},3]}, {"category":"sponsor","segment":[0,3]}]"""
        assertEquals(listOf(SponsorSegment(0,3000,"sponsor")),TimelinePolicy.parseSponsors(response))
    }
    @Test fun skipStartsInsideSelectedSegmentsAndStopsExactlyAtEnd(){
        val segments=listOf(SponsorSegment(1000,2500,"sponsor"),SponsorSegment(5000,6000,"intro"))
        assertNull(TimelinePolicy.skipAt(segments,setOf("sponsor"),999,10000))
        assertEquals(2500L,TimelinePolicy.skipAt(segments,setOf("sponsor"),1000,10000)!!.endMs)
        assertEquals(2500L,TimelinePolicy.skipAt(segments,setOf("sponsor"),2499,10000)!!.endMs)
        assertNull(TimelinePolicy.skipAt(segments,setOf("sponsor"),2500,10000))
        assertNull(TimelinePolicy.skipAt(segments,setOf("sponsor"),5100,10000))
    }
    @Test fun overlappingSelectedSegmentsSkipTheirUnionWithoutSkippingUnselectedParts(){
        val segments=listOf(SponsorSegment(1000,3000,"sponsor"),SponsorSegment(2900,5000,"intro"),SponsorSegment(4900,8000,"outro"))
        assertEquals(5000L,TimelinePolicy.skipAt(segments,setOf("sponsor","intro"),2000,10000)!!.endMs)
        assertEquals(7000L,TimelinePolicy.skipAt(segments,TimelinePolicy.colors.keys,2000,7000)!!.endMs)
    }
    @Test fun cutTimelinesRetainChapterTitlesAndRemapRemainingSponsorSegments(){
        val source=TimelineCapture(listOf(Chapter(0,"A"),Chapter(10000,"Removed"),Chapter(20000,"C")),listOf(SponsorSegment(5000,25000,"intro")),"es")
        val saved=TimelinePolicy.retime(source,listOf(Cut(10_000_000,20_000_000)),30000,"content://video/1")
        assertEquals(listOf(Chapter(0,"A"),Chapter(10000,"C")),saved.chapters)
        assertEquals(listOf(SponsorSegment(5000,10000,"intro"),SponsorSegment(10000,15000,"intro")),saved.sponsors)
        assertEquals("content://video/1",saved.mediaUri)
    }
    @Test fun completeRemovedSponsorSegmentsAreNotSkippedTwice(){
        val capture=TimelineCapture(listOf(Chapter(0,"Inicio")),listOf(SponsorSegment(10000,20000,"sponsor")),"es")
        assertTrue(TimelinePolicy.retime(capture,listOf(Cut(10_000_000,20_000_000)),30000,"uri").sponsors.isEmpty())
    }
    @Test fun originalSponsorBlockColorsAreStable(){
        assertEquals(0xFF00D400,TimelinePolicy.colors["sponsor"])
        assertEquals(0xFFFFFF00,TimelinePolicy.colors["selfpromo"])
        assertEquals(0xFF00FFFF,TimelinePolicy.colors["intro"])
    }
    @Test fun offlineRecordRoundTripPreservesLanguageCategoriesAndChapters(){
        val original=VideoTimeline("content://media/1","es",listOf(Chapter(0,"Inicio")),listOf(SponsorSegment(100,200,"sponsor")))
        assertEquals(original,Json.decodeFromString<VideoTimeline>(Json.encodeToString(VideoTimeline.serializer(),original)))
    }
    @Test fun coloredRailMergesDuplicateRangesAndPreservesChapterGaps() {
        val chapters=listOf(Chapter(0,"A"),Chapter(5000,"B"),Chapter(10000,"C"))
        val segments=listOf(SponsorSegment(1000,8000,"sponsor"),SponsorSegment(2000,7000,"sponsor"),SponsorSegment(3000,6000,"intro"))
        assertEquals(listOf(SponsorSegment(1000,5000,"sponsor"),SponsorSegment(5000,8000,"sponsor"),SponsorSegment(3000,5000,"intro"),SponsorSegment(5000,6000,"intro")),TimelinePolicy.railSegments(chapters,15000,segments))
    }
    @Test fun railClampsEndAndDoesNotCreateEmptyBoundaryFragments() {
        assertEquals(listOf(SponsorSegment(5000,10000,"sponsor")),TimelinePolicy.railSegments(listOf(Chapter(0,"A"),Chapter(5000,"B")),10000,listOf(SponsorSegment(5000,20000,"sponsor"))))
    }
}
