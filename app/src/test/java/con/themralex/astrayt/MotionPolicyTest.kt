package con.themralex.astrayt

import con.themralex.astrayt.core.designsystem.MotionPolicy
import org.junit.Assert.*
import org.junit.Test

class MotionPolicyTest {
    @Test fun navbarDirectionFollowsTabPositionsIncludingSkippedTabs() {
        assertEquals(1,MotionPolicy.direction(0,1))
        assertEquals(1,MotionPolicy.direction(0,3))
        assertEquals(-1,MotionPolicy.direction(3,1))
        assertEquals(-1,MotionPolicy.direction(2,0))
        assertEquals(0,MotionPolicy.direction(2,2))
    }
    @Test fun playerAndNonTabRoutesDoNotAcquireLateralTabMotion() {
        assertEquals(0,MotionPolicy.direction(1,null))
        assertEquals(0,MotionPolicy.direction(null,2))
    }
    @Test fun batterySaverStopsAmbientEffectsWithoutFreezingActivityFeedback() {
        val policy=MotionPolicy.resolve(true,1f,true)
        assertTrue(policy.transitions);assertTrue(policy.progress);assertFalse(policy.ambient)
    }
    @Test fun disablingDecorativeMotionKeepsBusyIndicatorsAlive() {
        val policy=MotionPolicy.resolve(false,1f,false)
        assertFalse(policy.transitions);assertFalse(policy.ambient);assertTrue(policy.progress)
    }
    @Test fun systemReducedMotionTakesPrecedenceAndCanBeRestored() {
        val reduced=MotionPolicy.resolve(true,0f,false)
        assertFalse(reduced.transitions);assertFalse(reduced.ambient);assertFalse(reduced.progress)
        val restored=MotionPolicy.resolve(true,.5f,false)
        assertTrue(restored.transitions);assertTrue(restored.ambient);assertTrue(restored.progress)
    }
}
