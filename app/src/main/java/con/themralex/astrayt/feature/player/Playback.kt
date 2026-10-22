package con.themralex.astrayt.feature.player
import android.content.*
import android.net.Uri
import android.provider.OpenableColumns
import con.themralex.astrayt.R
import java.util.Locale
import androidx.core.content.ContextCompat
import androidx.media3.common.*
import androidx.media3.session.*
import con.themralex.astrayt.data.db.*
import con.themralex.astrayt.data.prefs.Preferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.*
import java.io.File
import javax.inject.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class PlayerTrack(val group:TrackGroup,val index:Int,val displayName:String,val selected:Boolean,val type:Int)
data class PlaybackState(val video:VideoEntity?=null,val playing:Boolean=false,val buffering:Boolean=false,val position:Long=0,val duration:Long=0,val buffered:Long=0,val speed:Float=1f,val audioOnly:Boolean=false,val audioTracks:List<PlayerTrack> = emptyList(),val subtitleTracks:List<PlayerTrack> = emptyList())
@Singleton class Playback @Inject constructor(@ApplicationContext private val context:Context,private val dao:AstraDao,private val preferences:Preferences) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate+CoroutineExceptionHandler{_,_->})
    private val mutex=Mutex()
    private val _state=MutableStateFlow(PlaybackState())
    val state=_state.asStateFlow()
    var controller:MediaController?=null;private set
    private var playlist=emptyList<VideoEntity>()
    suspend fun connect():MediaController=mutex.withLock {
        controller?.let{return it}
        val token=SessionToken(context,ComponentName(context,PlaybackService::class.java));val future=MediaController.Builder(context,token).buildAsync()
        val media=suspendCancellableCoroutine<MediaController>{c->future.addListener({try{if(c.isActive)c.resume(future.get())}catch(e:Exception){if(c.isActive)c.resumeWithException(e)}},ContextCompat.getMainExecutor(context));c.invokeOnCancellation{future.cancel(true)}}
        controller=media
        media.addListener(object:Player.Listener {
            override fun onEvents(player:Player,events:Player.Events){val video=playlist.firstOrNull{it.id.toString()==player.currentMediaItem?.mediaId};_state.value=_state.value.copy(video=video,playing=player.isPlaying,buffering=player.playbackState==Player.STATE_BUFFERING,position=player.currentPosition.coerceAtLeast(0),duration=player.duration.coerceAtLeast(0),buffered=player.bufferedPosition.coerceAtLeast(0),speed=player.playbackParameters.speed,audioTracks=tracks(player,C.TRACK_TYPE_AUDIO),subtitleTracks=tracks(player,C.TRACK_TYPE_TEXT))}
            override fun onPlaybackStateChanged(playbackState:Int){if(playbackState==Player.STATE_ENDED)scope.launch{save()}}
            override fun onIsPlayingChanged(isPlaying:Boolean){if(!isPlaying)scope.launch{save()}}
        })
        scope.launch {var tick=0;while(isActive){delay(500);_state.value=_state.value.copy(position=media.currentPosition.coerceAtLeast(0),duration=media.duration.coerceAtLeast(0),buffered=media.bufferedPosition.coerceAtLeast(0));if(++tick%10==0)save()}}
        media
    }
    private fun tracks(player:Player,type:Int):List<PlayerTrack> = player.currentTracks.groups.filter{it.type==type}.flatMap { group->
        (0 until group.length).filter{group.isTrackSupported(it)}.map { index->
            val format=group.getTrackFormat(index)
            val language=format.language?.takeUnless{it.isBlank() || it=="und"}?.let{Locale.forLanguageTag(it).displayLanguage}
            val video=playlist.firstOrNull{it.id.toString()==player.currentMediaItem?.mediaId}
            val savedAudio=if(type==C.TRACK_TYPE_AUDIO)video?.audioLabel?.takeIf{it.isNotBlank()} ?: video?.audioLangTag?.takeIf{it.isNotBlank()}?.let{Locale.forLanguageTag(it).displayLanguage}else null
            val label=format.label?.takeIf{it.isNotBlank()} ?: language ?: savedAudio ?: context.getString(R.string.player_track,index+1)
            PlayerTrack(group.mediaTrackGroup,index,label,group.isTrackSelected(index),type)
        }
    }
    fun selectTrack(track:PlayerTrack) {
        val media=controller ?: return
        if(media.currentTracks.groups.none{it.mediaTrackGroup==track.group})return
        media.trackSelectionParameters=media.trackSelectionParameters.buildUpon().setTrackTypeDisabled(track.type,false).setOverrideForType(TrackSelectionOverride(track.group,track.index)).build()
    }
    fun disableSubtitles(){controller?.let{it.trackSelectionParameters=it.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true).build()}}
    suspend fun addSubtitle(uri:Uri) {
        val media=controller ?: return
        val video=state.value.video ?: return
        val name=withContext(Dispatchers.IO){context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0)else null}} ?: uri.lastPathSegment.orEmpty()
        val mime=SubtitleFiles.mimeType(name) ?: throw java.io.IOException("SUBTITLE_FORMAT")
        val copied=withContext(Dispatchers.IO) {
            val dir=File(context.filesDir,"subtitles/${video.ytId}").apply{mkdirs()}
            val file=File(dir,SubtitleFiles.fileName(name,System.nanoTime()))
            try {context.contentResolver.openInputStream(uri)?.use{input->file.outputStream().use{output->val buffer=ByteArray(16384);var count=0L;while(true){currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0)break;count+=n;if(count>10*1024*1024)throw java.io.IOException("SUBTITLE_SIZE");output.write(buffer,0,n)}}} ?: throw java.io.IOException("STORAGE");file}
            catch(e:Exception){file.delete();throw e}
        }
        // The picker may return after the queue has advanced to another video.
        if(media.currentMediaItem?.mediaId!=video.id.toString()){copied.delete();return}
        val position=media.currentPosition;val playing=media.playWhenReady
        val current=media.currentMediaItem ?: return
        val config=MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(copied)).setMimeType(mime).setLabel(name.take(120)).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
        val item=current.buildUpon().setSubtitleConfigurations(current.localConfiguration?.subtitleConfigurations.orEmpty()+config).build()
        media.replaceMediaItem(media.currentMediaItemIndex,item)
        media.seekTo(position);media.prepare();media.playWhenReady=playing
        media.trackSelectionParameters=media.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT,false).setSelectUndeterminedTextLanguage(true).build()
    }
    private suspend fun savedSubtitles(video:VideoEntity):List<MediaItem.SubtitleConfiguration> = withContext(Dispatchers.IO) {
        File(context.filesDir,"subtitles/${video.ytId}").listFiles()?.sortedBy{it.name}?.mapNotNull { file->
            val mime=SubtitleFiles.mimeType(file.name) ?: return@mapNotNull null
            MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file)).setMimeType(mime).setLabel(file.name.substringAfter('-')).build()
        }.orEmpty()
    }
    suspend fun play(video:VideoEntity,all:List<VideoEntity> = listOf(video),audioOnly:Boolean=false) {
        val media=connect();save();playlist=all
        setQueue(media,video,video.positionMs.takeIf{it<video.durationMs*.95} ?: 0)
        media.prepare();setAudioOnly(audioOnly || video.kind=="AUDIO");media.play();_state.value=_state.value.copy(video=video)
    }
    private suspend fun setQueue(media:MediaController,video:VideoEntity,position:Long) {
        val queue=if(preferences.flow.first().autoplayNext)playlist else listOf(video)
        val items=queue.map{row->MediaItem.Builder().setMediaId(row.id.toString()).setUri(row.contentUri).setSubtitleConfigurations(savedSubtitles(row)).setMediaMetadata(MediaMetadata.Builder().setTitle(row.title).setArtist(row.channelName).setArtworkUri(row.thumbnailPath.takeIf{it.isNotBlank()}?.let{Uri.fromFile(File(it))}).build()).build()}
        media.setMediaItems(items,queue.indexOfFirst{it.id==video.id}.coerceAtLeast(0),position)
    }
    fun setAudioOnly(enabled:Boolean){controller?.let{it.trackSelectionParameters=it.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO,enabled).build()};_state.value=_state.value.copy(audioOnly=enabled)}
    fun refreshAutoplay() {scope.launch{val video=_state.value.video ?: return@launch;val media=controller ?: return@launch;val position=media.currentPosition;val playing=media.playWhenReady;setQueue(media,video,position);media.prepare();media.playWhenReady=playing}}

    suspend fun toggle(){val media=connect();if(media.isPlaying){media.pause();save()}else media.play()}
    suspend fun close(){save();controller?.stop();controller?.clearMediaItems();_state.value=PlaybackState()}
    fun seek(position:Long){controller?.seekTo(position.coerceIn(0,state.value.duration))}
    fun skip(delta:Long)=seek(state.value.position+delta)
    fun speed(value:Float){controller?.setPlaybackSpeed(value.coerceIn(.25f,2f))}
    fun next(){val index=playlist.indexOfFirst{it.id==state.value.video?.id};playlist.getOrNull(index+1)?.let{next->scope.launch{play(next,playlist,_state.value.audioOnly)}}}
    fun previous(){val index=playlist.indexOfFirst{it.id==state.value.video?.id};val previous=playlist.getOrNull(index-1);if(previous!=null)scope.launch{play(previous,playlist,_state.value.audioOnly)}else seek(0)}
    suspend fun save(){_state.value.video?.let{dao.position(it.id,state.value.position)}}
}
