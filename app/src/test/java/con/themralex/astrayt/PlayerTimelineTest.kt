package con.themralex.astrayt

import con.themralex.astrayt.feature.player.PlayerTimeline
import org.junit.Assert.*
import org.junit.Test

class PlayerTimelineTest {
    @Test fun unknownDurationNeverProducesInvalidSeekOrProgress() {
        listOf(0L,-1L,Long.MIN_VALUE).forEach{duration->
            assertEquals(0f,PlayerTimeline.fraction(10,duration),0f)
            assertEquals(0L,PlayerTimeline.seek(.5f,duration))
        }
    }
    @Test fun scrubBoundsStayInsideTheMediaIncludingTheExactEnd() {
        assertEquals(0L,PlayerTimeline.seek(-.2f,120000))
        assertEquals(120000L,PlayerTimeline.seek(1.2f,120000))
        assertEquals(120000L,PlayerTimeline.seek(1f,120000))
        assertEquals(60000L,PlayerTimeline.seek(.5f,120000))
    }
    @Test fun invalidPointerValuesDoNotMovePlaybackToAnArbitraryPosition() {
        listOf(Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY).forEach{assertEquals(0L,PlayerTimeline.seek(it,60000))}
    }
    @Test fun staleOrBufferedPositionsAreClampedForTheSeekBar() {
        assertEquals(0f,PlayerTimeline.fraction(-1,100),0f)
        assertEquals(1f,PlayerTimeline.fraction(150,100),0f)
        assertEquals(.5f,PlayerTimeline.fraction(50,100),0f)
    }
    @Test fun watchedOrInvalidSavedPositionsRestartAndOtherVideosResume() {
        assertEquals(0L,PlayerTimeline.resume(95000,100000))
        assertEquals(0L,PlayerTimeline.resume(100001,100000))
        assertEquals(0L,PlayerTimeline.resume(-100,100000))
        assertEquals(0L,PlayerTimeline.resume(100,0))
        assertEquals(42000L,PlayerTimeline.resume(42000,100000))
    }
}
