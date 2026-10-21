package con.themralex.astrayt.youtube

import org.schabi.newpipe.extractor.stream.VideoStream

/** NewPipe can omit numeric height while retaining its resolution label. */
internal object ExtractorStreams {
    fun video(stream:VideoStream):MediaStream {
        val height=stream.height.takeIf{it>0} ?: Regex("(\\d+)p").find(stream.resolution.orEmpty())?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val fps=stream.fps.takeIf{it>0} ?: Regex("p(\\d+)").find(stream.resolution.orEmpty())?.groupValues?.get(1)?.toIntOrNull() ?: 30
        return MediaStream(stream.id,stream.content,stream.format?.suffix.orEmpty(),stream.codec.orEmpty(),stream.bitrate,height,fps,videoOnly=stream.isVideoOnly,hdr=stream.quality.orEmpty().contains("HDR",true))
    }
}
