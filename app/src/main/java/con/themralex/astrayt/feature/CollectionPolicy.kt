package con.themralex.astrayt.feature

import con.themralex.astrayt.data.db.*
import con.themralex.astrayt.download.Progress

/** Stable ordering is shared by library playback and collection details. */
object CollectionPolicy {
    fun videos(id:Long,memberships:List<PlaylistMembership>,all:List<VideoEntity>):List<VideoEntity> {
        return groupedVideos(memberships,all)[id].orEmpty()
    }
    fun groupedVideos(memberships:List<PlaylistMembership>,all:List<VideoEntity>):Map<Long,List<VideoEntity>> {
        val byId=all.associateBy{it.ytId}
        return memberships.groupBy{it.playlistId}.mapValues{(_,entries)->entries.sortedBy{it.position}.mapNotNull{byId[it.ytId]}}
    }
    fun taskCount(rows:List<DownloadEntity>):Int=rows.map{(it.playlistId!=null) to (it.playlistId ?: it.id)}.distinct().size
    fun state(rows:List<DownloadEntity>):String=when {
        rows.isEmpty()->"QUEUED"
        rows.all{it.state=="CANCELED"}->"CANCELED"
        rows.all{it.state=="DONE"}->"DONE"
        rows.any{it.state in setOf("DOWNLOADING","FETCHING","MUXING","SUBTITLES")}->"DOWNLOADING"
        rows.any{it.state=="QUEUED"}->"QUEUED"
        rows.any{it.state=="WAITING_NETWORK"}->"WAITING_NETWORK"
        rows.any{it.state=="FAILED"}->"FAILED"
        rows.any{it.state=="PAUSED"}->"PAUSED"
        else->"CANCELED"
    }
    fun fraction(rows:List<DownloadEntity>,progress:Map<Long,Progress>):Float=if(rows.isEmpty())0f else rows.sumOf{row->
        (if(row.state=="DONE")1f else progress[row.id]?.fraction ?: if(row.bytesTotal>0)row.bytesDone.toFloat()/row.bytesTotal else 0f).coerceIn(0f,1f).toDouble()
    }.toFloat()/rows.size
}
