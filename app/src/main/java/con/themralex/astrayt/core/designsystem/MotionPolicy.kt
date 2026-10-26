package con.themralex.astrayt.core.designsystem

internal data class MotionOptions(val transitions:Boolean,val ambient:Boolean,val progress:Boolean)
internal object MotionPolicy {
    fun resolve(preference:Boolean,systemScale:Float,powerSave:Boolean):MotionOptions {
        val system=systemScale.isFinite() && systemScale>0f
        return MotionOptions(preference && system,preference && system && !powerSave,system)
    }
    // Increasing indices are physically to the right in both navigation strips.
    fun direction(from:Int?,to:Int?):Int = if(from==null || to==null)0 else to.compareTo(from)
}
