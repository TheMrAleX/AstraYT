package con.themralex.astrayt.data.db
import androidx.room.*
import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow

@Entity(tableName="videos",indices=[Index(value=["ytId"],unique=true),Index("channelId"),Index("addedAtMs")])
data class VideoEntity(@PrimaryKey(autoGenerate=true)val id: Long=0,val ytId: String,val title: String,val channelId: String,val channelName: String,val channelAvatarPath: String="",val thumbnailPath: String="",val durationMs: Long,val viewCount: Long=0,val uploadDateMs: Long=0,val description: String="",val contentUri: String,val fileSizeBytes: Long,val mimeType: String,val width: Int=0,val height: Int=0,val kind: String,val audioLangTag: String="",val audioLabel: String="",val addedAtMs: Long=System.currentTimeMillis(),val lastPlayedAtMs: Long=0,val positionMs: Long=0,val dominantColor: Int=0,@ColumnInfo(defaultValue="-1") val likeCount:Long=-1,@ColumnInfo(defaultValue="0") val removedDurationMs:Long=0)
@Fts4(contentEntity=VideoEntity::class) @Entity(tableName="video_fts") data class VideoFts(val title: String,val channelName: String)
@Entity(tableName="downloads",indices=[Index("state"),Index("createdAtMs")])
data class DownloadEntity(@PrimaryKey(autoGenerate=true)val id: Long=0,val ytId: String,val url: String,val title: String,val channelName: String,val thumbnailPath: String,val kind: String,val optionsJson: String,val state: String="QUEUED",val bytesDone: Long=0,val bytesTotal: Long=0,val errorCode: String?=null,val priority: Int=0,val createdAtMs: Long=System.currentTimeMillis(),val updatedAtMs: Long=System.currentTimeMillis())
@Entity(tableName="channel_audio_pref") data class ChannelAudioPref(@PrimaryKey val channelId: String,val languageTag: String)
@Dao interface AstraDao {
    @Query("SELECT * FROM videos ORDER BY addedAtMs DESC") fun videos():Flow<List<VideoEntity>>
    @Query("SELECT * FROM videos ORDER BY addedAtMs DESC") fun paging():PagingSource<Int,VideoEntity>
    @Query("SELECT videos.* FROM videos JOIN video_fts ON videos.id=video_fts.rowid WHERE video_fts MATCH :query ORDER BY addedAtMs DESC") fun search(query: String):Flow<List<VideoEntity>>
    @RawQuery(observedEntities=[VideoEntity::class,VideoFts::class]) fun pagingQuery(query:androidx.sqlite.db.SupportSQLiteQuery):PagingSource<Int,VideoEntity>
    @Query("SELECT * FROM videos WHERE ytId=:ytId") suspend fun byYoutubeId(ytId:String):VideoEntity?
    @Query("SELECT * FROM videos WHERE id=:id") suspend fun video(id: Long):VideoEntity?
    @Query("SELECT * FROM videos") suspend fun allVideos():List<VideoEntity>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun insertVideo(video: VideoEntity):Long
    @Query("DELETE FROM videos WHERE id=:id") suspend fun deleteVideo(id: Long)
    @Query("UPDATE videos SET positionMs=:position,lastPlayedAtMs=:now WHERE id=:id") suspend fun position(id: Long,position: Long,now: Long=System.currentTimeMillis())
    @Query("SELECT * FROM downloads ORDER BY priority DESC,createdAtMs ASC") fun downloads():Flow<List<DownloadEntity>>
    @Query("SELECT * FROM downloads ORDER BY priority DESC,createdAtMs ASC") suspend fun allDownloads():List<DownloadEntity>
    @Query("SELECT * FROM downloads WHERE id=:id") suspend fun download(id: Long):DownloadEntity?
    @Insert suspend fun enqueue(download: DownloadEntity):Long
    @Query("UPDATE downloads SET state=:state,errorCode=:error,updatedAtMs=:now WHERE id=:id") suspend fun state(id: Long,state: String,error: String?=null,now: Long=System.currentTimeMillis())
    @Query("UPDATE downloads SET state='FETCHING',errorCode=NULL WHERE id=:id AND state='QUEUED'") suspend fun claimQueued(id:Long):Int
    @Query("UPDATE downloads SET state=:state,errorCode=NULL WHERE id=:id AND state NOT IN ('DONE','CANCELED')") suspend fun stop(id:Long,state:String)
    @Query("UPDATE downloads SET state=:state,errorCode=NULL WHERE id=:id AND state NOT IN ('PAUSED','CANCELED','DONE','FAILED')") suspend fun runningState(id:Long,state:String)
    @Query("UPDATE downloads SET state='QUEUED',errorCode=NULL WHERE id=:id AND state IN ('PAUSED','FAILED')") suspend fun queueIfResumable(id:Long)
    @Query("UPDATE downloads SET optionsJson=:options WHERE id=:id") suspend fun options(id:Long,options:String)
    @Query("UPDATE downloads SET state='FAILED',errorCode=:error,updatedAtMs=:now WHERE id=:id AND state NOT IN ('CANCELED','PAUSED','DONE')") suspend fun failUnlessStopped(id:Long,error:String,now:Long=System.currentTimeMillis())
    @Query("UPDATE downloads SET title=:title,channelName=:channel,thumbnailPath=:thumbnail WHERE id=:id") suspend fun metadata(id:Long,title:String,channel:String,thumbnail:String)
    @Query("UPDATE downloads SET bytesDone=:done,bytesTotal=:total,updatedAtMs=:now WHERE id=:id") suspend fun progress(id: Long,done: Long,total: Long,now: Long=System.currentTimeMillis())
    @Query("UPDATE downloads SET priority=(SELECT COALESCE(MAX(priority),0)+1 FROM downloads) WHERE id=:id") suspend fun prioritize(id: Long)
    @Query("DELETE FROM downloads WHERE state IN ('DONE','CANCELED')") suspend fun clearFinished()
    @Query("UPDATE downloads SET state='PAUSED' WHERE state IN ('QUEUED','FETCHING','DOWNLOADING','MUXING','WAITING_NETWORK')") suspend fun pauseInterrupted()
    @Query("SELECT * FROM channel_audio_pref") fun channels():Flow<List<ChannelAudioPref>>
    @Query("SELECT languageTag FROM channel_audio_pref WHERE channelId=:id") suspend fun channelLanguage(id: String):String?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun remember(pref: ChannelAudioPref)
    @Query("DELETE FROM channel_audio_pref WHERE channelId=:id") suspend fun forget(id: String)
}
@Database(entities=[VideoEntity::class,VideoFts::class,DownloadEntity::class,ChannelAudioPref::class],version=2,exportSchema=true)
abstract class AstraDatabase:RoomDatabase(){abstract fun dao():AstraDao}
