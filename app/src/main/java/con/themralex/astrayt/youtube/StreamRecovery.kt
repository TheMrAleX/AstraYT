package con.themralex.astrayt.youtube

/** A playable SD result may still be an incomplete extraction. */
internal object StreamRecovery {
    fun needsAlternate(details:VideoDetails):Boolean =
        details.tracks.none{it.bestStreamForMp4!=null || it.bestStreamForWebm!=null} ||
            StreamPlanner.options(details,details.tracks.firstOrNull{it.type=="ORIGINAL"} ?: details.tracks.firstOrNull()).none{it.video?.videoOnly==true && it.height>=720}

    fun needsAudioDiscovery(details:VideoDetails,preferred:String):Boolean {
        val tracks=details.tracks.filter{it.type!="DESCRIPTIVE" && it.streams.isNotEmpty()}
        return tracks.map{it.locale}.filter{it.isNotBlank()}.distinct().size<2 ||
            tracks.none{it.locale.substringBefore('-').equals(preferred.substringBefore('-'),true)}
    }

    fun merge(primary:VideoDetails,alternate:VideoDetails):VideoDetails {
        require(primary.ytId==alternate.ytId)
        val tracks=primary.tracks.toMutableList()
        for(candidate in alternate.tracks){
            val sameId=tracks.indexOfFirst{it.id==candidate.id}
            val match=if(sameId>=0)sameId else tracks.indexOfFirst{old->
                val language=old.locale.substringBefore('-')
                val other=candidate.locale.substringBefore('-')
                old.type==candidate.type && ((language.isNotBlank() && old.locale.equals(candidate.locale,true)) ||
                    (old.type=="ORIGINAL" && ((language.isNotBlank() && language.equals(other,true)) || ((language.isBlank() || other.isBlank()) && primary.tracks.count{it.type=="ORIGINAL"}<=1 && alternate.tracks.count{it.type=="ORIGINAL"}==1))))
            }
            if(match<0)tracks.add(candidate)else {
                val old=tracks[match]
                tracks[match]=old.copy(locale=old.locale.ifBlank{candidate.locale},type=if(old.type=="UNKNOWN")candidate.type else old.type,displayName=old.displayName.ifBlank{candidate.displayName},streams=(candidate.streams+old.streams).distinctBy{it.id},default=old.default || candidate.default)
            }
        }
        return primary.copy(videos=(alternate.videos+primary.videos).distinctBy{Triple(it.id,it.height,it.videoOnly)},tracks=tracks,fetchedAt=alternate.fetchedAt)
    }
}
