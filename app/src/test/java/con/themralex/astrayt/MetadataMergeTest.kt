package con.themralex.astrayt
import con.themralex.astrayt.data.db.VideoEntity
import con.themralex.astrayt.download.MetadataMerge
import con.themralex.astrayt.youtube.VideoDetails
import org.junit.Assert.*
import org.junit.Test
class MetadataMergeTest {
    private val old=VideoEntity(id=4,ytId="F0OkwXKcPSE",title="Guardado",channelId="",channelName="",durationMs=70000,contentUri="content://media/42",fileSizeBytes=10000,mimeType="video/mp4",height=1080,kind="VIDEO",audioLangTag="es",positionMs=30000,removedDurationMs=10000)
    private val remote=VideoDetails(old.ytId,"Nuevo título","canal","Canal","avatar","thumb",80000,1234,1000,"Descripción",emptyList(),emptyList(),likeCount=56)
    @Test fun repairsMissingMetadataWithoutChangingMediaPlaybackOrCutDuration(){
        val filled=MetadataMerge.fill(old,remote)
        assertEquals("Descripción",filled.description);assertEquals(56L,filled.likeCount);assertEquals("canal",filled.channelId)
        assertEquals(old,filled.copy(channelId="",channelName="",description="",viewCount=0,uploadDateMs=0,likeCount=-1))
    }
    @Test fun preservesExistingTextAndARealZeroLikeCount(){
        val existing=old.copy(description="Existente",channelName="Guardado",likeCount=0,viewCount=4,uploadDateMs=5)
        val filled=MetadataMerge.fill(existing,remote)
        assertEquals("Guardado",filled.title);assertEquals("Existente",filled.description);assertEquals(0L,filled.likeCount);assertEquals(4L,filled.viewCount);assertEquals(5L,filled.uploadDateMs)
    }
}
