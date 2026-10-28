package con.themralex.astrayt.feature

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import con.themralex.astrayt.R
import con.themralex.astrayt.core.designsystem.*
import con.themralex.astrayt.core.util.*
import con.themralex.astrayt.data.db.*
import con.themralex.astrayt.download.Progress
import con.themralex.astrayt.youtube.*

@Composable internal fun CollectionCaptureHeader(list:ResolvedLink.Playlist,state:CaptureState,model:CaptureModel) {
    val c=SingularityTheme.colors
    Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
        coil3.compose.AsyncImage(list.thumbnail,null,Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(16.dp)).background(c.high),contentScale=ContentScale.Crop)
        Text(list.title,color=c.text,style=SingularityTheme.type.headline)
        Text(pluralStringResource(R.plurals.collection_selection,state.selected.size,state.selected.size,list.entries.size),color=c.mid,style=SingularityTheme.type.caption)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            SingularityButton(stringResource(R.string.select_all),model::selectAll,variant="secondary",modifier=Modifier.weight(1f))
            SingularityButton(stringResource(R.string.select_none),{state.selected.toList().forEach(model::select)},variant="tertiary",modifier=Modifier.weight(1f))
        }
        SegmentedToggle(listOf(stringResource(R.string.video),stringResource(R.string.audio)),if(state.audioOnly)1 else 0,{model.kind(it==1)})
        GlassCard {
            SectionHeader(stringResource(R.string.quality_all))
            if(!state.audioOnly)QualityGrid(state.height,model::height)
            Text(stringResource(R.string.quality_all_hint),color=c.mid,style=SingularityTheme.type.caption)
            Row(Modifier.fillMaxWidth().toggleable(state.grouped,role=Role.Switch,onValueChange=model::grouped).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(stringResource(R.string.save_collection),color=c.text,style=SingularityTheme.type.label,modifier=Modifier.weight(1f))
                SingularitySwitch(state.grouped,model::grouped,stringResource(R.string.save_collection),interactive=false)
            }
            AnimatedVisibility(state.grouped,enter=expandVertically(tween(if(SingularityTheme.motion.enabled)220 else 0))+fadeIn(tween(if(SingularityTheme.motion.enabled)160 else 0)),exit=shrinkVertically(tween(if(SingularityTheme.motion.enabled)180 else 0))+fadeOut(tween(if(SingularityTheme.motion.enabled)120 else 0))){LinkField(state.collectionName,model::collectionName,stringResource(R.string.collection_name),{})}
        }
        if(list.entries.isEmpty())Text(stringResource(R.string.collection_empty),color=c.mid,style=SingularityTheme.type.body)
    }
}

