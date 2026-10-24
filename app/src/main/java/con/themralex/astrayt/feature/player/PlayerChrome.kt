package con.themralex.astrayt.feature.player

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import con.themralex.astrayt.R
import con.themralex.astrayt.core.designsystem.*
import con.themralex.astrayt.core.util.Formatters

/** Translucent surfaces over the real video, following Arca's player controls. */
internal fun Modifier.playerGlass(shape:Shape=CircleShape,strong:Boolean=false):Modifier =
    clip(shape).background(Color.Black.copy(alpha=if(strong).68f else .58f))
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha=if(strong).20f else .11f),Color.White.copy(alpha=.04f))))
        .border(1.dp,Brush.verticalGradient(listOf(Color.White.copy(alpha=if(strong).35f else .22f),Color.White.copy(alpha=.08f))),shape)

@Composable internal fun PlayerGlassIcon(icon:ImageVector,label:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,size:Dp=48.dp,strong:Boolean=false) {
    Box(modifier.size(size).playerGlass(strong=strong).clickable(enabled=enabled,role=Role.Button,onClick=onClick),contentAlignment=Alignment.Center) {
        Icon(icon,label,tint=Color.White.copy(alpha=if(enabled)1f else .35f),modifier=Modifier.size(if(strong)32.dp else 23.dp))
    }
}

