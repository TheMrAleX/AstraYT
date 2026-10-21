package con.themralex.astrayt.feature.player
import android.app.Activity
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

@Composable fun PlayerScreen(main:MainModel,state:PlaybackState,all:List<VideoEntity>,settings:Settings,onAutoplay:(Boolean)->Unit,onChannel:(String)->Unit,onBack:()->Unit,onDelete:(VideoEntity)->Unit,pip:Boolean=false) {
    val video=state.video ?: return
    val context=LocalContext.current;val activity=context as? Activity;val c=SingularityTheme.colors
    var controls by remember{mutableStateOf(true)};var full by rememberSaveable{mutableStateOf(false)};var fill by remember{mutableStateOf(false)};var speeds by remember{mutableStateOf(false)};var expanded by rememberSaveable{mutableStateOf(false)};var gestureText by remember{mutableStateOf("")}
    val playback=main.playback
    val audio=remember{context.getSystemService(AudioManager::class.java)}
    LaunchedEffect(controls,state.playing,gestureText){if(gestureText.isNotBlank()){delay(900);gestureText=""};if(controls && state.playing){delay(3000);controls=false}}
    DisposableEffect(full){val window=activity?.window;val controller=window?.let{WindowCompat.getInsetsController(it,it.decorView)};if(full){activity?.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;controller?.hide(WindowInsetsCompat.Type.systemBars());controller?.systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE}else{activity?.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;controller?.show(WindowInsetsCompat.Type.systemBars())};onDispose{activity?.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;controller?.show(WindowInsetsCompat.Type.systemBars());main.save()}}
    Column(Modifier.fillMaxSize().background(c.void)) {
        Box(Modifier.fillMaxWidth().then(if(full||pip)Modifier.weight(1f)else Modifier.aspectRatio(if(video.kind=="AUDIO")1f else 16f/9)).background(Color.Black)
            .pointerInput(video.id){detectTapGestures(onTap={controls=!controls},onDoubleTap={offset->val delta=if(offset.x<size.width/2)-10000L else 10000L;playback.skip(delta);gestureText=if(delta>0)"+10 s"else"−10 s"},onLongPress={playback.speed(2f);gestureText="2×"},onPress={val old=playback.state.value.speed;tryAwaitRelease();playback.speed(old)})}
            .pointerInput(video.id){var left=false;var delta=0f;detectVerticalDragGestures(onDragStart={left=it.x<size.width/2;delta=0f},onVerticalDrag={change,amount->change.consume();delta-=amount/size.height;if(left){activity?.window?.let{window->val attrs=window.attributes;attrs.screenBrightness=((if(attrs.screenBrightness<0).5f else attrs.screenBrightness)-amount/size.height).coerceIn(.01f,1f);window.attributes=attrs}}else if(kotlin.math.abs(delta)>.04f){audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,if(delta>0)AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,0);delta=0f}})}
            .pointerInput(video.id){detectTransformGestures{_,_,zoom,_->if(zoom>1.03f)fill=true else if(zoom<.97f)fill=false}}
        ) {
            if(video.kind=="AUDIO") {AsyncImage(video.thumbnailPath,null,Modifier.fillMaxSize(),contentScale=androidx.compose.ui.layout.ContentScale.Crop);AccretionRing(if(state.duration>0)state.position.toFloat()/state.duration else 0f,100.dp,modifier=Modifier.align(Alignment.Center))}
            else AndroidView(factory={PlayerView(it).apply{useController=false;player=playback.controller}},modifier=Modifier.fillMaxSize(),update={it.player=playback.controller;it.resizeMode=if(fill)AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT})
            if(state.buffering)AccretionRing(0f,64.dp,"indeterminate",Modifier.align(Alignment.Center))
            if(gestureText.isNotBlank())Tag(gestureText)
            if(controls && !pip)Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xB3040208),Color.Transparent,Color(0xB3040208)))).padding(12.dp),verticalArrangement=Arrangement.SpaceBetween) {
                Row(verticalAlignment=Alignment.CenterVertically){SingularityIcon(Icons.Rounded.KeyboardArrowDown,stringResource(R.string.close),{if(full)full=false else onBack()});Text(video.title,color=c.text,style=SingularityTheme.type.label,maxLines=1,modifier=Modifier.weight(1f).padding(8.dp));SingularityIcon(Icons.Rounded.MoreVert,stringResource(R.string.speed),{speeds=true})}
                Row(Modifier.align(Alignment.CenterHorizontally),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(24.dp)) {SingularityIcon(Icons.Rounded.SkipPrevious,stringResource(R.string.previous),{playback.previous()});SingularityIcon(if(state.playing)Icons.Rounded.Pause else Icons.Rounded.PlayArrow,stringResource(if(state.playing)R.string.pause else R.string.play),{main.toggle()},Modifier.size(64.dp));SingularityIcon(Icons.Rounded.SkipNext,stringResource(R.string.next_video),{playback.next()})}
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {Text("${Formatters.duration(state.position)} / ${Formatters.duration(state.duration)}",color=c.text,style=SingularityTheme.type.caption);Box(Modifier.weight(1f)){SingularitySlider((state.position/1000).toInt(),0..(state.duration/1000).toInt().coerceAtLeast(1),stringResource(R.string.duration)){playback.seek(it*1000L)}};SingularityIcon(Icons.Rounded.Fullscreen,stringResource(R.string.fullscreen),{full=!full})}
            }
        }
        if(!full && !pip)Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(video.title,color=c.text,style=SingularityTheme.type.title)
            Text("${video.channelName} · ${Formatters.bytes(video.fileSizeBytes)}",color=c.low,style=SingularityTheme.type.caption)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){SingularityButton(stringResource(R.string.share),{share(context,listOf(video))},variant="secondary");SingularityButton(stringResource(R.string.open_youtube),{external(context,"https://www.youtube.com/watch?v=${video.ytId}")},variant="secondary");SingularityButton(stringResource(R.string.delete),{onDelete(video);onBack()},variant="tertiary")}
            SettingsRow(Icons.Rounded.AccountCircle,video.channelName,onClick={onChannel(video.channelId)})
            if(video.description.isNotBlank())GlassCard{Text(video.description,color=c.mid,style=SingularityTheme.type.body,maxLines=if(expanded)Int.MAX_VALUE else 3);SingularityButton(stringResource(if(expanded)R.string.show_less else R.string.show_more),{expanded=!expanded},variant="tertiary")}
            SettingsRow(Icons.Rounded.PlayCircle,stringResource(R.string.autoplay),trailing={SingularitySwitch(settings.autoplayNext,onAutoplay,stringResource(R.string.autoplay))})
            SectionHeader(stringResource(R.string.up_next))
            all.filter{it.id!=video.id}.sortedBy{it.channelId!=video.channelId}.forEach{next->VideoCardCompact(next.title,next.channelName,next.thumbnailPath){main.play(next,all)}}
        }
    }
    if(speeds)SingularitySheet({speeds=false}){listOf(.25f,.5f,.75f,1f,1.25f,1.5f,1.75f,2f).forEach{speed->SingularityButton("${speed}×",{playback.speed(speed);speeds=false},variant=if(state.speed==speed)"primary"else"secondary")}}
}
@Composable fun MiniPlayer(state:PlaybackState,onOpen:()->Unit,onToggle:()->Unit,onClose:()->Unit,modifier:Modifier=Modifier) {
    val video=state.video ?: return
    GlassCard(modifier.clickable(onClick=onOpen)) {Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){AsyncImage(video.thumbnailPath,null,Modifier.size(48.dp),contentScale=androidx.compose.ui.layout.ContentScale.Crop);Text(video.title,color=SingularityTheme.colors.text,style=SingularityTheme.type.label,maxLines=1,modifier=Modifier.weight(1f));SingularityIcon(if(state.playing)Icons.Rounded.Pause else Icons.Rounded.PlayArrow,stringResource(if(state.playing)R.string.pause else R.string.play),onToggle);SingularityIcon(Icons.Rounded.Close,stringResource(R.string.close),onClose)}}
}
