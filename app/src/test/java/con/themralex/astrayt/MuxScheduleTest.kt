package con.themralex.astrayt
import con.themralex.astrayt.download.MuxSchedule
import org.junit.Assert.*
import org.junit.Test
class MuxScheduleTest {
    @Test fun oneInputCanBeReadInFileOrderUntilItsEnd(){assertEquals(Long.MAX_VALUE,MuxSchedule.batchEnd(1000,null))}
    @Test fun batchingIsBoundedToAQuarterSecondOfTheOtherInput(){assertEquals(251000L,MuxSchedule.batchEnd(1000,2000));assertEquals(250100L,MuxSchedule.batchEnd(1000,100))}
    @Test fun batchingPreservesEveryTrackSampleAndGreatlyReducesInputSwitches(){
        val times=listOf((0..1000).map{it*20000L},(0..600).map{it*33333L});val positions=IntArray(2);val emitted=listOf(mutableListOf<Long>(),mutableListOf<Long>());var batches=0
        while(positions.indices.any{positions[it]<times[it].size}){
            val active=positions.indices.filter{positions[it]<times[it].size};val first=active.minBy{times[it][positions[it]]};val other=active.firstOrNull{it!=first}?.let{times[it][positions[it]]}
            val limit=MuxSchedule.batchEnd(times[first][positions[first]],other);batches++
            do{emitted[first].add(times[first][positions[first]++])}while(positions[first]<times[first].size && times[first][positions[first]]<limit)
        }
        assertEquals(times,emitted);assertTrue("No per-sample source switching",batches<200)
    }
}
