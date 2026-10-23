package con.themralex.astrayt.feature.player

import android.text.format.DateUtils
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import con.themralex.astrayt.R
import con.themralex.astrayt.core.designsystem.*
import con.themralex.astrayt.core.util.Formatters
import con.themralex.astrayt.data.db.VideoEntity
import con.themralex.astrayt.feature.*
import coil3.compose.AsyncImage

@Composable internal fun WatchPage(video:VideoEntity,refreshing:Boolean,onMetadata:()->Unit,onSubtitles:()->Unit,audioOnly:Boolean,all:List<VideoEntity>,autoplay:Boolean,onAutoplay:(Boolean)->Unit,onChannel:(String)->Unit,onAudio:()->Unit,onPlay:(VideoEntity)->Unit,listState:LazyListState,expanded:Boolean,onExpand:()->Unit,modifier:Modifier=Modifier) {
    val c=SingularityTheme.colors;val context=LocalContext.current;val motion=SingularityTheme.motion.enabled
    val related=remember(all,video.id){all.filter{it.id!=video.id}.sortedBy{it.channelId!=video.channelId}.take(30)}
    val channelCount=remember(all,video.channelId){all.count{it.channelId==video.channelId}}
    val viewsLabel=stringResource(R.string.views_text,Formatters.views(video.viewCount))
    val meta=remember(video.id,video.viewCount,video.uploadDateMs,viewsLabel){buildList{
        if(video.viewCount>0)add(viewsLabel)
        if(video.uploadDateMs>0)add(DateUtils.getRelativeTimeSpanString(video.uploadDateMs,System.currentTimeMillis(),DateUtils.DAY_IN_MILLIS).toString())
    }.joinToString(" · ")}
    LazyColumn(modifier,state=listState,contentPadding=PaddingValues(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item(key="heading",contentType="heading") {
            Column(Modifier.padding(start=16.dp,end=16.dp,top=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(video.title,color=c.text,style=SingularityTheme.type.title)
                if(meta.isNotBlank())Text(meta,color=c.mid,style=SingularityTheme.type.caption)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    Tag(stringResource(R.string.player_offline))
                    if(video.height>0)Text("${video.height}p",color=c.mid,style=SingularityTheme.type.caption)
                    Text(Formatters.duration(video.durationMs)+" · "+Formatters.bytes(video.fileSizeBytes),color=c.low,style=SingularityTheme.type.caption)
                }
            }
        }
        item(key="channel",contentType="channel") {
            Row(Modifier.fillMaxWidth().clickable(role=Role.Button){onChannel(video.channelId)}.padding(horizontal=16.dp,vertical=2.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                AsyncImage(video.channelAvatarPath,null,Modifier.size(42.dp).clip(CircleShape).background(c.high),contentScale=ContentScale.Crop)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text(video.channelName,color=c.text,style=SingularityTheme.type.strong,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text(stringResource(R.string.player_channel_saved,channelCount),color=c.low,style=SingularityTheme.type.caption)
                }
                Icon(Icons.Rounded.ChevronRight,null,tint=c.mid,modifier=Modifier.size(20.dp))
            }
        }
        item(key="actions",contentType="actions") {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                if(video.likeCount>=0)WatchAction(Icons.Rounded.ThumbUp,stringResource(R.string.likes_text,Formatters.views(video.likeCount)),null)
                WatchAction(Icons.Rounded.Share,stringResource(R.string.share),{share(context,listOf(video))})
                if(video.kind=="VIDEO")WatchAction(if(audioOnly)Icons.Rounded.Videocam else Icons.Rounded.Headphones,stringResource(if(audioOnly)R.string.show_video else R.string.audio_only),onAudio,audioOnly)
                WatchAction(Icons.Rounded.ClosedCaption,stringResource(R.string.subtitle_download),onSubtitles)
                WatchAction(Icons.Rounded.Refresh,stringResource(if(refreshing)R.string.metadata_loading else R.string.metadata_download),if(refreshing)null else onMetadata)
                WatchAction(Icons.Rounded.OpenInNew,stringResource(R.string.open_youtube),{external(context,"https://www.youtube.com/watch?v=${video.ytId}")})
            }
        }
        if(video.description.isNotBlank() || video.removedDurationMs>0)item(key="description",contentType="description") {
            Column(Modifier.padding(horizontal=16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface).border(1.dp,c.hairline,RoundedCornerShape(14.dp)).animateContentSize(tween(if(motion)200 else 0)).padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                if(video.removedDurationMs>0)Text(stringResource(R.string.segments_removed,Formatters.duration(video.removedDurationMs)),color=c.accent,style=SingularityTheme.type.caption)
                if(video.description.isNotBlank()) {
                    FormattedDescription(video.description,expanded)
                    Text(stringResource(if(expanded)R.string.show_less else R.string.show_more),color=c.text,style=SingularityTheme.type.label,modifier=Modifier.heightIn(min=48.dp).fillMaxWidth().clickable(role=Role.Button,onClick=onExpand).wrapContentHeight())
                }
            }
        }
        item(key="next",contentType="next-heading") {
            Row(Modifier.padding(horizontal=16.dp).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(stringResource(R.string.up_next),color=c.text,style=SingularityTheme.type.small,modifier=Modifier.weight(1f))
                Text(stringResource(R.string.autoplay),color=c.mid,style=SingularityTheme.type.caption,modifier=Modifier.padding(end=8.dp))
                SingularitySwitch(autoplay,onAutoplay,stringResource(R.string.autoplay))
            }
        }
        items(related,key={it.id},contentType={"related-video"}) { next->
            Row(Modifier.fillMaxWidth().clickable(role=Role.Button){onPlay(next)}.padding(horizontal=16.dp,vertical=2.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Box(Modifier.width(144.dp).aspectRatio(16f/9).clip(RoundedCornerShape(10.dp)).background(c.high)) {
                    AsyncImage(next.thumbnailPath,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                    Text(Formatters.duration(next.durationMs),color=Color.White,style=SingularityTheme.type.caption,modifier=Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha=.8f),RoundedCornerShape(4.dp)).padding(horizontal=4.dp))
                    if(next.positionMs>0)AccretionProgressBar(PlayerTimeline.fraction(next.positionMs,next.durationMs),Modifier.align(Alignment.BottomCenter),height=2.dp)
                }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(next.title,color=c.text,style=SingularityTheme.type.label,maxLines=2,overflow=TextOverflow.Ellipsis)
                    Text(next.channelName,color=c.low,style=SingularityTheme.type.caption,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text(if(next.kind=="AUDIO")stringResource(R.string.audio)else listOfNotNull(next.height.takeIf{it>0}?.let{"${it}p"},Formatters.bytes(next.fileSizeBytes)).joinToString(" · "),color=c.low,style=SingularityTheme.type.caption)
                }
            }
        }
        if(related.isEmpty())item(key="no-next"){Text(stringResource(R.string.player_no_next),color=c.low,style=SingularityTheme.type.body,modifier=Modifier.padding(horizontal=16.dp))}
    }
}

@Composable private fun WatchAction(icon:ImageVector,label:String,onClick:(()->Unit)?,active:Boolean=false) {
    val c=SingularityTheme.colors
    Row(Modifier.heightIn(min=48.dp).clip(RoundedCornerShape(12.dp)).background(if(active)c.accent.copy(alpha=.18f)else c.high)
        .then(if(onClick!=null)Modifier.semantics{selected=active}.clickable(role=Role.Button,onClick=onClick)else Modifier).padding(horizontal=14.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Icon(icon,null,tint=if(active)c.accent else c.text,modifier=Modifier.size(18.dp))
        Text(label,color=if(active)c.accent else c.text,style=SingularityTheme.type.label)
    }
}
