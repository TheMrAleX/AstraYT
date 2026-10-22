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
}
