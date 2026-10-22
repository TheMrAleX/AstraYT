package con.themralex.astrayt.feature.player

// Adapted from TheMrAleX/arca PlayerUi.kt at 5182d49f9b14d639d44d602a84045d453f6565ed.
// Arca's gesture arbitration, release-safe speed boost and buffered seek bar, with YouTube styling.
import android.app.Activity
import android.media.AudioManager
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import con.themralex.astrayt.R
import con.themralex.astrayt.core.designsystem.SingularityTheme
import con.themralex.astrayt.core.util.Formatters
import kotlinx.coroutines.*
import kotlin.math.abs

internal val YoutubeRed=Color(0xFFFF3636)
private enum class DragMode { SEEK, BRIGHTNESS, VOLUME }
private data class DragState(val mode:DragMode,val value:Float=0f,val target:Long=0)
@Composable internal fun ArcaGestureLayer(playback:Playback,state:PlaybackState,locked:Boolean,onTap:()->Unit,onInteract:()->Unit,immersive:Boolean,onMinimize:()->Unit,onMinimizeDrag:(Float)->Unit,modifier:Modifier=Modifier) {
    val context=LocalContext.current;val activity=context as? Activity
    val audio=remember {context.getSystemService(AudioManager::class.java)}
    val maxVolume=remember{audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)}
    val current by rememberUpdatedState(state);val disabled by rememberUpdatedState(locked)
    val tap by rememberUpdatedState(onTap);val interact by rememberUpdatedState(onInteract)
    val minimize by rememberUpdatedState(onMinimize);val minimizeDrag by rememberUpdatedState(onMinimizeDrag)
    var boosted by remember{mutableStateOf(false)};var drag by remember{mutableStateOf<DragState?>(null)}
    var flash by remember{mutableStateOf<Pair<Boolean,Int>?>(null)}
    var oldSpeed by remember{mutableFloatStateOf(1f)}
    DisposableEffect(playback){val brightness=activity?.window?.attributes?.screenBrightness;onDispose{if(boosted)playback.speed(oldSpeed);if(brightness!=null)activity.window.attributes=activity.window.attributes.apply{screenBrightness=brightness}}}
    LaunchedEffect(flash){if(flash!=null){delay(900);flash=null}}
    Box(modifier
        .pointerInput(playback) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                boosted=false
                val held=withTimeoutOrNull(700L) {
                    while(true) {
                        val change=awaitPointerEvent().changes.firstOrNull{it.id==down.id} ?: return@withTimeoutOrNull false
                        if(!change.pressed || change.isConsumed || (change.position-down.position).getDistance()>viewConfiguration.touchSlop)return@withTimeoutOrNull false
                    }
                }==null
                if(held && !disabled && current.playing) {
                    boosted=true;drag=null;oldSpeed=current.speed;playback.speed(2f);interact()
                    try {while(true){val change=awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull{it.id==down.id} ?: break;change.consume();if(!change.pressed)break}}
                    finally{playback.speed(oldSpeed);boosted=false}
                }
            }
        }
        .pointerInput(playback) {
            detectTapGestures(onTap={tap()},onDoubleTap={offset->
                if(disabled){tap();return@detectTapGestures}
                if(offset.x in size.width/3f..size.width*2/3f){playback.controller?.let{if(it.isPlaying)it.pause()else it.play()}}
                else {val forward=offset.x>size.width/2;playback.skip(if(forward)10000 else -10000);flash=forward to ((flash?.takeIf{it.first==forward}?.second ?: 0)+10)}
                interact()
            })
        }
        .then(if(immersive)Modifier.pointerInput(playback,immersive) {
            var startPosition=0L;var startValue=0f;var side=DragMode.BRIGHTNESS;var accumulated=Offset.Zero
            detectDragGestures(onDragStart={offset->
                accumulated=Offset.Zero;startPosition=current.position;side=if(offset.x<size.width/2)DragMode.BRIGHTNESS else DragMode.VOLUME
                startValue=if(side==DragMode.BRIGHTNESS)activity?.window?.attributes?.screenBrightness?.takeIf{it>=0} ?: .5f else audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()/maxVolume
                drag=null
            },onDragEnd={drag?.takeIf{it.mode==DragMode.SEEK && !boosted && !disabled}?.let{playback.seek(it.target)};drag=null},onDragCancel={drag=null},onDrag={change,amount->
                if(disabled || boosted)return@detectDragGestures
                change.consume();accumulated+=amount
                val mode=drag?.mode ?: when{abs(accumulated.x)>abs(accumulated.y)*1.3f && abs(accumulated.x)>12f->DragMode.SEEK;abs(accumulated.y)>12f->side;else->return@detectDragGestures}
                drag=when(mode){
                    DragMode.SEEK->DragState(mode,target=(startPosition+accumulated.x/size.width*minOf(current.duration,180000)).toLong().coerceIn(0,current.duration.coerceAtLeast(0)))
                    DragMode.BRIGHTNESS->{val value=(startValue-accumulated.y/(size.height*.8f)).coerceIn(.01f,1f);activity?.window?.let{it.attributes=it.attributes.apply{screenBrightness=value}};DragState(mode,value)}
                    DragMode.VOLUME->{val value=(startValue-accumulated.y/(size.height*.8f)).coerceIn(0f,1f);audio.setStreamVolume(AudioManager.STREAM_MUSIC,(value*maxVolume).toInt(),0);DragState(mode,value)}
                };interact()
            })
        }else Modifier.pointerInput(playback,immersive) {
            var distance=0f
            val threshold=80.dp.toPx()
            detectVerticalDragGestures(onDragStart={distance=0f},onDragCancel={minimizeDrag(0f)},onDragEnd={
                if(!disabled && distance>threshold)minimize()else minimizeDrag(0f)
            },onVerticalDrag={change,dy->
                if(!disabled && !boosted){change.consume();distance=(distance+dy).coerceAtLeast(0f);minimizeDrag(distance)}
            })
        })) {
        val label=when{boosted->"2×";drag?.mode==DragMode.SEEK->Formatters.duration(drag!!.target);drag!=null->stringResource(if(drag!!.mode==DragMode.BRIGHTNESS)R.string.brightness else R.string.volume)+" ${(drag!!.value*100).toInt()} %";else->null}
        if(label!=null)Text(label,color=Color.White,modifier=Modifier.align(Alignment.Center).background(Color.Black.copy(alpha=.75f),RoundedCornerShape(10.dp)).padding(12.dp))
        flash?.let{(forward,seconds)->Text((if(forward)"+"else"−")+"$seconds s",color=Color.White,modifier=Modifier.align(if(forward)Alignment.CenterEnd else Alignment.CenterStart).padding(24.dp).background(Color.Black.copy(alpha=.6f),CircleShape).padding(20.dp))}
    }
}
@Composable internal fun ArcaSeekBar(state:PlaybackState,onSeek:(Long)->Unit,onInteract:()->Unit,modifier:Modifier=Modifier) {
    var scrub by remember{mutableStateOf<Float?>(null)}
    val current by rememberUpdatedState(state);val seek by rememberUpdatedState(onSeek);val interact by rememberUpdatedState(onInteract)
    val duration=state.duration.coerceAtLeast(1)
    val fraction=(scrub ?: state.position.toFloat()/duration).coerceIn(0f,1f)
    val description=stringResource(R.string.duration)
    Column(modifier) {
        scrub?.let{Text(Formatters.duration((it*duration).toLong()),color=Color.White,style=SingularityTheme.type.caption)}
        Box(Modifier.fillMaxWidth().height(48.dp).semantics {
            contentDescription=description;progressBarRangeInfo=ProgressBarRangeInfo(fraction,0f..1f)
            setProgress {value->seek((value.coerceIn(0f,1f)*current.duration).toLong());interact();true}
        }.pointerInput(Unit){detectTapGestures{seek((it.x/size.width*current.duration).toLong());interact()}}
            .pointerInput(Unit){detectHorizontalDragGestures(onDragStart={scrub=(it.x/size.width).coerceIn(0f,1f);interact()},onDragEnd={scrub?.let{seek((it*current.duration).toLong())};scrub=null},onDragCancel={scrub=null},onHorizontalDrag={change,dx->change.consume();scrub=((scrub ?: 0f)+dx/size.width).coerceIn(0f,1f);interact()})},contentAlignment=Alignment.CenterStart) {
            Canvas(Modifier.fillMaxWidth().height(16.dp)) {
                val h=3.dp.toPx();val y=(size.height-h)/2;val radius=CornerRadius(h/2)
                drawRoundRect(Color.White.copy(alpha=.25f),Offset(0f,y),Size(size.width,h),radius)
                drawRoundRect(Color.White.copy(alpha=.45f),Offset(0f,y),Size(size.width*(state.buffered.toFloat()/duration).coerceIn(0f,1f),h),radius)
                drawRoundRect(YoutubeRed,Offset(0f,y),Size(size.width*fraction,h),radius)
                drawCircle(YoutubeRed,(if(scrub!=null)8.dp else 6.dp).toPx(),Offset(size.width*fraction,size.height/2))
            }
        }
    }
}
