package con.themralex.astrayt.feature.player
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.graphics.graphicsLayer
import android.app.Activity
import android.app.PictureInPictureParams
import android.graphics.Rect
import android.os.Build
import android.util.Rational
import android.content.pm.ActivityInfo
import android.media.AudioManager
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.*
import coil3.compose.AsyncImage
import con.themralex.astrayt.R
import con.themralex.astrayt.feature.*
import con.themralex.astrayt.data.db.VideoEntity
import con.themralex.astrayt.data.prefs.Settings
import con.themralex.astrayt.core.designsystem.*
import con.themralex.astrayt.core.util.Formatters
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
@Composable fun PlayerScreen(main:MainModel,state:PlaybackState,all:List<VideoEntity>,settings:Settings,onAutoplay:(Boolean)->Unit,onChannel:(String)->Unit,onBack:()->Unit,onDelete:(VideoEntity)->Unit,pip:Boolean=false) {
    val video=state.video ?: return
    val context=LocalContext.current;val activity=context as? Activity;val c=SingularityTheme.colors
    var controls by remember{mutableStateOf(true)};var full by rememberSaveable{mutableStateOf(false)};var fill by remember{mutableStateOf(false)};var speeds by remember{mutableStateOf(false)};var expanded by rememberSaveable{mutableStateOf(false)};var gestureText by remember{mutableStateOf("")}
    var dragOffset by remember{mutableFloatStateOf(0f)}
    val translated by animateFloatAsState(dragOffset,spring(dampingRatio=.9f,stiffness=650f),label="minimize")
    var trackSheet by remember{mutableStateOf<String?>(null)}
    var deleting by remember{mutableStateOf(false)}
    var portraitLock by rememberSaveable{mutableStateOf(false)}
    var locked by rememberSaveable{mutableStateOf(false)}
    var interaction by remember{mutableLongStateOf(0)}
    val landscape=androidx.compose.ui.platform.LocalConfiguration.current.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val immersive=full || landscape
    BackHandler(full || locked){if(locked)locked=false else {full=false;portraitLock=true}}
    var sourceRect by remember{mutableStateOf<Rect?>(null)}
    LaunchedEffect(state.playing,state.audioOnly,sourceRect){if(Build.VERSION.SDK_INT>=31){val builder=PictureInPictureParams.Builder().setAspectRatio(Rational(16,9)).setAutoEnterEnabled(state.playing && video.kind=="VIDEO" && !state.audioOnly);sourceRect?.let{builder.setSourceRectHint(it)};activity?.setPictureInPictureParams(builder.build())}}
    DisposableEffect(Unit){onDispose{if(Build.VERSION.SDK_INT>=31)activity?.setPictureInPictureParams(PictureInPictureParams.Builder().setAutoEnterEnabled(false).build())}}
    val playback=main.playback
    val subtitlePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null){main.addSubtitle(uri);trackSheet=null}}

    LaunchedEffect(controls,state.playing,gestureText,interaction){if(gestureText.isNotBlank()){delay(900);gestureText=""};if(controls && state.playing){delay(3000);controls=false}}
    DisposableEffect(full,immersive,portraitLock){val window=activity?.window;val controller=window?.let{WindowCompat.getInsetsController(it,it.decorView)};if(immersive){activity?.requestedOrientation=if(full)ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else if(portraitLock)ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;controller?.hide(WindowInsetsCompat.Type.systemBars());controller?.systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE}else{activity?.requestedOrientation=if(portraitLock)ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;controller?.show(WindowInsetsCompat.Type.systemBars())};onDispose{activity?.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;controller?.show(WindowInsetsCompat.Type.systemBars());main.save()}}
    Column(Modifier.fillMaxSize().graphicsLayer{translationY=if(immersive || pip)0f else translated}.background(c.void)) {
        Box(Modifier.fillMaxWidth().onGloballyPositioned{coordinates->val rect=coordinates.boundsInWindow();val result=Rect(rect.left.toInt(),rect.top.toInt(),rect.right.toInt(),rect.bottom.toInt());if(result!=sourceRect)sourceRect=result}.then(if(immersive||pip)Modifier.weight(1f)else Modifier.aspectRatio(16f/9)).background(Color.Black)
        ) {
            if(video.kind=="AUDIO" || state.audioOnly) {Box(Modifier.fillMaxSize().background(c.abyss));AsyncImage(video.thumbnailPath,null,Modifier.fillMaxSize().padding(28.dp).clip(RoundedCornerShape(20.dp)),contentScale=androidx.compose.ui.layout.ContentScale.Crop)}
            else AndroidView(factory={PlayerView(it).apply{useController=false;setKeepContentOnPlayerReset(true);setShutterBackgroundColor(android.graphics.Color.BLACK);player=playback.controller}},modifier=Modifier.fillMaxSize(),onRelease={it.player=null;it.keepScreenOn=false},update={it.player=playback.controller;it.keepScreenOn=state.playing;it.resizeMode=if(fill)AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT})
            if(!pip)ArcaGestureLayer(playback,state,locked,{controls=!controls},{interaction=System.currentTimeMillis()},immersive,onBack,{dragOffset=it},Modifier.fillMaxSize())
            if(locked && controls && !pip)SingularityIcon(Icons.Rounded.Lock,stringResource(R.string.screen_unlock),{locked=false;interaction=System.currentTimeMillis()},Modifier.align(Alignment.CenterEnd).background(Color.Black.copy(alpha=.6f),RoundedCornerShape(12.dp)))
            if(state.buffering)AccretionRing(0f,64.dp,"indeterminate",Modifier.align(Alignment.Center))
            if(gestureText.isNotBlank())Tag(gestureText)
            if(controls && !locked && !pip)Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xC008090B),Color.Transparent,Color(0xC008090B)))).padding(4.dp)) {
                Row(Modifier.align(Alignment.TopCenter),verticalAlignment=Alignment.CenterVertically){SingularityIcon(Icons.Rounded.KeyboardArrowDown,stringResource(R.string.close),{if(full){full=false;portraitLock=true}else onBack()});Text(video.title,color=c.text,style=SingularityTheme.type.label,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f).padding(8.dp));SingularityIcon(Icons.Rounded.LockOpen,stringResource(R.string.screen_lock),{locked=true;controls=false});SingularityIcon(Icons.Rounded.MoreVert,stringResource(R.string.options),{trackSheet="menu"})}
                Row(Modifier.align(Alignment.Center),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(24.dp)) {SingularityIcon(Icons.Rounded.Replay10,stringResource(R.string.seek_back),{playback.skip(-10000);interaction=System.currentTimeMillis()});Box(Modifier.size(64.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.Black.copy(alpha=.5f)),contentAlignment=Alignment.Center){Icon(if(state.playing)Icons.Rounded.Pause else Icons.Rounded.PlayArrow,stringResource(if(state.playing)R.string.pause else R.string.play),Modifier.size(64.dp).clickable{main.toggle()}.padding(18.dp),tint=Color.White)};SingularityIcon(Icons.Rounded.Forward10,stringResource(R.string.seek_forward),{playback.skip(10000);interaction=System.currentTimeMillis()})}
                Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("${Formatters.duration(state.position)} / ${Formatters.duration(state.duration)}",color=Color.White,style=SingularityTheme.type.caption.copy(fontFeatureSettings="tnum"))
                    ArcaSeekBar(state,{playback.seek(it)},{interaction=System.currentTimeMillis()},Modifier.weight(1f))
                    if(immersive)SingularityIcon(Icons.Rounded.AspectRatio,stringResource(R.string.resize_video),{fill=!fill;interaction=System.currentTimeMillis()})
                    SingularityIcon(if(immersive)Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,stringResource(R.string.fullscreen),{if(immersive){full=false;portraitLock=true}else {portraitLock=false;full=true}})
                }
            }
        }
        if(!immersive && !pip)Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal=16.dp,vertical=14.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(video.title,color=c.text,style=SingularityTheme.type.title)
            val viewCountLabel=if(video.viewCount>0)stringResource(R.string.views_text,Formatters.views(video.viewCount))else null
            Text(buildList{viewCountLabel?.let{add(it)};if(video.uploadDateMs>0)add(android.text.format.DateUtils.getRelativeTimeSpanString(video.uploadDateMs,System.currentTimeMillis(),android.text.format.DateUtils.DAY_IN_MILLIS).toString())}.joinToString(" · "),color=c.mid,style=SingularityTheme.type.caption)
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.fillMaxWidth().clickable{onChannel(video.channelId)}) {
                AsyncImage(video.channelAvatarPath,null,Modifier.size(42.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.high),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
                Column(Modifier.weight(1f)){Text(video.channelName,color=c.text,style=SingularityTheme.type.strong);Text(stringResource(R.string.offline_saved,Formatters.bytes(video.fileSizeBytes)),color=c.low,style=SingularityTheme.type.caption)}
                Icon(Icons.Rounded.ChevronRight,null,tint=c.mid)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){if(video.likeCount>=0)Row(Modifier.heightIn(min=48.dp).background(c.high,RoundedCornerShape(24.dp)).padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){Icon(Icons.Rounded.ThumbUp,stringResource(R.string.likes),tint=c.text,modifier=Modifier.size(20.dp));Text(stringResource(R.string.likes_text,Formatters.views(video.likeCount)),color=c.text,style=SingularityTheme.type.label)};SingularityButton(stringResource(R.string.share),{share(context,listOf(video))},variant="secondary");SingularityButton(stringResource(if(state.audioOnly)R.string.show_video else R.string.audio_only),{playback.setAudioOnly(!state.audioOnly)},variant="secondary");SingularityButton(stringResource(R.string.open_youtube),{external(context,"https://www.youtube.com/watch?v=${video.ytId}")},variant="secondary");SingularityButton(stringResource(R.string.delete),{deleting=true},variant="tertiary")}
            if(video.removedDurationMs>0)Tag(stringResource(R.string.segments_removed,Formatters.duration(video.removedDurationMs)))
            if(video.description.isNotBlank())GlassCard{FormattedDescription(video.description,expanded);SingularityButton(stringResource(if(expanded)R.string.show_less else R.string.show_more),{expanded=!expanded},variant="tertiary")}
            SettingsRow(Icons.Rounded.PlayCircle,stringResource(R.string.autoplay),trailing={SingularitySwitch(settings.autoplayNext,onAutoplay,stringResource(R.string.autoplay))})
            SectionHeader(stringResource(R.string.up_next))
            all.filter{it.id!=video.id}.sortedBy{it.channelId!=video.channelId}.take(30).forEach{next->VideoCardCompact(next.title,next.channelName,next.thumbnailPath){main.play(next,all)}}
        }
    }
    if(deleting)SingularityDialog(stringResource(R.string.delete),stringResource(R.string.delete_question),stringResource(R.string.delete),stringResource(R.string.cancel),{deleting=false;onDelete(video);onBack()},{deleting=false})
    trackSheet?.let { type->SingularitySheet({trackSheet=null}) {
        when(type) {
            "menu"->{
                SingularityButton(stringResource(R.string.speed),{trackSheet=null;speeds=true},variant="secondary")
                if(state.audioTracks.isNotEmpty())SingularityButton(stringResource(R.string.audio_tracks),{trackSheet="audio"},variant="secondary")
                SingularityButton(stringResource(R.string.subtitles),{trackSheet="text"},variant="secondary")
            }
            "audio"->{
                SectionHeader(stringResource(R.string.audio_tracks))
                state.audioTracks.forEach { track->SingularityButton(track.displayName,{playback.selectTrack(track);trackSheet=null},variant=if(track.selected)"primary"else"secondary") }
            }
            "text"->{
                SectionHeader(stringResource(R.string.subtitles))
                SingularityButton(stringResource(R.string.subtitles_off),{playback.disableSubtitles();trackSheet=null},variant=if(state.subtitleTracks.none{it.selected})"primary"else"secondary")
                state.subtitleTracks.forEach { track->SingularityButton(track.displayName,{playback.selectTrack(track);trackSheet=null},variant=if(track.selected)"primary"else"secondary") }
                SingularityButton(stringResource(R.string.open_subtitle),{runCatching{subtitlePicker.launch(arrayOf("text/*","application/x-subrip","application/ttml+xml","application/octet-stream"))}},variant="secondary")
            }
        }
    }}
    if(speeds)SingularitySheet({speeds=false}){listOf(.25f,.5f,.75f,1f,1.25f,1.5f,1.75f,2f).forEach{speed->SingularityButton("${speed}×",{playback.speed(speed);speeds=false;interaction=System.currentTimeMillis()},variant=if(state.speed==speed)"primary"else"secondary")}}
}
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
@Composable fun MiniPlayer(state:PlaybackState,playback:Playback,onOpen:()->Unit,onToggle:()->Unit,onClose:()->Unit,modifier:Modifier=Modifier) {
    val video=state.video ?: return
    val c=SingularityTheme.colors
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(c.surface).clickable(onClick=onOpen)) {
        Row(Modifier.padding(start=8.dp,end=4.dp,top=6.dp,bottom=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Box(Modifier.width(96.dp).aspectRatio(16f/9).clip(RoundedCornerShape(8.dp)).background(Color.Black)) {
                if(video.kind=="VIDEO" && !state.audioOnly)AndroidView(factory={PlayerView(it).apply{useController=false;setKeepContentOnPlayerReset(true);player=playback.controller}},modifier=Modifier.fillMaxSize(),onRelease={it.player=null;it.keepScreenOn=false},update={it.player=playback.controller;it.keepScreenOn=state.playing;it.resizeMode=AspectRatioFrameLayout.RESIZE_MODE_FIT})
                else AsyncImage(video.thumbnailPath,null,Modifier.fillMaxSize(),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
                // The view does not consume taps intended to expand the mini player.
                Box(Modifier.fillMaxSize().clickable(onClick=onOpen))
            }
            Column(Modifier.weight(1f)){Text(video.title,color=c.text,style=SingularityTheme.type.label,maxLines=1,overflow=TextOverflow.Ellipsis);Text(video.channelName,color=c.low,style=SingularityTheme.type.caption,maxLines=1,overflow=TextOverflow.Ellipsis)}
            SingularityIcon(if(state.playing)Icons.Rounded.Pause else Icons.Rounded.PlayArrow,stringResource(if(state.playing)R.string.pause else R.string.play),onToggle)
            SingularityIcon(Icons.Rounded.Close,stringResource(R.string.close),onClose)
        }
        AccretionProgressBar(if(state.duration>0)state.position.toFloat()/state.duration else 0f,height=2.dp)
    }
}