@Composable internal fun CollectionCaptureEntry(index:Int,entry:PlaylistEntry,state:CaptureState,model:CaptureModel) {
    val c=SingularityTheme.colors
    var qualityOpen by rememberSaveable(entry.ytId){mutableStateOf(false)}
    val selected=entry.ytId in state.selected
    val duration=if(SingularityTheme.motion.enabled)220 else 0
    Column(Modifier.animateContentSize(tween(duration)).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(48.dp).toggleable(selected,role=Role.Checkbox,onValueChange={model.select(entry.ytId)}),contentAlignment=Alignment.Center){
                Icon(if(selected)Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,stringResource(R.string.select_video),tint=if(selected)c.accent else c.low)
            }
            Box(Modifier.width(112.dp).aspectRatio(16f/9).clip(RoundedCornerShape(10.dp)).background(c.high).clickable{model.select(entry.ytId)}) {
                coil3.compose.AsyncImage(entry.thumbnail,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                Text(Formatters.duration(entry.durationMs),color=androidx.compose.ui.graphics.Color.White,style=SingularityTheme.type.caption,modifier=Modifier.align(Alignment.BottomEnd).background(androidx.compose.ui.graphics.Color.Black.copy(alpha=.8f)).padding(3.dp))
            }
            Column(Modifier.weight(1f).clickable{model.select(entry.ytId)},verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(entry.title,color=c.text,style=SingularityTheme.type.label,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(entry.channel,color=c.low,style=SingularityTheme.type.caption,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
        }
        if(!state.audioOnly)Row(Modifier.fillMaxWidth().padding(start=58.dp),verticalAlignment=Alignment.CenterVertically) {
            Text((index+1).toString(),color=c.low,style=SingularityTheme.type.caption,modifier=Modifier.weight(1f))
            SingularityButton(StreamPlanner.qualityLabel(state.entryHeights[entry.ytId] ?: state.height),{qualityOpen=!qualityOpen},variant="tertiary")
        }
        AnimatedVisibility(qualityOpen && !state.audioOnly,enter=expandVertically(tween(duration))+fadeIn(),exit=shrinkVertically(tween(duration))+fadeOut()) {
            GlassCard {QualityGrid(state.entryHeights[entry.ytId] ?: state.height,{model.entryHeight(entry.ytId,it)})}
        }
    }
}

@Composable internal fun CollectionCard(list:PlaylistEntity,count:Int,thumbnail:String,onClick:()->Unit) {
    val c=SingularityTheme.colors
    GlassCard(Modifier.fillMaxWidth().clickable(role=Role.Button,onClick=onClick)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(96.dp,64.dp).clip(RoundedCornerShape(10.dp)).background(c.high)) {
                coil3.compose.AsyncImage(thumbnail.ifBlank{list.thumbnail},null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                Icon(Icons.Rounded.PlaylistPlay,null,tint=androidx.compose.ui.graphics.Color.White,modifier=Modifier.align(Alignment.BottomEnd).background(androidx.compose.ui.graphics.Color.Black.copy(alpha=.7f)).padding(4.dp))
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(list.title,color=c.text,style=SingularityTheme.type.strong,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(pluralStringResource(R.plurals.collection_videos,count,count),color=c.mid,style=SingularityTheme.type.caption)
            }
            Icon(Icons.Rounded.ChevronRight,null,tint=c.low)
        }
    }
}

@Composable internal fun QueueCollection(list:PlaylistEntity,rows:List<DownloadEntity>,progress:Map<Long,Progress>,model:QueueModel,onPlay:(Long)->Unit) {
    val c=SingularityTheme.colors
    val expandedGroups by model.expanded.collectAsStateWithLifecycle()
    val expanded=list.id in expandedGroups
    val state=CollectionPolicy.state(rows)
    val active=rows.any{it.state in setOf("QUEUED","FETCHING","DOWNLOADING","MUXING","SUBTITLES","WAITING_NETWORK")}
    GlassCard(Modifier.animateContentSize(tween(if(SingularityTheme.motion.enabled)240 else 0))) {
        CollectionCard(list,rows.size,rows.firstOrNull()?.thumbnailPath.orEmpty()){model.toggleCollection(list.id)}
        Text(stringResource(stateResource(state))+" · "+stringResource(R.string.collection_completed,rows.count{it.state=="DONE"},rows.size),color=c.mid,style=SingularityTheme.type.caption)
        AccretionProgressBar(CollectionPolicy.fraction(rows,progress),height=3.dp)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(rows.any{it.state !in setOf("DONE","CANCELED")}){
                SingularityButton(stringResource(if(active)R.string.pause_all else R.string.resume_all),{if(active)model.pauseCollection(list.id)else model.resumeCollection(list.id)},variant="secondary",modifier=Modifier.weight(1f))
                SingularityButton(stringResource(R.string.cancel),{model.cancelCollection(list.id)},variant="tertiary")
            }
        }
        AnimatedVisibility(expanded,enter=expandVertically(tween(if(SingularityTheme.motion.enabled)220 else 0))+fadeIn(tween(if(SingularityTheme.motion.enabled)160 else 0)),exit=shrinkVertically(tween(if(SingularityTheme.motion.enabled)180 else 0))+fadeOut(tween(if(SingularityTheme.motion.enabled)120 else 0))) {
            LazyColumn(Modifier.heightIn(max=560.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                items(rows,key={it.id},contentType={"collection-download"}){row->
                    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(row.title,color=c.text,style=SingularityTheme.type.label,maxLines=2,overflow=TextOverflow.Ellipsis)
                                Text(stringResource(if(row.state=="FAILED")Errors.string(row.errorCode)else stateResource(row.state)),color=if(row.state=="FAILED")c.error else c.mid,style=SingularityTheme.type.caption)
                            }
                            if(row.state=="DONE")SingularityIcon(Icons.Rounded.PlayArrow,stringResource(R.string.play),{onPlay(row.id)})
                            else if(row.state!="CANCELED"){
                                val resumable=row.state in setOf("PAUSED","FAILED")
                                SingularityIcon(if(resumable)Icons.Rounded.PlayArrow else Icons.Rounded.Pause,stringResource(if(resumable)R.string.resume else R.string.pause),{if(resumable)model.resume(row.id)else model.pause(row.id)})
                                SingularityIcon(Icons.Rounded.Close,stringResource(R.string.cancel),{model.cancel(row.id)})
                            }
                        }
                        AccretionProgressBar(CollectionPolicy.fraction(listOf(row),progress),height=3.dp)
                    }
                }
            }
        }
    }
}

