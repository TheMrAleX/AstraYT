package con.themralex.astrayt
import con.themralex.astrayt.feature.player.SubtitleFiles
import org.junit.Assert.*
import org.junit.Test
class SubtitleFilesTest {
    @Test fun supportedFormatsHavePlayerMimeTypes() {
        assertEquals("application/x-subrip",SubtitleFiles.mimeType("Español.SRT"))
        assertEquals("text/vtt",SubtitleFiles.mimeType("captions.vtt"))
        assertEquals("text/x-ssa",SubtitleFiles.mimeType("dialogue.ass"))
        assertNull(SubtitleFiles.mimeType("download.html"))
    }
    @Test fun longOrPathLikeNamesKeepTheirExtensionAndStayInOneDirectory() {
        val name=SubtitleFiles.fileName("../../"+"á".repeat(250)+".SRT",42)
        assertFalse(name.contains('/'));assertFalse(name.contains('\\'))
        assertTrue(name.endsWith(".srt"));assertEquals("application/x-subrip",SubtitleFiles.mimeType(name))
        assertTrue(name.length<120)
    }
    @Test fun utf8ImportPreservesAccentsAndRemovesBom() {
        assertEquals("Español",SubtitleFiles.utf8("\uFEFFEspañol".toByteArray()))
        assertEquals("日本語",SubtitleFiles.utf8("日本語".toByteArray()))
    }
    @Test fun utf16ImportsKeepCueTimesAndText() {
        val cue="1\r\n00:00:01,000 --> 00:00:02,000\r\n¡Hola!\r\n"
        assertEquals(cue,SubtitleFiles.utf8(byteArrayOf(0xff.toByte(),0xfe.toByte())+cue.toByteArray(Charsets.UTF_16LE)))
        assertEquals(cue,SubtitleFiles.utf8(byteArrayOf(0xfe.toByte(),0xff.toByte())+cue.toByteArray(Charsets.UTF_16BE)))
    }
    @Test fun windows1252ImportPreservesSpanishText() {
        val cue="¿Qué tal?"
        assertEquals(cue,SubtitleFiles.utf8(cue.toByteArray(charset("windows-1252"))))
    }
}
