package con.themralex.astrayt.core.designsystem

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import coil3.compose.AsyncImage
import kotlin.math.*
import kotlin.random.Random

@Composable fun VoidBackground(animate: Boolean=false, content: @Composable BoxScope.()->Unit) {
    val c=SingularityTheme.colors
    val stars=remember { val r=Random(72);List(70){ floatArrayOf(r.nextFloat(),r.nextFloat(),r.nextFloat()*1f+0.6f,r.nextFloat()*0.55f+0.15f) } }
    Box(Modifier.fillMaxSize().background(c.void).drawWithCache {
        val photon=Brush.radialGradient(listOf(c.photon.copy(alpha=.14f),Color.Transparent),Offset(size.width,0f),420.dp.toPx())
        val ember=Brush.radialGradient(listOf(c.ember.copy(alpha=.08f),Color.Transparent),Offset(0f,size.height),360.dp.toPx())
        onDrawBehind { drawRect(photon);drawRect(ember);stars.forEach { drawCircle(c.text.copy(alpha=it[3]),it[2].dp.toPx(),Offset(it[0]*size.width,it[1]*size.height)) } }
    },content=content)
}
@Composable fun BlackHoleHero(size: Dp=220.dp, animate: Boolean=true) {
    val c=SingularityTheme.colors
    val enabled=animate && SingularityTheme.motion.enabled
    val rotation=if(enabled) rememberInfiniteTransition(label="disk").animateFloat(0f,360f,infiniteRepeatable(tween(24000,easing=LinearEasing)),label="rotation").value else 0f
    Canvas(Modifier.size(size).clearAndSetSemantics {}) {
        val r=this.size.minDimension/3.5f; val center=this.center
        drawCircle(Brush.radialGradient(listOf(c.ember.copy(alpha=.25f),Color.Transparent),center,r*1.8f),r*1.8f)
        val diskTop=Offset(center.x-r*1.5f,center.y-r*.3f);val diskSize=Size(r*3,r*.6f)
        for((w,a) in listOf(18f to .18f,10f to .4f,4f to 1f)) drawArc(c.sweep,180f,180f,false,diskTop,diskSize,alpha=a,style=Stroke(w.dp.toPx()))
        drawCircle(c.void,r);drawCircle(c.corona.copy(alpha=.9f),r,style=Stroke(1.5.dp.toPx()))
        rotate(rotation*.025f) {
            for((w,a) in listOf(12f to .12f,6f to .4f,2f to 1f)) drawArc(c.sweep,180f,180f,false,Offset(center.x-r*1.22f,center.y-r*1.22f),Size(r*2.44f,r*2.44f),alpha=a,style=Stroke(w.dp.toPx()))
        }
        drawArc(c.sweep,0f,180f,false,Offset(center.x-r*1.08f,center.y-r*1.08f),Size(r*2.16f,r*2.16f),alpha=.55f,style=Stroke(2.dp.toPx()))
        for((w,a) in listOf(18f to .18f,10f to .4f,4f to 1f)) drawArc(c.sweep,0f,180f,false,diskTop,diskSize,alpha=a,style=Stroke(w.dp.toPx()))
    }
}
@Composable fun AccretionRing(progress: Float, size: Dp=56.dp, state: String="active", modifier: Modifier=Modifier) {
    val c=SingularityTheme.colors
    val rotation=if(state=="indeterminate" && SingularityTheme.motion.enabled) rememberInfiniteTransition(label="progress").animateFloat(0f,360f,infiniteRepeatable(tween(1100,easing=LinearEasing)),label="spin").value else -90f
    Box(modifier.size(size).semantics { progressBarRangeInfo=ProgressBarRangeInfo(progress.coerceIn(0f,1f),0f..1f) },contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(4.dp)) {
            val width=5.dp.toPx();val arc=if(state=="indeterminate")90f else progress.coerceIn(0f,1f)*360f
            drawArc(c.hairline,0f,360f,false,style=Stroke(width))
            val brush=when(state){"paused"->Brush.linearGradient(listOf(c.low,c.low));"error"->Brush.linearGradient(listOf(c.error,c.error));"done"->Brush.linearGradient(listOf(c.success,c.success));else->c.sweep}
            drawArc(brush,rotation,arc,false,style=Stroke(width,cap=StrokeCap.Round))
        }
        Text(when(state){"paused"->"Ⅱ";"error"->"!";"done"->"✓";"indeterminate"->"";else->"${(progress*100).toInt()}"},color=c.text,style=SingularityTheme.type.label.copy(fontFeatureSettings="tnum"))
    }
}
@Composable fun AccretionProgressBar(progress: Float,modifier: Modifier=Modifier,height: Dp=6.dp) {
    val c=SingularityTheme.colors
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(c.hairline)) { Box(Modifier.fillMaxWidth(progress.coerceIn(0f,1f)).fillMaxHeight().background(c.linear)) }
}
@Composable fun SingularityButton(text: String,onClick: ()->Unit,modifier: Modifier=Modifier,variant: String="primary",enabled: Boolean=true,loading: Boolean=false) {
    val c=SingularityTheme.colors;val shape=CircleShape
    val bg=when(variant){"primary"->c.linear;"secondary"->Brush.linearGradient(listOf(c.high,c.high));else->Brush.linearGradient(listOf(Color.Transparent,Color.Transparent))}
    Box(modifier.defaultMinSize(minHeight=52.dp,minWidth=48.dp).alpha(if(enabled)1f else .35f).clip(shape).background(bg).then(if(variant=="secondary")Modifier.border(1.dp,c.border,shape)else Modifier).clickable(enabled=enabled && !loading,onClick=onClick).padding(horizontal=20.dp,vertical=12.dp),contentAlignment=Alignment.Center) {
        if(loading) AccretionRing(0f,24.dp,"indeterminate") else Text(text,color=if(variant=="primary")c.void else if(variant=="tertiary")c.accent else c.text,style=SingularityTheme.type.small,textAlign=TextAlign.Center)
    }
}
@Composable fun SingularityIcon(icon: ImageVector,description: String,onClick: ()->Unit,modifier: Modifier=Modifier) {
    val c=SingularityTheme.colors
    Box(modifier.size(48.dp).clip(CircleShape).background(c.high).border(1.dp,c.border,CircleShape).clickable(onClick=onClick),contentAlignment=Alignment.Center){Icon(icon,description,tint=c.text,modifier=Modifier.size(24.dp))}
}
@Composable fun GlassCard(modifier: Modifier=Modifier,content: @Composable ColumnScope.()->Unit) {
    val c=SingularityTheme.colors
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(c.high.copy(alpha=.92f)).border(1.dp,c.border,RoundedCornerShape(22.dp)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
}
@Composable fun LinkField(value: String,onValueChange: (String)->Unit,hint: String,onGo: ()->Unit,modifier: Modifier=Modifier,error: String?=null,loading: Boolean=false) {
    val c=SingularityTheme.colors
    Column(modifier) {
        Row(Modifier.fillMaxWidth().heightIn(min=60.dp).clip(RoundedCornerShape(22.dp)).background(c.high).border(1.dp,if(error!=null)Brush.linearGradient(listOf(c.error,c.error))else c.border,RoundedCornerShape(22.dp)).padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Rounded.Link,null,tint=c.accent)
            BasicTextField(value,onValueChange,modifier=Modifier.weight(1f).padding(horizontal=12.dp),textStyle=SingularityTheme.type.body.copy(color=c.text),singleLine=true,cursorBrush=SolidColor(c.accent),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Go),keyboardActions=KeyboardActions(onGo={onGo()}),decorationBox={inner->if(value.isEmpty())Text(hint,color=c.low,style=SingularityTheme.type.body);inner()})
            if(loading)AccretionRing(0f,28.dp,"indeterminate") else if(value.isNotEmpty())Icon(Icons.Rounded.Close,androidx.compose.ui.res.stringResource(con.themralex.astrayt.R.string.clear),Modifier.size(48.dp).clickable{onValueChange("")}.padding(12.dp),tint=c.mid)
        }
        error?.let{Text(it,color=c.error,style=SingularityTheme.type.label,modifier=Modifier.padding(8.dp))}
    }
}
@Composable fun MetadataCard(title: String,channel: String,thumbnail: String?,duration: String,modifier: Modifier=Modifier) {
    val c=SingularityTheme.colors
    Column(modifier,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Box {
            AsyncImage(thumbnail,null,Modifier.fillMaxWidth().aspectRatio(16f/9).clip(RoundedCornerShape(16.dp)).background(c.high),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
            Text(duration,color=c.text,style=SingularityTheme.type.caption,modifier=Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(CircleShape).background(c.void.copy(alpha=.7f)).padding(6.dp))
        }
        Text(title,color=c.text,style=SingularityTheme.type.small,maxLines=2,overflow=TextOverflow.Ellipsis)
        Text(channel,color=c.mid,style=SingularityTheme.type.label)
    }
}
@Composable fun SegmentedToggle(labels: List<String>,selected: Int,onSelect: (Int)->Unit) {
    val c=SingularityTheme.colors
    Row(Modifier.fillMaxWidth().clip(CircleShape).background(c.high).padding(4.dp)) {
        labels.forEachIndexed{i,label->Box(Modifier.weight(1f).heightIn(min=44.dp).clip(CircleShape).background(if(i==selected)c.accent.copy(alpha=.18f)else Color.Transparent).then(if(i==selected)Modifier.border(1.dp,c.accent,CircleShape)else Modifier).clickable{onSelect(i)}.padding(8.dp),contentAlignment=Alignment.Center){Text(label,color=if(i==selected)c.accent else c.mid,style=SingularityTheme.type.label)}}
    }
}
@Composable fun QualityChip(title: String,subtitle: String,selected: Boolean,onClick: ()->Unit) {
    val c=SingularityTheme.colors;val shape=RoundedCornerShape(16.dp)
    Column(Modifier.widthIn(min=92.dp).heightIn(min=52.dp).clip(shape).background(if(selected)c.accent.copy(alpha=.14f)else c.high).border(1.dp,if(selected)c.linear else c.border,shape).clickable(onClick=onClick).padding(12.dp)) {Text(title,color=c.text,style=SingularityTheme.type.small);Text(subtitle,color=c.mid,style=SingularityTheme.type.caption)}
}
@Composable fun LanguageRow(name: String,subtitle: String,code: String,selected: Boolean,onClick: ()->Unit) {
    val c=SingularityTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min=60.dp).clip(RoundedCornerShape(16.dp)).background(if(selected)c.accent.copy(alpha=.1f)else Color.Transparent).clickable(onClick=onClick).padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).border(1.dp,c.sweep,CircleShape),contentAlignment=Alignment.Center){Text(code.take(2).uppercase(),color=c.text,style=SingularityTheme.type.caption)}
        Column(Modifier.weight(1f)){Text(name,color=c.text,style=SingularityTheme.type.strong);Text(subtitle,color=c.mid,style=SingularityTheme.type.caption)}
        Box(Modifier.size(24.dp).border(2.dp,if(selected)c.accent else c.low,CircleShape).padding(5.dp).then(if(selected)Modifier.background(c.linear,CircleShape)else Modifier))
    }
}
@Composable fun Tag(text: String,color: Color=SingularityTheme.colors.corona) {Text(text,color=color,style=SingularityTheme.type.caption,modifier=Modifier.clip(CircleShape).background(color.copy(alpha=.14f)).padding(horizontal=8.dp,vertical=4.dp))}
@Composable fun FloatingNavBar(labels: List<String>,icons: List<ImageVector>,selected: Int,onSelect: (Int)->Unit,modifier: Modifier=Modifier) {
    val c=SingularityTheme.colors
    Row(modifier.clip(CircleShape).background(c.high.copy(alpha=.94f)).border(1.dp,c.border,CircleShape).padding(8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed{i,label->Row(Modifier.heightIn(min=52.dp).weight(if(i==selected)1.8f else 1f).clip(CircleShape).background(if(i==selected)c.accent.copy(alpha=.16f)else Color.Transparent).clickable{onSelect(i)}.padding(horizontal=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){Icon(icons[i],label,tint=if(i==selected)c.accent else c.low);if(i==selected){Spacer(Modifier.width(6.dp));Text(label,color=c.accent,style=SingularityTheme.type.label,maxLines=1)}}}
    }
}
@Composable fun VideoCard(title: String,meta: String,thumbnail: String?,duration: String,progress: Float=0f,onClick: ()->Unit,onMore: ()->Unit,modifier: Modifier=Modifier) {
    Column(modifier.clickable(onClick=onClick),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Box { MetadataCard(title,meta,thumbnail,duration);if(progress>0)AccretionProgressBar(progress,Modifier.padding(top=1.dp),3.dp) }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){SingularityIcon(Icons.Rounded.MoreVert,androidx.compose.ui.res.stringResource(con.themralex.astrayt.R.string.options),onMore)}
    }
}
@Composable fun VideoCardCompact(title: String,meta: String,thumbnail: String?,onClick: ()->Unit) {
    val c=SingularityTheme.colors
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        AsyncImage(thumbnail,null,Modifier.width(148.dp).aspectRatio(16f/9).clip(RoundedCornerShape(16.dp)).background(c.high),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
        Column(Modifier.weight(1f)){Text(title,color=c.text,style=SingularityTheme.type.strong,maxLines=2,overflow=TextOverflow.Ellipsis);Text(meta,color=c.mid,style=SingularityTheme.type.caption)}
    }
}
@Composable fun VideoCardShelf(title: String,meta: String,thumbnail: String?,duration: String,onClick: ()->Unit){MetadataCard(title,meta,thumbnail,duration,Modifier.width(240.dp).clickable(onClick=onClick))}
@Composable fun FilterChipRow(labels: List<String>,selected: Int,onSelect: (Int)->Unit) {
    val c=SingularityTheme.colors
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){labels.forEachIndexed{i,s->Box(Modifier.heightIn(min=48.dp).clip(RoundedCornerShape(12.dp)).background(if(i==selected)c.linear else Brush.linearGradient(listOf(c.high,c.high))).clickable{onSelect(i)}.padding(horizontal=16.dp,vertical=12.dp)){Text(s,color=if(i==selected)c.void else c.mid,style=SingularityTheme.type.label)}}}
}
@Composable fun SingularitySheet(onDismiss: ()->Unit,content: @Composable ColumnScope.()->Unit) {
    Dialog(onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)){Box(Modifier.fillMaxSize().background(SingularityTheme.colors.void.copy(alpha=.6f)).clickable(onClick=onDismiss),contentAlignment=Alignment.BottomCenter){Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart=28.dp,topEnd=28.dp)).background(SingularityTheme.colors.high).clickable(enabled=false){}.padding(24.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(16.dp)){Box(Modifier.align(Alignment.CenterHorizontally).size(36.dp,4.dp).background(SingularityTheme.colors.low,CircleShape));content()}}}
}
@Composable fun SingularityDialog(title: String,body: String,confirm: String,cancel: String,onConfirm: ()->Unit,onDismiss: ()->Unit) {
    Dialog(onDismiss){GlassCard{Text(title,color=SingularityTheme.colors.text,style=SingularityTheme.type.title);Text(body,color=SingularityTheme.colors.mid,style=SingularityTheme.type.body);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){SingularityButton(cancel,onDismiss,Modifier.weight(1f),"tertiary");SingularityButton(confirm,onConfirm,Modifier.weight(1f))}}}
}
@Composable fun Toast(text: String,modifier: Modifier=Modifier,action: String?=null,onAction: ()->Unit={}) {
    val c=SingularityTheme.colors
    Row(modifier.clip(CircleShape).background(c.top).padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){Box(Modifier.size(8.dp).background(c.success,CircleShape));Text(text,color=c.text,style=SingularityTheme.type.label,modifier=Modifier.weight(1f));action?.let{Text(it,color=c.accent,modifier=Modifier.clickable(onClick=onAction).padding(8.dp))}}
}
@Composable fun Skeleton(modifier: Modifier=Modifier) {Box(modifier.clip(RoundedCornerShape(16.dp)).background(SingularityTheme.colors.high))}
@Composable fun EmptyState(title: String,body: String,button: String?=null,onAction: ()->Unit={}) {
    Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)){BlackHoleHero(120.dp,false);Text(title,color=SingularityTheme.colors.text,style=SingularityTheme.type.title,textAlign=TextAlign.Center);Text(body,color=SingularityTheme.colors.mid,style=SingularityTheme.type.body,textAlign=TextAlign.Center);button?.let{SingularityButton(it,onAction)}}
}
@Composable fun SingularitySwitch(checked: Boolean,onChange: (Boolean)->Unit,label: String) {
    val c=SingularityTheme.colors
    Box(Modifier.size(56.dp,48.dp).semantics { contentDescription=label;toggleableState=if(checked)androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off }.clickable{onChange(!checked)},contentAlignment=Alignment.Center){Box(Modifier.size(52.dp,30.dp).clip(CircleShape).background(if(checked)c.linear else Brush.linearGradient(listOf(c.top,c.top))).padding(4.dp)){Box(Modifier.size(22.dp).align(if(checked)Alignment.CenterEnd else Alignment.CenterStart).background(c.text,CircleShape))}}
}
@Composable fun SingularitySlider(value: Int,range: IntRange,label: String,onChange: (Int)->Unit) {
    val c=SingularityTheme.colors
    Column {Text("$label · $value",color=c.text,style=SingularityTheme.type.label);BoxWithConstraints(Modifier.fillMaxWidth().height(48.dp).semantics{contentDescription=label;progressBarRangeInfo=ProgressBarRangeInfo(value.toFloat(),range.first.toFloat()..range.last.toFloat(),range.count()-2)}) {
        val width=constraints.maxWidth.toFloat();val fraction=(value-range.first).toFloat()/(range.last-range.first)
        Canvas(Modifier.fillMaxSize().pointerInput(range){detectTapGestures{offset->onChange((range.first+offset.x/width*(range.last-range.first)).roundToInt().coerceIn(range))}}){drawLine(c.hairline,Offset(0f,center.y),Offset(size.width,center.y),6.dp.toPx(),StrokeCap.Round);drawLine(c.accent,Offset(0f,center.y),Offset(size.width*fraction,center.y),6.dp.toPx(),StrokeCap.Round);drawCircle(c.accent,10.dp.toPx(),Offset((size.width*fraction).coerceIn(10.dp.toPx(),size.width-10.dp.toPx()),center.y))}
    }}
}
@Composable fun SettingsRow(icon: ImageVector,title: String,subtitle: String="",value: String="",onClick: ()->Unit={},trailing: (@Composable ()->Unit)?=null) {
    val c=SingularityTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min=64.dp).clickable(onClick=onClick).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.accent.copy(alpha=.14f)),contentAlignment=Alignment.Center){Icon(icon,null,tint=c.accent)}
        Column(Modifier.weight(1f)){Text(title,color=c.text,style=SingularityTheme.type.strong);if(subtitle.isNotEmpty())Text(subtitle,color=c.low,style=SingularityTheme.type.label)}
        if(trailing!=null)trailing() else Text(value,color=c.mid,style=SingularityTheme.type.label)
    }
}
@Composable fun SectionHeader(text: String) {Row(Modifier.padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){Box(Modifier.size(16.dp,2.dp).background(SingularityTheme.colors.linear));Text(text.uppercase(),color=SingularityTheme.colors.low,style=SingularityTheme.type.eyebrow)}}
