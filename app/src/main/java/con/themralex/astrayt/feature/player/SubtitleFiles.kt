package con.themralex.astrayt.feature.player

import java.util.Locale
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

internal object SubtitleFiles {
    fun mimeType(name:String):String? = when(name.substringAfterLast('.').lowercase(Locale.ROOT)) {
        "srt"->"application/x-subrip"
        "vtt"->"text/vtt"
        "ttml","xml"->"application/ttml+xml"
        "ass","ssa"->"text/x-ssa"
        else->null
    }
    // Android document providers return SRTs in UTF-16 and Windows-1252 too.
    // Store UTF-8 consistently so Media3 can parse them offline.
    fun utf8(bytes:ByteArray):String {
        val text=when {
            bytes.size>=2 && bytes[0]==0xff.toByte() && bytes[1]==0xfe.toByte()->String(bytes,Charsets.UTF_16LE)
            bytes.size>=2 && bytes[0]==0xfe.toByte() && bytes[1]==0xff.toByte()->String(bytes,Charsets.UTF_16BE)
            else->try{Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()}
                catch(_:java.nio.charset.CharacterCodingException){String(bytes,charset("windows-1252"))}
        }
        return text.removePrefix("\uFEFF")
    }
    fun fileName(name:String,id:Long):String {
        require(mimeType(name)!=null)
        val base=name.substringBeforeLast('.').replace(Regex("[^\\p{L}\\p{N}._ -]"),"_").take(100).ifBlank{"subtitles"}
        return "$id-$base.${name.substringAfterLast('.').lowercase(Locale.ROOT)}"
    }
}
