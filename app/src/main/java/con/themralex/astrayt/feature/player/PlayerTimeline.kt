package con.themralex.astrayt.feature.player

/** Bounds for incomplete metadata, ended media and pointer-driven seeking. */
internal object PlayerTimeline {
    fun fraction(position:Long,duration:Long):Float=if(duration>0)(position.toDouble()/duration).coerceIn(0.0,1.0).toFloat()else 0f
    fun seek(fraction:Float,duration:Long):Long=if(duration>0 && fraction.isFinite())(fraction.coerceIn(0f,1f).toDouble()*duration).toLong().coerceIn(0,duration)else 0
    fun resume(position:Long,duration:Long):Long=if(duration>0 && position in 0 until duration && fraction(position,duration)<.95f)position else 0
}
