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
data class PlaybackState(val video:VideoEntity?=null,val playing:Boolean=false,val buffering:Boolean=false,val position:Long=0,val duration:Long=0,val buffered:Long=0,val speed:Float=1f,val audioOnly:Boolean=false,val audioTracks:List<PlayerTrack> = emptyList(),val subtitleTracks:List<PlayerTrack> = emptyList(),val ended:Boolean=false,val errorCode:Int?=null,val hasPrevious:Boolean=false,val hasNext:Boolean=false,val playWhenReady:Boolean=false)
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
@Singleton class Playback @Inject constructor(@ApplicationContext private val context:Context,private val dao:AstraDao,private val preferences:Preferences) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate+CoroutineExceptionHandler{_,_->})
    private val mutex=Mutex()
    private val selection=Mutex()
    private var preparing=false
    private var audioPreference=false
    private var target:java.lang.ref.WeakReference<androidx.media3.ui.PlayerView>?=null
    @androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
    fun attachSurface(view:androidx.media3.ui.PlayerView){
        val media=controller ?: return
        if(target?.get()===view && view.player===media)return
        androidx.media3.ui.PlayerView.switchTargetView(media,target?.get(),view)
        target=java.lang.ref.WeakReference(view)
    }
    fun detachSurface(view:androidx.media3.ui.PlayerView){
        view.player=null
        if(target?.get()===view)target=null
    }
    private fun update(player:Player){
        if(preparing)return
        val video=playlist.firstOrNull{it.id.toString()==player.currentMediaItem?.mediaId}
        val index=playlist.indexOfFirst{it.id==video?.id}
        val audioMode=audioPreference || video?.kind=="AUDIO"
        _state.value=_state.value.copy(video=video,audioOnly=audioMode,playing=player.isPlaying,buffering=player.playbackState==Player.STATE_BUFFERING,
            position=player.currentPosition.coerceAtLeast(0),duration=player.duration.takeIf{it>0} ?: video?.durationMs ?: 0,
            buffered=player.bufferedPosition.coerceAtLeast(0),speed=player.playbackParameters.speed,
            audioTracks=tracks(player,C.TRACK_TYPE_AUDIO),subtitleTracks=tracks(player,C.TRACK_TYPE_TEXT),
            playWhenReady=player.playWhenReady,ended=player.playbackState==Player.STATE_ENDED,errorCode=player.playerError?.errorCode,hasPrevious=index>0,hasNext=index>=0 && index<playlist.lastIndex)
        if(player.trackSelectionParameters.getTrackTypeDisabled(C.TRACK_TYPE_VIDEO)!=audioMode)player.trackSelectionParameters=player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO,audioMode).build()
    }
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
            override fun onEvents(player:Player,events:Player.Events){update(player)}
            override fun onMediaItemTransition(mediaItem:MediaItem?,reason:Int){
                if(!preparing){val old=_state.value;old.video?.takeIf{it.id.toString()!=mediaItem?.mediaId}?.let{video->scope.launch{dao.position(video.id,old.position)}}}
            }
            override fun onPlaybackStateChanged(playbackState:Int){if(!preparing && playbackState==Player.STATE_ENDED)scope.launch{save()}}
            override fun onIsPlayingChanged(isPlaying:Boolean){if(!preparing && !isPlaying)scope.launch{save()}}
        })
        scope.launch {var tick=0;while(isActive){delay(500);if(!preparing){_state.value=_state.value.copy(position=media.currentPosition.coerceAtLeast(0),duration=media.duration.takeIf{it>0} ?: state.value.video?.durationMs ?: 0,buffered=media.bufferedPosition.coerceAtLeast(0));if(++tick%10==0)save()}}}
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
    suspend fun play(video:VideoEntity,all:List<VideoEntity> = listOf(video),audioOnly:Boolean=false)=selection.withLock {
        val previous=_state.value
        audioPreference=audioOnly
        preparing=true
        controller?.pause();controller?.clearMediaItems()
        val position=PlayerTimeline.resume(video.positionMs,video.durationMs)
        _state.value=PlaybackState(video=video,position=position,duration=video.durationMs,buffering=true,playWhenReady=true,audioOnly=audioOnly || video.kind=="AUDIO")
        try {
            previous.video?.let{dao.position(it.id,previous.position)}
            val media=connect();playlist=all.ifEmpty{listOf(video)}.let{if(it.none{row->row.id==video.id})listOf(video)+it else it}
            setQueue(media,video,position)
            setAudioOnly(audioOnly);media.prepare();media.play()
        }catch(e:CancellationException){throw e}catch(_:Exception){
            _state.value=_state.value.copy(buffering=false,playing=false,playWhenReady=false,errorCode=PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        }finally{preparing=false;controller?.takeIf{it.currentMediaItem?.mediaId==video.id.toString()}?.let{update(it)}}
    }
    private suspend fun setQueue(media:MediaController,video:VideoEntity,position:Long) {
        val queue=if(preferences.flow.first().autoplayNext)playlist else listOf(video)
        // File enumeration and metadata creation stay off the UI thread during navigation.
        val items=withContext(Dispatchers.IO){queue.map{row->MediaItem.Builder().setMediaId(row.id.toString()).setUri(row.contentUri).setSubtitleConfigurations(savedSubtitles(row)).setMediaMetadata(MediaMetadata.Builder().setTitle(row.title).setArtist(row.channelName).setArtworkUri(row.thumbnailPath.takeIf{it.isNotBlank()}?.let{Uri.fromFile(File(it))}).build()).build()}}
        media.setMediaItems(items,queue.indexOfFirst{it.id==video.id}.coerceAtLeast(0),position)
    }
    fun setAudioOnly(enabled:Boolean){
        audioPreference=enabled
        val effective=enabled || state.value.video?.kind=="AUDIO"
        controller?.let{it.trackSelectionParameters=it.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO,effective).build()}
        _state.value=_state.value.copy(audioOnly=effective)
    }
    fun refreshAutoplay() {scope.launch{selection.withLock{
        val video=_state.value.video ?: return@withLock;val media=controller ?: return@withLock
        val position=media.currentPosition;val playing=media.playWhenReady
        preparing=true
        try{setQueue(media,video,position);media.prepare();media.playWhenReady=playing}finally{preparing=false;update(media)}
    }}}
    suspend fun toggle(){val media=connect();if(media.playWhenReady && media.playbackState!=Player.STATE_ENDED && media.playerError==null){media.pause();save()}else{if(media.playbackState==Player.STATE_ENDED)media.seekTo(0);if(media.playerError!=null)media.prepare();media.play()}}
    fun retry(){val video=state.value.video ?: return;val media=controller
        if(media?.currentMediaItem?.mediaId==video.id.toString()){media.prepare();media.play()}
        else scope.launch{play(video,playlist,audioPreference)}
    }
    suspend fun close()=selection.withLock{save();controller?.stop();controller?.clearMediaItems();_state.value=PlaybackState()}
    fun seek(position:Long){controller?.seekTo(position.coerceIn(0,state.value.duration.coerceAtLeast(0)))}
    fun skip(delta:Long)=seek((controller?.currentPosition ?: state.value.position)+delta)
    fun speed(value:Float){controller?.setPlaybackSpeed(value.coerceIn(.25f,2f))}
    fun next(){val index=playlist.indexOfFirst{it.id==state.value.video?.id};playlist.getOrNull(index+1)?.let{next->scope.launch{play(next,playlist,audioPreference)}}}
    fun previous(){if((controller?.currentPosition ?: state.value.position)>3000){seek(0);return};val index=playlist.indexOfFirst{it.id==state.value.video?.id};val previous=playlist.getOrNull(index-1);if(previous!=null)scope.launch{play(previous,playlist,audioPreference)}else seek(0)}
    suspend fun save(){_state.value.video?.let{dao.position(it.id,state.value.position)}}
}
