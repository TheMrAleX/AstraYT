package con.themralex.astrayt.feature.player
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import android.app.Activity
import android.app.PictureInPictureParams
import android.graphics.Rect
import android.os.Build
import android.util.Rational
import android.content.pm.ActivityInfo
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import con.themralex.astrayt.R
import con.themralex.astrayt.feature.*
import con.themralex.astrayt.data.db.VideoEntity
import con.themralex.astrayt.data.prefs.Settings
import con.themralex.astrayt.core.designsystem.*
import con.themralex.astrayt.core.util.Formatters
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
@Composable fun PlayerScreen(main:MainModel,extras:VideoExtrasModel,state:PlaybackState,all:List<VideoEntity>,settings:Settings,onAutoplay:(Boolean)->Unit,onChannel:(String)->Unit,onBack:()->Unit,onDelete:(VideoEntity)->Unit,pip:Boolean=false,active:Boolean=true) {
    val video=state.video ?: return
    val context=LocalContext.current;val activity=context as? Activity;val c=SingularityTheme.colors;val motion=SingularityTheme.motion.enabled
    val refreshing by extras.refreshing.collectAsStateWithLifecycle()
    val playback=main.playback
    val watchList=rememberSaveable(video.id,saver=LazyListState.Saver){LazyListState()}
    var descriptionExpanded by rememberSaveable(video.id){mutableStateOf(false)}
    var controls by remember(video.id){mutableStateOf(true)}
    var full by rememberSaveable{mutableStateOf(false)}
    var fill by rememberSaveable{mutableStateOf(false)}
    var trackSheet by remember{mutableStateOf<String?>(null)}
    var deleting by remember{mutableStateOf(false)}
    var portraitLock by rememberSaveable{mutableStateOf(false)}
    var locked by rememberSaveable{mutableStateOf(false)}
    var scrubbing by remember(video.id){mutableStateOf(false)}
    var interaction by remember{mutableLongStateOf(0)}
    var dragOffset by remember{mutableFloatStateOf(0f)}
    val translated by animateFloatAsState(dragOffset,if(motion)spring(dampingRatio=1f,stiffness=1000f)else snap(),label="minimize")
    val landscape=androidx.compose.ui.platform.LocalConfiguration.current.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val immersive=full || landscape
    fun interact(){interaction=android.os.SystemClock.uptimeMillis()}
    fun leaveFullscreen(){full=false;portraitLock=true}
    BackHandler(immersive || locked){if(locked){locked=false;controls=true}else leaveFullscreen()}
    var sourceRect by remember{mutableStateOf<Rect?>(null)}
    LaunchedEffect(state.playing,state.audioOnly,sourceRect,active){if(Build.VERSION.SDK_INT>=31){
        val builder=PictureInPictureParams.Builder().setAspectRatio(Rational(16,9)).setAutoEnterEnabled(active && state.playing && video.kind=="VIDEO" && !state.audioOnly)
        sourceRect?.let{builder.setSourceRectHint(it)};activity?.setPictureInPictureParams(builder.build())
    }}
    DisposableEffect(activity){
        val original=activity?.requestedOrientation
        onDispose{
            if(Build.VERSION.SDK_INT>=31)activity?.setPictureInPictureParams(PictureInPictureParams.Builder().setAutoEnterEnabled(false).build())
            if(original!=null)activity.requestedOrientation=original
            activity?.window?.let{WindowCompat.getInsetsController(it,it.decorView).show(WindowInsetsCompat.Type.systemBars())}
            main.save()
        }
    }
    LaunchedEffect(full,landscape,portraitLock,pip,active){
        val window=activity?.window ?: return@LaunchedEffect
        val bars=WindowCompat.getInsetsController(window,window.decorView)
        activity.requestedOrientation=when{!active || pip->ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;full->ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;portraitLock->ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;else->ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED}
        if(immersive && !pip && active){bars.hide(WindowInsetsCompat.Type.systemBars());bars.systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE}else if(!pip)bars.show(WindowInsetsCompat.Type.systemBars())
    }
    LaunchedEffect(controls,state.playing,state.buffering,scrubbing,trackSheet,interaction){
        if(controls && state.playing && !state.buffering && !scrubbing && trackSheet==null){delay(3000);controls=false}
    }
    LaunchedEffect(state.ended,state.errorCode){if(state.ended || state.errorCode!=null)controls=true}
    var skippedCategory by remember(video.id){mutableStateOf<String?>(null)}
    LaunchedEffect(state.skipNotice?.serial){
        val notice=state.skipNotice
        if(notice!=null && notice.mediaId==video.id.toString() && System.nanoTime()-notice.serial in 0..2_000_000_000L){skippedCategory=notice.category;delay(1800);skippedCategory=null}
    }
    val subtitlePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null){main.addSubtitle(uri);trackSheet=null}}
    Column(Modifier.fillMaxSize().then(if(!immersive && !pip)Modifier.statusBarsPadding()else Modifier).graphicsLayer{
        translationY=if(immersive || pip)0f else translated
        if(!immersive && !pip){val progress=(translated/1500f).coerceIn(0f,.2f);scaleX=1f-progress*.3f;scaleY=1f-progress*.3f}
    }.background(c.void)) {
        Box(Modifier.fillMaxWidth().onGloballyPositioned{coordinates->val rect=coordinates.boundsInWindow();val result=Rect(rect.left.toInt(),rect.top.toInt(),rect.right.toInt(),rect.bottom.toInt());if(result!=sourceRect)sourceRect=result}
            .then(if(immersive||pip)Modifier.weight(1f)else Modifier.aspectRatio(16f/9)).background(Color.Black)) {
            if(video.kind=="AUDIO" || state.audioOnly) {
                Box(Modifier.fillMaxSize().background(c.abyss))
                AsyncImage(video.thumbnailPath,null,Modifier.fillMaxSize().padding(if(immersive)48.dp else 20.dp).clip(RoundedCornerShape(16.dp)),contentScale=androidx.compose.ui.layout.ContentScale.Fit)
            }else PlayerSurface(playback,state,active,fill,Modifier.fillMaxSize())
            if(!pip)key(video.id){ArcaGestureLayer(playback,state,locked,{controls=!controls;interact()},{controls=false;interact()},immersive,onBack,{dragOffset=it},Modifier.fillMaxSize())}
            androidx.compose.animation.AnimatedVisibility(visible=skippedCategory!=null,modifier=Modifier.align(Alignment.TopCenter).padding(top=52.dp),enter=fadeIn(tween(if(motion)140 else 0)),exit=fadeOut(tween(if(motion)120 else 0))) {
                val label=when(state.skipNotice?.category ?: skippedCategory){"sponsor"->R.string.sb_sponsor_skipped;"intro"->R.string.sb_intro_skipped;"outro"->R.string.sb_outro_skipped;else->R.string.sb_segment_skipped}
                Text(stringResource(label),color=Color.White,style=SingularityTheme.type.caption,modifier=Modifier.background(Color.Black.copy(alpha=.72f),RoundedCornerShape(8.dp)).padding(horizontal=10.dp,vertical=5.dp))
            }
            if(locked && controls && !pip)PlayerGlassIcon(Icons.Rounded.Lock,stringResource(R.string.screen_unlock),{locked=false;interact()},Modifier.align(Alignment.CenterEnd).padding(end=12.dp).background(Color.Black.copy(alpha=.65f),CircleShape))
            if(state.buffering && !controls && state.errorCode==null && !pip)Box(Modifier.align(Alignment.Center).size(64.dp).background(Color.Black.copy(alpha=.5f),CircleShape),contentAlignment=Alignment.Center){AccretionRing(0f,40.dp,"indeterminate")}
            if(state.errorCode!=null && !pip)PlayerGlassIcon(Icons.Rounded.KeyboardArrowDown,stringResource(R.string.player_minimize),{if(immersive)leaveFullscreen()else onBack()},Modifier.align(Alignment.TopStart))
            if(state.errorCode!=null && !pip)Column(Modifier.align(Alignment.Center).background(Color.Black.copy(alpha=.88f),RoundedCornerShape(12.dp)).padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.player_error),color=Color.White,style=SingularityTheme.type.label)
                SingularityButton(stringResource(R.string.retry),{playback.retry();interact()},variant="secondary")
            }
            androidx.compose.animation.AnimatedVisibility(visible=controls && !locked && !pip && state.errorCode==null,enter=fadeIn(tween(if(motion)140 else 0)),exit=fadeOut(tween(if(motion)180 else 0))) {
                PlayerChrome(state,immersive,
                    onMinimize={if(immersive)leaveFullscreen()else onBack()},
                    onMenu={trackSheet=it;interact()},onLock={locked=true;controls=false},
                    onToggle={main.toggle();interact()},onSkip={playback.skip(it);interact()},
                    onSeek={playback.seek(it)},onPrevious={playback.previous();interact()},onNext={playback.next();interact()},
                    onResize={fill=!fill;interact()},
                    onFullscreen={if(immersive)leaveFullscreen()else{portraitLock=false;full=true};interact()},
                    onInteract={interact()},onScrubbing={scrubbing=it})
            }
        }
        if(!immersive && !pip)WatchPage(video,video.id in refreshing,{extras.refreshMetadata(video)},{extras.openSubtitles(video)},state.audioOnly,all,settings.autoplayNext,onAutoplay,onChannel,{playback.setAudioOnly(!state.audioOnly)},{main.play(it,all)},watchList,descriptionExpanded,{descriptionExpanded=!descriptionExpanded},Modifier.weight(1f).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                PlayerGlassLabel("${state.speed}×",stringResource(R.string.speed),{trackSheet="speed";interact()},Icons.Rounded.Speed)
                if(state.audioTracks.isNotEmpty())PlayerGlassLabel(state.audioTracks.firstOrNull{it.selected}?.displayName ?: stringResource(R.string.audio_tracks),stringResource(R.string.audio_tracks),{trackSheet="audio";interact()},Icons.Rounded.Headphones,Modifier.widthIn(max=180.dp))
                PlayerGlassLabel(stringResource(R.string.subtitles),stringResource(R.string.subtitles),{trackSheet="text";interact()},Icons.Rounded.ClosedCaption)
            }
        }
    }
    if(deleting)SingularityDialog(stringResource(R.string.delete),stringResource(R.string.delete_question),stringResource(R.string.delete),stringResource(R.string.cancel),{deleting=false;onDelete(video);onBack()},{deleting=false})
    trackSheet?.let{page->PlayerMenu(page,state,{trackSheet=it},{trackSheet=null},{playback.speed(it);interact()},{playback.selectTrack(it)},{playback.disableSubtitles()}, {runCatching{subtitlePicker.launch(arrayOf("text/*","application/x-subrip","application/ttml+xml","application/octet-stream"))}}, {playback.setAudioOnly(!state.audioOnly)}, {extras.refreshMetadata(video)}, {extras.openSubtitles(video)}, {trackSheet=null;deleting=true},video.id in refreshing)}
}
@Composable fun MiniPlayer(state:PlaybackState,playback:Playback,onOpen:()->Unit,onToggle:()->Unit,onClose:()->Unit,modifier:Modifier=Modifier,active:Boolean=true) {
    val video=state.video ?: return
    val c=SingularityTheme.colors
    var dismissDistance by remember(video.id){mutableFloatStateOf(0f)}
    val close by rememberUpdatedState(onClose)
    Column(modifier.pointerInput(video.id){
        val threshold=56.dp.toPx()
        detectVerticalDragGestures(onDragStart={dismissDistance=0f},onDragCancel={dismissDistance=0f},onDragEnd={if(dismissDistance>threshold)close();dismissDistance=0f},onVerticalDrag={change,dy->change.consume();dismissDistance=(dismissDistance+dy).coerceAtLeast(0f)})
    }.graphicsLayer{translationY=dismissDistance*.6f;alpha=(1f-dismissDistance/600f).coerceIn(.3f,1f)}.clip(RoundedCornerShape(14.dp)).background(c.surface).border(1.dp,c.hairline,RoundedCornerShape(14.dp))) {
        Row(Modifier.fillMaxWidth().clickable(role=Role.Button,onClick=onOpen).padding(start=6.dp,end=2.dp,top=5.dp,bottom=5.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Box(Modifier.width(92.dp).aspectRatio(16f/9).clip(RoundedCornerShape(8.dp)).background(Color.Black)) {
                if(video.kind=="VIDEO" && !state.audioOnly)PlayerSurface(playback,state,active,modifier=Modifier.fillMaxSize())
                else AsyncImage(video.thumbnailPath,null,Modifier.fillMaxSize(),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
                Box(Modifier.fillMaxSize().clickable(role=Role.Button,onClick=onOpen))
            }
            Column(Modifier.weight(1f)){Text(video.title,color=c.text,style=SingularityTheme.type.label,maxLines=1,overflow=TextOverflow.Ellipsis);Text(video.channelName,color=c.low,style=SingularityTheme.type.caption,maxLines=1,overflow=TextOverflow.Ellipsis)}
            SingularityIcon(if(state.ended)Icons.Rounded.Replay else if(state.playWhenReady)Icons.Rounded.Pause else Icons.Rounded.PlayArrow,stringResource(if(state.playWhenReady)R.string.pause else R.string.play),onToggle)
            SingularityIcon(Icons.Rounded.Close,stringResource(R.string.close),onClose)
        }
        AccretionProgressBar(PlayerTimeline.fraction(state.position,state.duration),height=2.dp)
    }
}
