package con.themralex.astrayt.youtube
object StreamPlanner {
    fun options(details: VideoDetails,track: AudioTrackOption?,audioOnly: Boolean=false):List<QualityOption> {
        fun bytes(stream: MediaStream?)=stream?.let { if(it.bytes>0)it.bytes else if(it.bitrate>0)it.bitrate.toLong()*details.durationMs/8_000 else 0 } ?: 0
        if(audioOnly) return listOfNotNull(track?.bestStreamForMp4?.let{QualityOption("M4A",null,it,"m4a",0,bytes(it))})
        val videoOnly=details.videos.filter{it.videoOnly}
        val sources=videoOnly.ifEmpty{details.videos.filter{!it.videoOnly}}
        return sources.groupBy{it.height}.mapNotNull { (height,streams)->
            val h264=streams.filter{it.format=="mp4" && (it.codec.startsWith("avc") || it.codec.contains("h264",true))}.maxByOrNull{it.fps}
            val stream=if(height<=1080 && h264!=null)h264 else streams.filter{it.format=="webm" && (it.codec.contains("vp9",true)||it.codec.startsWith("vp09"))}.maxByOrNull{it.fps} ?: return@mapNotNull null
            val container=if(stream.format=="mp4")"mp4" else "webm"
            val audio=if(stream.videoOnly)if(container=="mp4")track?.bestStreamForMp4 else track?.bestStreamForWebm else null
            if(stream.videoOnly && audio==null)return@mapNotNull null
            val size=bytes(stream)+bytes(audio)
            QualityOption("${height}p"+(if(stream.fps>=50)" ${stream.fps}" else "")+(if(container=="webm")" WebM"else ""),stream,audio,container,height,size,stream.fps,stream.hdr)
        }.sortedBy{it.height}
    }
    fun default(options: List<QualityOption>,maximum: Int)=options.filter{it.height<=maximum}.maxByOrNull{it.height} ?: options.minByOrNull{it.height}
}
