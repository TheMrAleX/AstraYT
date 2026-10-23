package con.themralex.astrayt.download

import con.themralex.astrayt.data.db.VideoEntity
import con.themralex.astrayt.youtube.VideoDetails

internal object MetadataMerge {
    fun fill(old:VideoEntity,details:VideoDetails)=old.copy(
        title=old.title.takeUnless{it.isBlank() || it==old.ytId} ?: details.title,
        channelId=old.channelId.ifBlank{details.channelId},channelName=old.channelName.ifBlank{details.channelName},
        description=old.description.ifBlank{details.description},viewCount=old.viewCount.takeIf{it>0} ?: details.viewCount.coerceAtLeast(0),
        uploadDateMs=old.uploadDateMs.takeIf{it>0} ?: details.uploadDateMs,likeCount=old.likeCount.takeIf{it>=0} ?: details.likeCount)
}
