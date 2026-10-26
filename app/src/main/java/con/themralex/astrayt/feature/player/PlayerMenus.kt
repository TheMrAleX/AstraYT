package con.themralex.astrayt.feature.player

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import con.themralex.astrayt.R
import con.themralex.astrayt.core.designsystem.*
import con.themralex.astrayt.feature.*

@Composable internal fun PlayerMenu(page:String,state:PlaybackState,onPage:(String)->Unit,onDismiss:()->Unit,onSpeed:(Float)->Unit,onTrack:(PlayerTrack)->Unit,onDisableText:()->Unit,onImport:()->Unit,onAudio:()->Unit,onMetadata:()->Unit,onSubtitles:()->Unit,onDelete:()->Unit,refreshing:Boolean){
    PlayerPanel(onDismiss,page,heading={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){SheetHeading(stringResource(when(page){"speed"->R.string.speed;"audio"->R.string.audio_tracks;"text"->R.string.subtitles;else->R.string.player_settings}),onDismiss,if(page!="menu")({onPage("menu")})else null);PlayerMenuTabs(page,onPage)}}){destination->
        when(destination){
            "speed"->{
                val speeds=listOf(.25f,.5f,.75f,1f,1.25f,1.5f,1.75f,2f)
                ChoiceGrid(speeds.map{if(it==1f)stringResource(R.string.speed_normal)else "$it×"},speeds.indexOf(state.speed)){onSpeed(speeds[it]);onDismiss()}
            }
            "audio"-> {
                if(state.audioTracks.isEmpty())Text(stringResource(R.string.player_no_audio_tracks),color=SingularityTheme.colors.mid,style=SingularityTheme.type.body,modifier=Modifier.padding(12.dp))
                state.audioTracks.forEach{track->OptionRow(Icons.Rounded.Headphones,track.displayName,{onTrack(track);onDismiss()},selected=track.selected)}
            }
            "text"->{
                OptionRow(Icons.Rounded.ClosedCaptionOff,stringResource(R.string.subtitles_off),{onDisableText();onDismiss()},selected=state.subtitleTracks.none{it.selected})
                state.subtitleTracks.forEach{track->OptionRow(Icons.Rounded.ClosedCaption,track.displayName,{onTrack(track);onDismiss()},selected=track.selected)}
                Spacer(Modifier.height(4.dp))
                OptionRow(Icons.Rounded.Subtitles,stringResource(R.string.subtitle_download),{onDismiss();onSubtitles()})
                OptionRow(Icons.Rounded.FolderOpen,stringResource(R.string.open_subtitle),onImport)
            }
            else->{
                Row(Modifier.fillMaxWidth().playerGlass(RoundedCornerShape(14.dp)).padding(14.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PlayCircleOutline,null,tint=SingularityTheme.colors.accent,modifier=Modifier.size(24.dp))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(state.video?.title.orEmpty(),color=Color.White,style=SingularityTheme.type.strong,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text("${state.speed}× · "+(state.audioTracks.firstOrNull{it.selected}?.displayName ?: state.video?.audioLabel.orEmpty()),color=SingularityTheme.colors.mid,style=SingularityTheme.type.label)
                    }
                }
                if(state.video?.kind=="VIDEO")OptionRow(if(state.audioOnly)Icons.Rounded.Videocam else Icons.Rounded.Headphones,stringResource(if(state.audioOnly)R.string.show_video else R.string.audio_only),{onAudio();onDismiss()})
                OptionRow(Icons.Rounded.Refresh,stringResource(if(refreshing)R.string.metadata_loading else R.string.metadata_download),{onDismiss();onMetadata()},enabled=!refreshing)
                OptionRow(Icons.Rounded.DeleteOutline,stringResource(R.string.delete),onDelete)
            }
        }
    }
}


