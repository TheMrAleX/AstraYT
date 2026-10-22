package con.themralex.astrayt.youtube

/** A progressive 360p result is playable but can still be an incomplete extraction. */
internal object StreamRecovery {
    fun needsAlternate(details:VideoDetails):Boolean =
        details.tracks.none{it.bestStreamForMp4!=null || it.bestStreamForWebm!=null} ||
            StreamPlanner.options(details,details.tracks.firstOrNull{it.type=="ORIGINAL"} ?: details.tracks.firstOrNull()).none{it.video?.videoOnly==true && it.height>=720}

    fun merge(primary:VideoDetails,alternate:VideoDetails):VideoDetails {
        require(primary.ytId==alternate.ytId)
        val tracks=(alternate.tracks+primary.tracks).groupBy{it.id}.map{(_,versions)->
            versions.first().copy(streams=versions.flatMap{it.streams}.distinctBy{it.id})
        }
        return primary.copy(videos=(alternate.videos+primary.videos).distinctBy{Triple(it.id,it.height,it.videoOnly)},tracks=tracks,fetchedAt=alternate.fetchedAt)
    }
}
