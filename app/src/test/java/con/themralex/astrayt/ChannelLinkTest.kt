package con.themralex.astrayt

import con.themralex.astrayt.youtube.*
import org.junit.Assert.*
import org.junit.Test

class ChannelLinkTest {
    @Test fun recognizesHandlesAndLegacyChannelLinks() {
        val cases=mapOf(
            "https://www.youtube.com/@TheMrAleX/videos" to "https://www.youtube.com/@TheMrAleX",
            "https://m.youtube.com/channel/UC123/videos" to "https://www.youtube.com/channel/UC123",
            "https://youtube.com/c/Example?si=ignored" to "https://www.youtube.com/c/Example",
            "https://youtube.com/user/Example" to "https://www.youtube.com/user/Example",
            "Mira este canal https://youtube.com/@Example" to "https://www.youtube.com/@Example"
        )
        cases.forEach{(input,url)->assertEquals(input,YoutubeLink.Channel(url),UrlParser.parse(input))}
    }
    @Test fun channelSupportKeepsVideoAndPlaylistRouting() {
        assertEquals(YoutubeLink.Playlist("PL123"),UrlParser.parse("https://youtube.com/playlist?list=PL123"))
        assertEquals(YoutubeLink.Video("dQw4w9WgXcQ"),UrlParser.parse("https://youtube.com/watch?v=dQw4w9WgXcQ&list=PL123"))
        assertEquals(YoutubeLink.Video("dQw4w9WgXcQ"),UrlParser.parse("https://youtu.be/dQw4w9WgXcQ?t=3"))
    }
    @Test fun rejectsImpersonatorsAndMalformedChannels() {
        listOf("https://youtube.com.attacker.test/@channel","https://evil@youtube.com/@channel","https://youtube.com/channel/","https://youtube.com/@","javascript://youtube.com/@Example","https://youtube.com/c/../watch").forEach{assertNull(it,UrlParser.parse(it))}
    }
}
