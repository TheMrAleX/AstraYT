package con.themralex.astrayt.feature

import con.themralex.astrayt.youtube.*

object CapturePolicy {
    // Metadata, stream discovery and their errors never block a valid single-video request.
    fun canEnqueue(state:CaptureState):Boolean = !state.busy &&
        if(state.playlist!=null)state.playlist.entries.any{it.ytId in state.selected}
        else UrlParser.parse(state.url) is YoutubeLink.Video
}