@Composable internal fun PlayerGlassLabel(text:String,label:String,onClick:()->Unit,icon:ImageVector?=null,modifier:Modifier=Modifier) {
    Row(modifier.heightIn(min=48.dp).playerGlass(RoundedCornerShape(14.dp)).semantics{contentDescription=label}
        .clickable(role=Role.Button,onClick=onClick).padding(horizontal=14.dp,vertical=10.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        if(icon!=null)Icon(icon,null,tint=Color.White,modifier=Modifier.size(20.dp))
        Text(text,color=Color.White,style=SingularityTheme.type.label,maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}

@Composable internal fun PlayerChrome(
    state:PlaybackState,immersive:Boolean,onMinimize:()->Unit,onMenu:(String)->Unit,onLock:()->Unit,
    onToggle:()->Unit,onSkip:(Long)->Unit,onSeek:(Long)->Unit,onPrevious:()->Unit,onNext:()->Unit,
    onResize:()->Unit,onFullscreen:()->Unit,onInteract:()->Unit,onScrubbing:(Boolean)->Unit,
) {
    val video=state.video ?: return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val roomy=immersive && maxHeight>=330.dp
        val directTracks=immersive && maxWidth>=600.dp && LocalDensity.current.fontScale<=1.4f
        val largeText=LocalDensity.current.fontScale>1.4f
        val showDuration=!largeText || maxWidth>=480.dp
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0f to Color.Black.copy(alpha=.65f),.32f to Color.Black.copy(alpha=.08f),.6f to Color.Black.copy(alpha=.08f),1f to Color.Black.copy(alpha=.7f))))
        Column(Modifier.fillMaxSize().then(if(immersive)Modifier.displayCutoutPadding().navigationBarsPadding()else Modifier)
            .padding(horizontal=if(immersive)20.dp else 10.dp,vertical=if(roomy)12.dp else 4.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                PlayerGlassIcon(Icons.Rounded.KeyboardArrowDown,stringResource(R.string.player_minimize),onMinimize)
                if(immersive)Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    if(roomy && !largeText)Text(video.channelName,color=Color(0xFFD1D3D8),style=SingularityTheme.type.label,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text(video.title,color=Color.White,style=SingularityTheme.type.small,maxLines=1,overflow=TextOverflow.Ellipsis)
                }else Spacer(Modifier.weight(1f))
                if(directTracks) {
                    PlayerGlassIcon(Icons.Rounded.ClosedCaption,stringResource(R.string.subtitles),{onMenu("text")})
                    if(state.audioTracks.isNotEmpty())PlayerGlassIcon(Icons.Rounded.Headphones,stringResource(R.string.audio_tracks),{onMenu("audio")})
                }
                if(immersive)PlayerGlassIcon(Icons.Rounded.LockOpen,stringResource(R.string.screen_lock),onLock)
                PlayerGlassIcon(Icons.Rounded.MoreHoriz,stringResource(R.string.player_settings),{onMenu("menu")})
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.align(Alignment.CenterHorizontally),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(if(roomy)28.dp else if(immersive)10.dp else 20.dp)) {
                if(immersive && !roomy)PlayerGlassIcon(Icons.Rounded.SkipPrevious,stringResource(R.string.player_previous),onPrevious,enabled=state.hasPrevious || state.position>3000)
                PlayerGlassIcon(Icons.Rounded.Replay10,stringResource(R.string.seek_back),{onSkip(-10000)},enabled=state.duration>0,size=if(roomy)54.dp else 48.dp)
                Box(contentAlignment=Alignment.Center) {
                    PlayerGlassIcon(when{state.ended->Icons.Rounded.Replay;state.playWhenReady->Icons.Rounded.Pause;else->Icons.Rounded.PlayArrow},
                        stringResource(when{state.ended->R.string.player_replay;state.playWhenReady->R.string.pause;else->R.string.play}),onToggle,
                        size=if(roomy)76.dp else 56.dp,strong=true)
                    if(state.buffering)AccretionRing(0f,if(roomy)72.dp else 52.dp,"indeterminate")
                }
                PlayerGlassIcon(Icons.Rounded.Forward10,stringResource(R.string.seek_forward),{onSkip(10000)},enabled=state.duration>0,size=if(roomy)54.dp else 48.dp)
                if(immersive && !roomy)PlayerGlassIcon(Icons.Rounded.SkipNext,stringResource(R.string.player_next),onNext,enabled=state.hasNext)
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(Formatters.duration(state.position),color=Color.White,style=SingularityTheme.type.label.copy(fontFeatureSettings="tnum"),maxLines=1)
                ArcaSeekBar(state,onSeek,onInteract,Modifier.weight(1f),onScrubbing)
                if(showDuration)Text(Formatters.duration(state.duration),color=Color(0xFFD1D3D8),style=SingularityTheme.type.label.copy(fontFeatureSettings="tnum"),maxLines=1)
                if(immersive && !roomy)PlayerGlassIcon(Icons.Rounded.AspectRatio,stringResource(R.string.resize_video),onResize)
                if(!roomy)PlayerGlassIcon(if(immersive)Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,stringResource(if(immersive)R.string.player_exit_fullscreen else R.string.fullscreen),onFullscreen)
            }
            if(roomy)Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                PlayerGlassIcon(Icons.Rounded.SkipPrevious,stringResource(R.string.player_previous),onPrevious,enabled=state.hasPrevious || state.position>3000)
                PlayerGlassIcon(Icons.Rounded.SkipNext,stringResource(R.string.player_next),onNext,enabled=state.hasNext)
                Spacer(Modifier.width(8.dp))
                PlayerGlassLabel("${state.speed}×",stringResource(R.string.speed),{onMenu("speed")},Icons.Rounded.Speed)
                if(state.audioTracks.isNotEmpty())PlayerGlassLabel(state.audioTracks.firstOrNull{it.selected}?.displayName ?: stringResource(R.string.audio_tracks),stringResource(R.string.audio_tracks),{onMenu("audio")},Icons.Rounded.Headphones,Modifier.widthIn(max=180.dp))
                PlayerGlassIcon(Icons.Rounded.ClosedCaption,stringResource(R.string.subtitles),{onMenu("text")})
                PlayerGlassIcon(Icons.Rounded.AspectRatio,stringResource(R.string.resize_video),onResize)
                PlayerGlassIcon(Icons.Rounded.FullscreenExit,stringResource(R.string.player_exit_fullscreen),onFullscreen)
            }
        }
    }
}
