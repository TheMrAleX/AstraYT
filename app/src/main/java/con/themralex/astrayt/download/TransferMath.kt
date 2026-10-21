package con.themralex.astrayt.download
import kotlin.math.*
data class Chunk(val index: Int,val start: Long,val end: Long) { val length get()=end-start+1 }
object ChunkPlanner {
    const val CHUNK_SIZE=5L*1024*1024
    fun plan(length: Long,chunkSize: Long=CHUNK_SIZE):List<Chunk> {require(length>=0 && chunkSize>0);return (0 until ((length+chunkSize-1)/chunkSize).toInt()).map{Chunk(it,it*chunkSize,min(length,(it+1)*chunkSize)-1)} }
}
class TransferMeter {
    private val samples=ArrayDeque<Pair<Long,Long>>()
    private var eta: Double?=null
    fun sample(now: Long,bytes: Long,total: Long):Pair<Long,Long> {
        if(samples.lastOrNull()?.let{now<it.first || bytes<it.second}==true){samples.clear();eta=null}
        samples.addLast(now to bytes)
        while(samples.size>2 && now-samples.first().first>3000)samples.removeFirst()
        val first=samples.first();val speed=if(now>first.first)(bytes-first.second)*1000/(now-first.first) else 0
        if(speed>0){val estimate=max(0L,total-bytes).toDouble()/speed;eta=eta?.let{it*.8+estimate*.2} ?: estimate}
        return speed to (eta?.toLong() ?: -1)
    }
}
