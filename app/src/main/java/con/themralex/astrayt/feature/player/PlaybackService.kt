package con.themralex.astrayt.feature.player

import androidx.media3.common.*
import androidx.media3.exoplayer.*
import androidx.media3.session.*
import con.themralex.astrayt.data.db.AstraDao
import con.themralex.astrayt.data.prefs.Preferences
import con.themralex.astrayt.data.prefs.Settings
import con.themralex.astrayt.timeline.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

@AndroidEntryPoint
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
class PlaybackService:MediaSessionService() {
    @Inject lateinit var preferences:Preferences
    @Inject lateinit var timelines:TimelineStore
    @Inject lateinit var dao:AstraDao
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate+CoroutineExceptionHandler{_,_->})
    private var session:MediaSession?=null
    private var settings=Settings()
    private var timeline=VideoTimeline()
    private var load:Job?=null
    private val messages=mutableListOf<PlayerMessage>()
    override fun onCreate(){
        super.onCreate()
        val player=ExoPlayer.Builder(this).build().apply{
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true)
            setHandleAudioBecomingNoisy(true);setSeekParameters(SeekParameters.EXACT)
        }
        fun checkSkip(){
            if(!player.isPlaying || !settings.sponsorRecognitionEnabled || !settings.sponsorSkipEnabled || player.currentMediaItem?.localConfiguration?.uri.toString()!=timeline.mediaUri)return
            val skip=TimelinePolicy.skipAt(timeline.sponsors,settings.sponsorBlockCategories,player.currentPosition,player.duration.takeIf{it>0} ?: return) ?: return
            val id=player.currentMediaItem?.mediaId.orEmpty()
            player.seekTo(skip.endMs)
            timelines.skipped(id,skip.category)
        }
        fun schedule(){
            messages.forEach{it.cancel()};messages.clear()
            if(settings.sponsorRecognitionEnabled && settings.sponsorSkipEnabled)timeline.sponsors.filter{it.category in settings.sponsorBlockCategories}.forEach{segment->
                messages+=player.createMessage{_,_->checkSkip()}.setLooper(mainLooper).setPosition(segment.startMs).setDeleteAfterDelivery(false).send()
            }
            checkSkip()
        }
        fun refresh(){
            load?.cancel();messages.forEach{it.cancel()};messages.clear();timeline=VideoTimeline()
            val id=player.currentMediaItem?.mediaId ?: return
            val uri=player.currentMediaItem?.localConfiguration?.uri.toString()
            load=scope.launch{
                val video=id.toLongOrNull()?.let{dao.video(it)} ?: return@launch
                val saved=timelines.read(video)
                if(player.currentMediaItem?.mediaId!=id || player.currentMediaItem?.localConfiguration?.uri.toString()!=uri)return@launch
                timeline=saved;schedule()
            }
        }
        player.addListener(object:Player.Listener {
            override fun onMediaItemTransition(mediaItem:MediaItem?,reason:Int){refresh()}
            override fun onPositionDiscontinuity(oldPosition:Player.PositionInfo,newPosition:Player.PositionInfo,reason:Int){checkSkip()}
            override fun onIsPlayingChanged(isPlaying:Boolean){if(isPlaying)checkSkip()}
            override fun onPlaybackStateChanged(playbackState:Int){if(playbackState==Player.STATE_READY)checkSkip()}
        })
        session=MediaSession.Builder(this,player).build()
        scope.launch{preferences.flow.collectLatest{settings=it;schedule()}}
        scope.launch{timelines.changes.collectLatest{refresh()}}
    }
    override fun onGetSession(controllerInfo:MediaSession.ControllerInfo):MediaSession?=session
    override fun onDestroy(){scope.cancel();messages.forEach{it.cancel()};session?.let{it.player.release();it.release()};session=null;super.onDestroy()}
}
