package con.themralex.astrayt
import con.themralex.astrayt.feature.player.DescriptionFormatter
import org.junit.Assert.*
import org.junit.Test
class PlayerDescriptionTest {
    @Test fun breaksParagraphsAndLinksAreReadable() {
        val result=DescriptionFormatter.format("<p>Primera<br>Segunda &amp; tercera</p><p><a href=\"https://example.test/video?a=1&amp;b=2\">Ver vídeo</a></p>")
        assertEquals("Primera\nSegunda & tercera\nVer vídeo",result.text)
        val link=result.links.single()
        assertEquals("Ver vídeo",result.text.substring(link.start,link.end))
        assertEquals("https://example.test/video?a=1&b=2",link.url)
    }
    @Test fun plainTextLineBreaksAndBlankLinesSurvive() {
        assertEquals("Uno\n\nDos\nTres",DescriptionFormatter.format("Uno\n\nDos<br>Tres").text)
    }
    @Test fun maliciousAndUnsupportedLinksAreOnlyText() {
        val result=DescriptionFormatter.format("<a href='javascript:alert(1)'>Uno</a> <a href='file:///secret'>Dos</a><script>bad()</script><style>body{}</style>")
        assertEquals("Uno Dos",result.text)
        assertTrue(result.links.isEmpty())
    }
    @Test fun listsAndRelativeYoutubeLinksRetainTextAndCorrectOffsets() {
        val result=DescriptionFormatter.format("<ul><li>A</li><li><a href='/watch?v=abcdefghijk'>B</a></li></ul>")
        assertEquals("• A\n• B",result.text)
        val link=result.links.single()
        assertEquals("B",result.text.substring(link.start,link.end))
        assertEquals("https://www.youtube.com/watch?v=abcdefghijk",link.url)
    }
}
