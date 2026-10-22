package con.themralex.astrayt.feature.player

import java.util.Locale

internal object SubtitleFiles {
    fun mimeType(name:String):String? = when(name.substringAfterLast('.').lowercase(Locale.ROOT)) {
        "srt"->"application/x-subrip"
        "vtt"->"text/vtt"
        "ttml","xml"->"application/ttml+xml"
        "ass","ssa"->"text/x-ssa"
        else->null
    }
    fun fileName(name:String,id:Long):String {
        require(mimeType(name)!=null)
        val base=name.substringBeforeLast('.').replace(Regex("[^\\p{L}\\p{N}._ -]"),"_").take(100).ifBlank{"subtitles"}
        return "$id-$base.${name.substringAfterLast('.').lowercase(Locale.ROOT)}"
    }
}