@Composable private fun PlayerMenuTabs(page:String,onPage:(String)->Unit) {
    val c=SingularityTheme.colors
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf("speed" to R.string.speed,"audio" to R.string.audio_tracks,"text" to R.string.subtitles).forEach { (id,title) ->
            val selected=page==id
            val motion=SingularityTheme.motion.enabled
            val fill by animateColorAsState(if(selected)c.accent.copy(alpha=.16f)else Color.White.copy(alpha=.05f),tween(if(motion)160 else 0),label="player-tab-fill")
            val border by animateColorAsState(if(selected)c.accent.copy(alpha=.55f)else Color.White.copy(alpha=.1f),tween(if(motion)160 else 0),label="player-tab-border")
            Column(Modifier.heightIn(min=48.dp).clip(RoundedCornerShape(12.dp))
                .background(fill)
                .border(1.dp,border,RoundedCornerShape(12.dp))
                .selectable(selected,role=Role.Tab){onPage(id)}.padding(horizontal=16.dp,vertical=14.dp)) {
                Text(stringResource(title),color=if(selected)c.accent else c.text,style=SingularityTheme.type.label)
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable private fun PlayerPanel(onDismiss:()->Unit,page:String,heading:@Composable ()->Unit,content:@Composable ColumnScope.(String)->Unit) {
    val motion=SingularityTheme.motion.enabled
    val pages=rememberSaveableStateHolder()
    val pageTravel=with(androidx.compose.ui.platform.LocalDensity.current){24.dp.roundToPx()}
    var entered by remember{mutableStateOf(false)}
    val reveal by animateFloatAsState(if(entered || !motion)1f else 0f,tween(if(motion)220 else 0),label="player-panel")
    LaunchedEffect(Unit){entered=true}
    Dialog(onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.18f)).clickable(onClick=onDismiss)) {
            val side=maxWidth>maxHeight && maxWidth>=600.dp
            val shape=if(side)RoundedCornerShape(20.dp)else RoundedCornerShape(topStart=24.dp,topEnd=24.dp)
            Column(Modifier.align(if(side)Alignment.CenterEnd else Alignment.BottomCenter)
                .then(if(side)Modifier.displayCutoutPadding().padding(12.dp).width(400.dp).fillMaxHeight()else Modifier.widthIn(max=560.dp).fillMaxWidth().heightIn(min=minOf(360.dp,maxHeight*.65f),max=maxHeight*.86f))
                .graphicsLayer{alpha=reveal;translationX=if(side)(1f-reveal)*24.dp.toPx()else 0f;translationY=if(side)0f else (1f-reveal)*24.dp.toPx()}
                .clip(shape).background(Color(0xE6111317))
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha=.05f),Color.Transparent)))
                .border(1.dp,Color.White.copy(alpha=.14f),shape)
                .clickable(interactionSource=remember{MutableInteractionSource()},indication=null){}
                .navigationBarsPadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                heading()
                AnimatedContent(page,modifier=Modifier.weight(1f,fill=false).fillMaxWidth(),
                    transitionSpec={
                        if(!motion)(EnterTransition.None togetherWith ExitTransition.None).using(SizeTransform(clip=false,sizeAnimationSpec={_,_->snap()}))
                        else {
                            val order=listOf("menu","speed","audio","text")
                            val direction=MotionPolicy.direction(order.indexOf(initialState),order.indexOf(targetState))
                            val distance=pageTravel
                            ((fadeIn(tween(200,easing=LinearOutSlowInEasing))+slideInHorizontally(tween(240,easing=CubicBezierEasing(.2f,0f,0f,1f))){direction*minOf(it/8,distance)}) togetherWith
                                (fadeOut(tween(100))+slideOutHorizontally(tween(180)){-direction*minOf(it/8,distance)}))
                                .using(SizeTransform(clip=false,sizeAnimationSpec={_,_->tween(240,easing=CubicBezierEasing(.2f,0f,0f,1f))}))
                        }
                    },label="player-panel-page") { destination->
                    pages.SaveableStateProvider(destination) {
                        Column(Modifier.fillMaxWidth().selectableGroup().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){content(destination)}
                    }
                }
            }
        }
    }
}