@Composable internal fun LibraryCollection(list:PlaylistEntity,videos:List<VideoEntity>,onClose:()->Unit,onPlay:(VideoEntity,List<VideoEntity>,Boolean)->Unit,onMenu:(VideoEntity)->Unit) {
    var closing by remember{mutableStateOf(false)}
    val motion=SingularityTheme.motion.enabled
    LaunchedEffect(closing){if(closing){kotlinx.coroutines.delay(if(motion)200L else 0L);onClose()}}
    Dialog(onDismissRequest={closing=true},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        val c=SingularityTheme.colors
        BackHandler(onBack={closing=true})
        val visible=remember{androidx.compose.animation.core.MutableTransitionState(false)}.apply{targetState=!closing}
        AnimatedVisibility(visibleState=visible,enter=fadeIn(tween(if(motion)180 else 0))+slideInVertically(tween(if(motion)240 else 0)){it/12},exit=fadeOut(tween(if(motion)180 else 0))+slideOutVertically(tween(if(motion)200 else 0)){it/12}) {
            Column(Modifier.fillMaxSize().background(c.void).statusBarsPadding().navigationBarsPadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically){SingularityIcon(Icons.Rounded.ArrowBack,stringResource(R.string.close),{closing=true});Text(list.title,color=c.text,style=SingularityTheme.type.headline,modifier=Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis)}
                LazyColumn(verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    item(key="header",contentType="header") {
                        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            coil3.compose.AsyncImage(videos.firstOrNull()?.thumbnailPath ?: list.thumbnail,null,Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)).background(c.high),contentScale=ContentScale.Crop)
                            Text(pluralStringResource(R.plurals.library_summary,videos.size,videos.size,Formatters.bytes(videos.sumOf{it.fileSizeBytes})),color=c.mid,style=SingularityTheme.type.caption)
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                SingularityButton(stringResource(R.string.play_all),{videos.firstOrNull()?.let{onPlay(it,videos,false)}},Modifier.weight(1f),enabled=videos.isNotEmpty())
                                SingularityButton(stringResource(R.string.shuffle),{val shuffled=videos.shuffled();shuffled.firstOrNull()?.let{onPlay(it,shuffled,false)}},Modifier.weight(1f),variant="secondary",enabled=videos.isNotEmpty())
                            }
                        }
                    }
                    itemsIndexed(videos,key={_,v->v.id},contentType={_,_->"collection-video"}){index,video->
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text((index+1).toString(),color=c.low,style=SingularityTheme.type.caption)
                            Box(Modifier.weight(1f)){VideoCardCompact(video.title,video.channelName+" · "+Formatters.duration(video.durationMs),video.thumbnailPath){onPlay(video,videos,false)}}
                            SingularityIcon(Icons.Rounded.MoreVert,stringResource(R.string.options),{onMenu(video)})
                        }
                    }
                }
            }
        }
    }
}
