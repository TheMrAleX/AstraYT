package con.themralex.astrayt.download

/** Bounded interleaving avoids switching input files for every AAC/video sample. */
internal object MuxSchedule {
    const val WINDOW_US=250_000L
    fun batchEnd(firstUs:Long,otherUs:Long?):Long = if(otherUs==null)Long.MAX_VALUE else minOf(firstUs+WINDOW_US,otherUs+WINDOW_US)
}
