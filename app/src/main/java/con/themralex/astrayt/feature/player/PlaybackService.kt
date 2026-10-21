package con.themralex.astrayt.feature.player
import android.content.Intent
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.*
class PlaybackService:MediaSessionService() {
    private var session:MediaSession?=null
    override fun onCreate(){super.onCreate();val player=ExoPlayer.Builder(this).build().apply{setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);setHandleAudioBecomingNoisy(true)};session=MediaSession.Builder(this,player).build()}
    override fun onGetSession(controllerInfo:MediaSession.ControllerInfo):MediaSession?=session
    override fun onDestroy(){session?.let{it.player.release();it.release()};session=null;super.onDestroy()}
}
