package con.themralex.astrayt.youtube
import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable @Immutable data class MediaStream(val id: String,val url: String,val format: String,val codec: String="",val bitrate: Int=0,val height: Int=0,val fps: Int=0,val bytes: Long=0,val videoOnly: Boolean=false,val hdr: Boolean=false,val headers:Map<String,String> = emptyMap())
@Serializable @Immutable data class AudioTrackOption(val id: String,val locale: String,val type: String,val displayName: String,val streams: List<MediaStream>,val default: Boolean=false) {
    val bestStreamForMp4 get()=streams.filter{it.format.equals("m4a",true) || it.format.equals("mp4",true) || it.codec.contains("mp4a",true) || it.codec.contains("aac",true)}.maxByOrNull{it.bitrate}
    val bestStreamForWebm get()=streams.filter{it.format=="webm" || it.codec.contains("opus",true)}.maxByOrNull{it.bitrate}
}
@Serializable @Immutable data class VideoDetails(val ytId: String,val title: String,val channelId: String,val channelName: String,val channelAvatar: String,val thumbnail: String,val durationMs: Long,val viewCount: Long,val uploadDateMs: Long,val description: String,val videos: List<MediaStream>,val tracks: List<AudioTrackOption>,val fetchedAt: Long=System.currentTimeMillis(),val likeCount:Long=-1,val audioDiscoveryIncomplete:Boolean=false)
@Immutable data class QualityOption(val label: String,val video: MediaStream?,val audio: MediaStream?,val container: String,val height: Int,val estimatedBytes: Long,val fps: Int=0,val hdr: Boolean=false,val headers:Map<String,String> = emptyMap(),val extractAudio:Boolean=false)
@Immutable data class PlaylistEntry(val ytId: String,val title: String,val channel: String,val thumbnail: String,val durationMs: Long)
sealed interface ResolvedLink { data class Video(val details: VideoDetails):ResolvedLink;data class Playlist(val url: String,val title: String,val thumbnail: String,val entries: List<PlaylistEntry>,val hasMore:Boolean=false):ResolvedLink }
interface YoutubeBackend { suspend fun resolve(url: String):ResolvedLink;suspend fun details(videoId: String):VideoDetails;suspend fun refreshStreams(videoId: String):VideoDetails;suspend fun morePlaylist(url:String):ResolvedLink.Playlist }
