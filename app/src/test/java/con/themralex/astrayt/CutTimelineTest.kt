package con.themralex.astrayt
import con.themralex.astrayt.download.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
class CutTimelineTest {
    @Test fun overlappingAndAdjacentCategoriesAreRemovedOnlyOnce() {
        val cuts=CutTimeline.normalize(listOf(Cut(30,50),Cut(10,35),Cut(50,60),Cut(80,90)),100)
        assertEquals(listOf(Cut(10,60),Cut(80,90)),cuts)
        assertEquals(30L,CutTimeline.outputTime(90,cuts))
    }
    @Test fun exactBoundariesAndBothTracksShareOneContinuousClock() {
        val cuts=listOf(Cut(100,200),Cut(400,500))
        assertEquals(99L,CutTimeline.outputTime(99,cuts));assertNull(CutTimeline.outputTime(100,cuts));assertNull(CutTimeline.outputTime(199,cuts))
        assertEquals(100L,CutTimeline.outputTime(200,cuts));assertEquals(300L,CutTimeline.outputTime(500,cuts))
        assertEquals(1L,CutTimeline.outputTime(501,cuts)!!-CutTimeline.outputTime(500,cuts)!!)
    }
    @Test fun invalidIntervalsDoNotChangeDuration() {
        assertEquals(listOf(Cut(0,20),Cut(90,100)),CutTimeline.normalize(listOf(Cut(-10,20),Cut(90,200),Cut(70,50),Cut(40,40)),100))
        assertEquals(12L,CutTimeline.outputTime(12,emptyList()))
        assertTrue(CutTimeline.normalize(listOf(Cut(10,20)),0).isEmpty())
    }
    @Test fun oldDownloadsDoNotSuddenlyEnableSponsorBlock() {
        assertTrue(Json.decodeFromString<DownloadOptions>("{}").sponsorBlockCategories.isEmpty())
        val prefs=Json.decodeFromString<con.themralex.astrayt.data.prefs.Settings>("{}")
        assertFalse(prefs.sponsorBlockEnabled)
    }
}
