package con.themralex.astrayt.youtube
import java.net.URI
import java.net.URLDecoder
sealed interface YoutubeLink { data class Video(val id: String):YoutubeLink;data class Playlist(val id: String):YoutubeLink;data class Channel(val url:String):YoutubeLink }
object UrlParser {
    private val id=Regex("[A-Za-z0-9_-]{11}")
    fun parse(input: String):YoutubeLink?=runCatching {
        val text=Regex("""https?://[^\s<>]+""",RegexOption.IGNORE_CASE).find(input.trim())?.value ?: input.trim()
        val uri=URI(if("://" in text)text else "https://$text")
        if(uri.scheme.lowercase() !in setOf("http","https") || uri.userInfo!=null)return null
        val host=uri.host?.lowercase() ?: return null
        val query=uri.rawQuery.orEmpty().split('&').mapNotNull { part->val pair=part.split('=',limit=2);if(pair.size==2)URLDecoder.decode(pair[0],"UTF-8") to URLDecoder.decode(pair[1],"UTF-8")else null }.toMap()
        val parts=uri.path.orEmpty().trim('/').split('/')
        val video=when(host){"youtu.be"->parts.firstOrNull();"youtube.com","www.youtube.com","m.youtube.com","music.youtube.com"->when(parts.firstOrNull()) {"watch"->query["v"];"shorts","live","embed"->parts.getOrNull(1);"channel","c","user"->return parts.getOrNull(1)?.takeIf{it.matches(Regex("[A-Za-z0-9_.-]+")) && it !in setOf(".","..")}?.let{YoutubeLink.Channel("https://www.youtube.com/${parts[0]}/$it")};"playlist"->return query["list"]?.takeIf{it.matches(Regex("[A-Za-z0-9_-]{2,}"))}?.let{YoutubeLink.Playlist(it)};else->if(parts.firstOrNull()?.startsWith("@")==true && parts[0].length>1)return YoutubeLink.Channel("https://www.youtube.com/${parts[0]}")else null};else->return null}
        video?.takeIf{id.matches(it)}?.let{YoutubeLink.Video(it)}
    }.getOrNull()
    fun canonical(id: String)="https://www.youtube.com/watch?v=$id"
}
