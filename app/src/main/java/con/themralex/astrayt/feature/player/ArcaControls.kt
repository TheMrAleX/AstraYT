package con.themralex.astrayt.feature.player

// Adapted from TheMrAleX/arca PlayerUi.kt at 5182d49f9b14d639d44d602a84045d453f6565ed.
// Arca's gesture arbitration, release-safe speed boost and buffered seek bar, with YouTube styling.
import android.app.Activity
import android.media.AudioManager
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextOverflow
import con.themralex.astrayt.timeline.TimelinePolicy
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
                if(offset.x in size.width/3f..size.width*2/3f){playback.controller?.let{if(it.playWhenReady)it.pause()else {if(current.ended)it.seekTo(0);it.play()}}}
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
        val motion=SingularityTheme.motion.enabled
        AnimatedVisibility(boosted,modifier=Modifier.align(Alignment.TopCenter).padding(top=if(immersive)24.dp else 12.dp),
            enter=if(motion)fadeIn(tween(120))+slideInVertically(tween(160)){-it/3}else EnterTransition.None,
            exit=if(motion)fadeOut(tween(100))else ExitTransition.None) {
            Row(Modifier.playerGlass(RoundedCornerShape(10.dp),strong=true).padding(horizontal=10.dp,vertical=5.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Rounded.FastForward,null,tint=Color.White.copy(alpha=.9f),modifier=Modifier.size(16.dp))
                Text(stringResource(R.string.player_speed_boost),color=Color.White,style=SingularityTheme.type.caption.copy(fontFeatureSettings="tnum"))
            }
        }
        val label=when{drag?.mode==DragMode.SEEK->Formatters.duration(drag!!.target);drag!=null->stringResource(if(drag!!.mode==DragMode.BRIGHTNESS)R.string.brightness else R.string.volume)+" ${(drag!!.value*100).toInt()} %";else->null}
        if(label!=null)Column(Modifier.align(Alignment.Center).widthIn(min=112.dp,max=220.dp).playerGlass(RoundedCornerShape(14.dp),strong=true).padding(horizontal=18.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(10.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                if(drag?.mode==DragMode.BRIGHTNESS || drag?.mode==DragMode.VOLUME)Icon(if(drag?.mode==DragMode.BRIGHTNESS)Icons.Rounded.Brightness6 else Icons.Rounded.VolumeUp,null,tint=Color.White,modifier=Modifier.size(20.dp))
                Text(label,color=Color.White,style=SingularityTheme.type.label.copy(fontFeatureSettings="tnum"))
            }
            if(drag?.mode==DragMode.BRIGHTNESS || drag?.mode==DragMode.VOLUME)Box(Modifier.width(128.dp).height(3.dp).background(Color.White.copy(alpha=.2f),CircleShape)){Box(Modifier.fillMaxWidth(drag!!.value).fillMaxHeight().background(Color.White,CircleShape))}
        }
        flash?.let{(forward,seconds)->Text((if(forward)"+"else"−")+"$seconds s",color=Color.White,modifier=Modifier.align(if(forward)Alignment.CenterEnd else Alignment.CenterStart).padding(24.dp).background(Color.Black.copy(alpha=.6f),CircleShape).padding(20.dp))}
    }
}
@Composable internal fun ArcaSeekBar(state:PlaybackState,onSeek:(Long)->Unit,onInteract:()->Unit,modifier:Modifier=Modifier,onScrubbing:(Boolean)->Unit={}) {
    val accent=SingularityTheme.colors.accent
    var scrub by remember(state.video?.id){mutableStateOf<Float?>(null)}
    val current by rememberUpdatedState(state);val seek by rememberUpdatedState(onSeek);val interact by rememberUpdatedState(onInteract);val scrubbing by rememberUpdatedState(onScrubbing)
    val fraction=scrub ?: PlayerTimeline.fraction(state.position,state.duration)
    val haptics=LocalHapticFeedback.current
    val chapterIndex=TimelinePolicy.chapterAt(state.chapters,PlayerTimeline.seek(fraction,state.duration))
    var lastChapter by remember(state.video?.id){mutableStateOf<Int?>(null)}
    LaunchedEffect(scrub!=null,chapterIndex){
        if(scrub==null)lastChapter=null
        else {if(lastChapter!=null && lastChapter!=chapterIndex && chapterIndex>=0)haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove);lastChapter=chapterIndex}
    }
    val boundaries=remember(state.chapters,state.duration){(listOf(0L)+state.chapters.map{it.startMs}+state.duration).distinct().sorted()}

    val boundarySet=remember(boundaries){boundaries.toHashSet()}
    val spans=remember(boundaries){boundaries.zipWithNext()}
    val overlays=remember(state.chapters,state.duration,state.sponsorSegments){TimelinePolicy.railSegments(state.chapters,state.duration,state.sponsorSegments).sortedBy{if(it.category=="sponsor")1 else 0}}
    val description=stringResource(R.string.player_seek)
    DisposableEffect(state.video?.id){onDispose{scrubbing(false)}}
    Box(modifier.fillMaxWidth().height(48.dp).semantics {
        contentDescription=description;progressBarRangeInfo=ProgressBarRangeInfo(fraction,0f..1f)
        setProgress {value->if(current.duration>0){seek(PlayerTimeline.seek(value,current.duration));interact();true}else false}
    }.pointerInput(state.video?.id){detectTapGestures{if(current.duration>0 && size.width>0){seek(PlayerTimeline.seek(it.x/size.width,current.duration));interact()}}}
        .pointerInput(state.video?.id){detectHorizontalDragGestures(onDragStart={
            if(current.duration>0 && size.width>0){scrub=(it.x/size.width).coerceIn(0f,1f);scrubbing(true);interact()}
        },onDragEnd={scrub?.let{seek(PlayerTimeline.seek(it,current.duration))};scrub=null;scrubbing(false);interact()},onDragCancel={scrub=null;scrubbing(false)},onHorizontalDrag={change,dx->
            if(scrub!=null && size.width>0){change.consume();scrub=((scrub ?: 0f)+dx/size.width).coerceIn(0f,1f);interact()}
        })},contentAlignment=Alignment.CenterStart) {
        scrub?.let{value->Column(Modifier.align(Alignment.BottomCenter).offset(y=(-44).dp).widthIn(max=240.dp).background(Color.Black.copy(alpha=.8f),RoundedCornerShape(8.dp)).padding(horizontal=10.dp,vertical=4.dp),horizontalAlignment=Alignment.CenterHorizontally){
            state.chapters.getOrNull(chapterIndex)?.let{Text(it.title,color=Color.White.copy(alpha=.85f),style=SingularityTheme.type.caption,maxLines=1,overflow=TextOverflow.Ellipsis)}
            Text(Formatters.duration(PlayerTimeline.seek(value,state.duration)),color=Color.White,style=SingularityTheme.type.caption)
        }}
        Canvas(Modifier.fillMaxWidth().height(16.dp)) {
            val h=(if(scrub!=null)5.dp else 3.dp).toPx();val y=(size.height-h)/2;val radius=CornerRadius(h/2)
            val gap=if(boundaries.size>2)2.dp.toPx()else 0f
            fun span(color:Color,start:Long,end:Long,limit:Float=1f) {
                val x0=size.width*PlayerTimeline.fraction(start,state.duration)
                val x1=size.width*PlayerTimeline.fraction(end,state.duration)
                val inset=minOf(gap/2,(x1-x0).coerceAtLeast(0f)/4)
                val left=x0+if(start>0 && start in boundarySet)inset else 0f
                val right=minOf(x1-if(end<state.duration && end in boundarySet)inset else 0f,size.width*limit)
                if(right>left)drawRoundRect(color,Offset(left,y),Size(right-left,h),radius)
            }
            spans.forEach{(a,b)->
                span(Color.White.copy(alpha=.25f),a,b)
                span(Color.White.copy(alpha=.45f),a,b,PlayerTimeline.fraction(state.buffered,state.duration))
                span(accent,a,b,fraction)
            }
            overlays.forEach{s->span(Color(TimelinePolicy.colors[s.category] ?: 0xFFFFFFFF),s.startMs,s.endMs)}
            drawCircle(Color.White,(if(scrub!=null)8.dp else 5.dp).toPx(),Offset((size.width*fraction).coerceIn(5.dp.toPx(),(size.width-5.dp.toPx()).coerceAtLeast(5.dp.toPx())),size.height/2))
        }
    }
}
