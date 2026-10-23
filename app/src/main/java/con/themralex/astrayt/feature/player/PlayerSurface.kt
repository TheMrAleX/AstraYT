package con.themralex.astrayt.feature.player

import android.view.LayoutInflater
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import con.themralex.astrayt.R

/** TextureView follows Compose transforms; the controller has only one active video target. */
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
@Composable internal fun PlayerSurface(playback:Playback,state:PlaybackState,active:Boolean,fill:Boolean=false,modifier:Modifier=Modifier) {
    Box(modifier.background(Color.Black)) {
        AsyncImage(state.video?.thumbnailPath,null,Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
        key(state.video?.id){AndroidView(
            factory={LayoutInflater.from(it).inflate(R.layout.player_surface,null,false) as PlayerView},
            modifier=Modifier.fillMaxSize(),
            onRelease={playback.detachSurface(it);it.keepScreenOn=false},
            update={view->
                view.resizeMode=if(fill)AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                view.keepScreenOn=active && state.playing
                if(active)playback.attachSurface(view)else playback.detachSurface(view)
            }
        )}
    }
}
