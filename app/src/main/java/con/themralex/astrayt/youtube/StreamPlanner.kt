package con.themralex.astrayt.youtube
object StreamPlanner {
    val commonHeights=listOf(144,240,360,480,720,1080,1440,2160)
    fun qualityLabel(height:Int)=when(height){1440->"2K · 1440p";2160->"4K · 2160p";else->"${height}p"}
    fun options(details: VideoDetails,track: AudioTrackOption?,audioOnly: Boolean=false):List<QualityOption> {
        fun bytes(stream: MediaStream?)=stream?.let { if(it.bytes>0)it.bytes else if(it.bitrate>0)it.bitrate.toLong()*details.durationMs/8_000 else 0 } ?: 0
        if(audioOnly) {
            val mp4=track?.bestStreamForMp4
            val audio=mp4 ?: track?.bestStreamForWebm ?: return emptyList()
            val container=if(audio==mp4)"m4a"else"webm"
            return listOf(QualityOption(if(container=="m4a")"M4A"else"WebM Opus",null,audio,container,0,bytes(audio)))
        }
        val videoOnly=details.videos.filter{it.videoOnly}
        val sources=videoOnly.ifEmpty{details.videos.filter{!it.videoOnly}}
        val planned=sources.filter{it.height>0}.groupBy{it.height}.mapNotNull { (height,streams)->
            val h264=streams.filter{(!it.videoOnly || track?.bestStreamForMp4!=null) && it.format=="mp4" && (it.codec.startsWith("avc",true) || it.codec.contains("h264",true) || (it.codec.isBlank() && it.id in setOf("18","22","133","134","135","136","137","160","212","264","298","299")))}.maxByOrNull{it.fps}
            val stream=if(height<=1080 && h264!=null)h264 else streams.filter{(!it.videoOnly || track?.bestStreamForWebm!=null) && it.format=="webm" && (it.codec.contains("vp9",true)||it.codec.startsWith("vp09",true)||(it.codec.isBlank() && it.id in setOf("242","243","244","247","248","271","272","278","302","303","308","313","315")))}.maxByOrNull{it.fps} ?: return@mapNotNull null
            val container=if(stream.format=="mp4")"mp4" else "webm"
            val audio=if(stream.videoOnly)if(container=="mp4")track?.bestStreamForMp4 else track?.bestStreamForWebm else null
            if(stream.videoOnly && audio==null)return@mapNotNull null
            val size=bytes(stream)+bytes(audio)
            QualityOption("${height}p"+(if(stream.fps>=50)" ${stream.fps}" else "")+(if(container=="webm")" WebM"else ""),stream,audio,container,height,size,stream.fps,stream.hdr)
        }.sortedBy{it.height}
        if(planned.isEmpty() && videoOnly.isNotEmpty() && details.videos.any{!it.videoOnly})return options(details.copy(videos=details.videos.filter{!it.videoOnly}),track,audioOnly)
        return planned
    }
    fun default(options: List<QualityOption>,maximum: Int)=options.filter{it.height<=maximum}.maxByOrNull{it.height} ?: options.minByOrNull{it.height}
}
