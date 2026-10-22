package con.themralex.astrayt.core.designsystem

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.foundation.selection.*
import androidx.compose.ui.focus.onFocusChanged
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
    Box(Modifier.fillMaxSize().background(c.void),content=content)
}

@Composable fun BlackHoleHero(size: Dp=220.dp, animate: Boolean=true) {
    val c=SingularityTheme.colors
    val enabled=animate && SingularityTheme.motion.enabled
    val breath=if(enabled)rememberInfiniteTransition(label="horizon").animateFloat(.72f,1f,infiniteRepeatable(tween(6000,easing=FastOutSlowInEasing),RepeatMode.Reverse),label="light").value else .85f
    Canvas(Modifier.size(size).clearAndSetSemantics {}) {
        val r=this.size.minDimension*.175f
        val center=this.center
        drawCircle(Brush.radialGradient(listOf(c.accent.copy(alpha=.10f*breath),Color.Transparent),center,r*2.05f),r*2.05f)
        // Fine orbital lines and a continuous lens, with no filled neon surfaces.
        rotate(-14f) {
            repeat(18){i->
                val spread=1f+i*.032f
                val w=r*3.65f*spread;val h=r*.58f*spread
                drawOval(c.accent.copy(alpha=(.17f-i*.007f).coerceAtLeast(.02f)*breath),Offset(center.x-w/2,center.y-h/2),Size(w,h),style=Stroke((if(i<3)1.1f else .45f).dp.toPx()))
            }
            drawCircle(c.void,r)
            repeat(9){i->drawCircle(c.accent.copy(alpha=(.13f-i*.012f)*breath),r+i*1.5.dp.toPx(),style=Stroke(1.dp.toPx()))}
            drawCircle(c.corona.copy(alpha=.9f),r,style=Stroke(.85f.dp.toPx()))
            drawArc(Brush.verticalGradient(listOf(c.corona,c.accent.copy(alpha=.12f))),185f,170f,false,Offset(center.x-r*1.18f,center.y-r*1.18f),Size(r*2.36f,r*2.36f),style=Stroke(1.5f.dp.toPx()))
            repeat(8){i->val w=r*(3.7f+i*.11f);val h=r*(.58f+i*.035f);drawArc(c.corona.copy(alpha=(.55f-i*.06f)*breath),0f,180f,false,Offset(center.x-w/2,center.y-h/2),Size(w,h),style=Stroke(.7f.dp.toPx()))}
        }
    }
}

@Composable fun AccretionRing(progress: Float, size: Dp=56.dp, state: String="active", modifier: Modifier=Modifier) {
    val c=SingularityTheme.colors
    val rotation=if(state=="indeterminate" && SingularityTheme.motion.enabled) rememberInfiniteTransition(label="progress").animateFloat(0f,360f,infiniteRepeatable(tween(1100,easing=LinearEasing)),label="spin").value else -90f
    Box(modifier.size(size).semantics { progressBarRangeInfo=ProgressBarRangeInfo(progress.coerceIn(0f,1f),0f..1f) },contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(4.dp)) {
            val width=2.dp.toPx();val arc=if(state=="indeterminate")90f else progress.coerceIn(0f,1f)*360f
            drawArc(c.hairline,0f,360f,false,style=Stroke(width))
            val brush=when(state){"paused"->Brush.linearGradient(listOf(c.low,c.low));"error"->Brush.linearGradient(listOf(c.error,c.error));"done"->Brush.linearGradient(listOf(c.success,c.success));else->Brush.linearGradient(listOf(c.accent,c.corona))}
            drawArc(brush,rotation,arc,false,style=Stroke(width,cap=StrokeCap.Round))
        }
        Text(when(state){"paused"->"Ⅱ";"error"->"!";"done"->"✓";"indeterminate"->"";else->"${(progress*100).toInt()}"},color=c.text,style=SingularityTheme.type.label.copy(fontFeatureSettings="tnum"))
    }
}
@Composable fun AccretionProgressBar(progress: Float,modifier: Modifier=Modifier,height: Dp=3.dp) {
    val c=SingularityTheme.colors
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(c.hairline)) { Box(Modifier.fillMaxWidth(progress.coerceIn(0f,1f)).fillMaxHeight().background(c.linear)) }
}
@Composable fun SingularityButton(text: String,onClick: ()->Unit,modifier: Modifier=Modifier,variant: String="primary",enabled: Boolean=true,loading: Boolean=false) {
    val c=SingularityTheme.colors;val shape=RoundedCornerShape(14.dp)
    val interactions=remember{MutableInteractionSource()};val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed).98f else 1f,label="press")
    val foreground=if(variant=="primary")c.void else if(variant=="tertiary")c.accent else c.text
    Box(modifier.graphicsLayer{scaleX=scale;scaleY=scale}.defaultMinSize(minHeight=52.dp,minWidth=48.dp).alpha(if(enabled)1f else .4f).clip(shape)
        .background(when(variant){"primary"->c.accent;"secondary"->c.high;else->Color.Transparent})
        .then(if(variant=="secondary")Modifier.border(1.dp,c.hairline,shape)else Modifier)
        .clickable(interactionSource=interactions,indication=LocalIndication.current,enabled=enabled && !loading,role=Role.Button,onClick=onClick).padding(horizontal=18.dp,vertical=14.dp),contentAlignment=Alignment.Center) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            if(loading)Canvas(Modifier.size(16.dp)){drawArc(foreground,0f,270f,false,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))}
            Text(text,color=foreground,style=SingularityTheme.type.strong,textAlign=TextAlign.Center)
        }
    }
}

@Composable fun SingularityIcon(icon: ImageVector,description: String,onClick: ()->Unit,modifier: Modifier=Modifier) {
    Box(modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).clickable(role=Role.Button,onClick=onClick),contentAlignment=Alignment.Center){Icon(icon,description,tint=SingularityTheme.colors.text,modifier=Modifier.size(22.dp))}
}

@Composable fun GlassCard(modifier: Modifier=Modifier,content: @Composable ColumnScope.()->Unit) {
    val c=SingularityTheme.colors;val shape=RoundedCornerShape(20.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp,c.border,shape).padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
}

@Composable fun LinkField(value: String,onValueChange: (String)->Unit,hint: String,onGo: ()->Unit,modifier: Modifier=Modifier,error: String?=null,loading: Boolean=false) {
    val c=SingularityTheme.colors;var focused by remember{mutableStateOf(false)};val shape=RoundedCornerShape(14.dp)
    Column(modifier) {
        Row(Modifier.fillMaxWidth().heightIn(min=58.dp).clip(shape).background(c.abyss).border(1.dp,if(error!=null)c.error else if(focused)c.accent else c.strong,shape).padding(start=16.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Rounded.Link,null,tint=c.mid,modifier=Modifier.size(20.dp))
            BasicTextField(value,onValueChange,modifier=Modifier.weight(1f).padding(horizontal=12.dp,vertical=16.dp).onFocusChanged{focused=it.isFocused},textStyle=SingularityTheme.type.body.copy(color=c.text),singleLine=true,cursorBrush=SolidColor(c.accent),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Go),keyboardActions=KeyboardActions(onGo={onGo()}),decorationBox={inner->if(value.isEmpty())Text(hint,color=c.low,style=SingularityTheme.type.body,maxLines=1,overflow=TextOverflow.Ellipsis);inner()})
            if(loading)AccretionRing(0f,36.dp,"indeterminate") else if(value.isNotEmpty())SingularityIcon(Icons.Rounded.Close,androidx.compose.ui.res.stringResource(con.themralex.astrayt.R.string.clear),{onValueChange("")})
        }
        error?.let{Text(it,color=c.error,style=SingularityTheme.type.label,modifier=Modifier.padding(top=8.dp))}
    }
}

@Composable fun MetadataCard(title: String,channel: String,thumbnail: String?,duration: String,modifier: Modifier=Modifier,watchedProgress:Float=0f,highlight:String="") {
    val c=SingularityTheme.colors
    Column(modifier,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Box {
            AsyncImage(thumbnail,null,Modifier.fillMaxWidth().aspectRatio(16f/9).clip(RoundedCornerShape(16.dp)).background(c.high),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
            if(watchedProgress>0)AccretionProgressBar(watchedProgress,Modifier.align(Alignment.BottomCenter),3.dp)
            Text(duration,color=c.text,style=SingularityTheme.type.caption,modifier=Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(CircleShape).background(c.void.copy(alpha=.7f)).padding(6.dp))
        }
        val styledTitle=remember(title,highlight,c.corona){buildAnnotatedString{append(title);highlight.split(' ').filter{it.isNotBlank()}.forEach{token->var start=title.indexOf(token,ignoreCase=true);while(start>=0){addStyle(SpanStyle(color=c.corona),start,start+token.length);start=title.indexOf(token,start+token.length,ignoreCase=true)}}}}
        Text(styledTitle,color=c.text,style=SingularityTheme.type.small,maxLines=2,overflow=TextOverflow.Ellipsis)
        Text(channel,color=c.mid,style=SingularityTheme.type.label)
    }
}
@Composable fun SegmentedToggle(labels: List<String>,selected: Int,onSelect: (Int)->Unit) {
    val c=SingularityTheme.colors
    Row(Modifier.fillMaxWidth().selectableGroup().clip(RoundedCornerShape(12.dp)).background(c.abyss).padding(4.dp)) {
        labels.forEachIndexed{i,label->Box(Modifier.weight(1f).heightIn(min=48.dp).clip(RoundedCornerShape(9.dp)).background(if(i==selected)c.top else Color.Transparent).selectable(i==selected,role=Role.Tab){onSelect(i)}.padding(10.dp),contentAlignment=Alignment.Center){Text(label,color=if(i==selected)c.text else c.mid,style=SingularityTheme.type.strong)}}
    }
}

@Composable fun QualityChip(title: String,subtitle: String,selected: Boolean,onClick: ()->Unit,modifier:Modifier=Modifier) {
    val c=SingularityTheme.colors;val shape=RoundedCornerShape(10.dp)
    Column(modifier.heightIn(min=52.dp).clip(shape).background(if(selected)c.accent.copy(alpha=.10f)else c.abyss).border(1.dp,if(selected)c.accent else c.hairline,shape).selectable(selected,role=Role.RadioButton,onClick=onClick).padding(horizontal=12.dp,vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(title,color=if(selected)c.corona else c.text,style=SingularityTheme.type.label,textAlign=TextAlign.Center)
        if(subtitle.isNotBlank())Text(subtitle,color=c.mid,style=SingularityTheme.type.caption,textAlign=TextAlign.Center)
    }
}

@Composable fun LanguageRow(name: String,subtitle: String,code: String,selected: Boolean,onClick: ()->Unit) {
    val c=SingularityTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min=60.dp).clip(RoundedCornerShape(16.dp)).background(if(selected)c.accent.copy(alpha=.1f)else Color.Transparent).clickable(onClick=onClick).padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(36.dp).background(c.top,RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center){Text(code.take(2).uppercase(),color=c.text,style=SingularityTheme.type.caption)}
        Column(Modifier.weight(1f)){Text(name,color=c.text,style=SingularityTheme.type.strong);Text(subtitle,color=c.mid,style=SingularityTheme.type.caption)}
        Box(Modifier.size(24.dp).border(2.dp,if(selected)c.accent else c.low,CircleShape).padding(5.dp).then(if(selected)Modifier.background(c.accent,CircleShape)else Modifier))
    }
}
@Composable fun Tag(text: String,color: Color=SingularityTheme.colors.corona) {Text(text,color=color,style=SingularityTheme.type.caption,modifier=Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha=.09f)).padding(horizontal=8.dp,vertical=4.dp))}
@Composable fun FloatingNavBar(labels: List<String>,icons: List<ImageVector>,selected: Int,onSelect: (Int)->Unit,modifier: Modifier=Modifier) {
    val c=SingularityTheme.colors
    Row(modifier.fillMaxWidth().background(c.void).drawBehind{drawLine(c.hairline,Offset.Zero,Offset(size.width,0f),1.dp.toPx())}.selectableGroup().padding(top=8.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed{i,label->Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).selectable(i==selected,role=Role.Tab){onSelect(i)}.padding(vertical=10.dp,horizontal=2.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Icon(icons[i],null,tint=if(i==selected)c.accent else c.low,modifier=Modifier.size(22.dp))
            Text(label,color=if(i==selected)c.text else c.low,style=SingularityTheme.type.caption,textAlign=TextAlign.Center)
        }}
    }
}

@Composable fun VideoCard(title: String,meta: String,thumbnail: String?,duration: String,progress: Float=0f,onClick: ()->Unit,onMore: ()->Unit,modifier: Modifier=Modifier,onLongClick: ()->Unit={},highlight:String="") {
    Box(modifier.clip(RoundedCornerShape(16.dp)).combinedClickable(onClick=onClick,onLongClick=onLongClick)) {
        MetadataCard(title,meta,thumbnail,duration,watchedProgress=progress,highlight=highlight)
        SingularityIcon(Icons.Rounded.MoreVert,androidx.compose.ui.res.stringResource(con.themralex.astrayt.R.string.options),onMore,Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha=.6f),RoundedCornerShape(12.dp)))
    }
}

@Composable fun VideoCardCompact(title: String,meta: String,thumbnail: String?,onClick: ()->Unit) {
    val c=SingularityTheme.colors
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        AsyncImage(thumbnail,null,Modifier.width(112.dp).aspectRatio(16f/9).clip(RoundedCornerShape(16.dp)).background(c.high),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
        Column(Modifier.weight(1f)){Text(title,color=c.text,style=SingularityTheme.type.strong,maxLines=2,overflow=TextOverflow.Ellipsis);Text(meta,color=c.mid,style=SingularityTheme.type.caption)}
    }
}
@Composable fun VideoCardShelf(title: String,meta: String,thumbnail: String?,duration: String,onClick: ()->Unit){MetadataCard(title,meta,thumbnail,duration,Modifier.width(240.dp).clickable(onClick=onClick))}
@Composable fun FilterChipRow(labels: List<String>,selected: Int,onSelect: (Int)->Unit) {
    val c=SingularityTheme.colors
    Row(Modifier.horizontalScroll(rememberScrollState()).selectableGroup(),horizontalArrangement=Arrangement.spacedBy(8.dp)){labels.forEachIndexed{i,s->Box(Modifier.heightIn(min=48.dp).clip(RoundedCornerShape(10.dp)).background(if(i==selected)c.text else c.surface).selectable(i==selected,role=Role.Tab){onSelect(i)}.padding(horizontal=16.dp,vertical=12.dp)){Text(s,color=if(i==selected)c.void else c.mid,style=SingularityTheme.type.label)}}}
}

@Composable fun SingularitySheet(onDismiss: ()->Unit,content: @Composable ColumnScope.()->Unit) {
    val c=SingularityTheme.colors
    Dialog(onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)){BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.72f)).clickable(onClick=onDismiss),contentAlignment=Alignment.BottomCenter){
        Column(Modifier.widthIn(max=600.dp).fillMaxWidth().heightIn(max=maxHeight*.9f).clip(RoundedCornerShape(topStart=24.dp,topEnd=24.dp)).background(c.surface).clickable(interactionSource=remember{MutableInteractionSource()},indication=null){}.verticalScroll(rememberScrollState()).padding(24.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom=12.dp).size(32.dp,3.dp).background(c.low,RoundedCornerShape(2.dp)));content()
        }
    }}
}

@Composable fun SingularityDialog(title: String,body: String,confirm: String,cancel: String,onConfirm: ()->Unit,onDismiss: ()->Unit) {
    Dialog(onDismiss){GlassCard{Text(title,color=SingularityTheme.colors.text,style=SingularityTheme.type.title);Text(body,color=SingularityTheme.colors.mid,style=SingularityTheme.type.body);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){SingularityButton(cancel,onDismiss,Modifier.weight(1f),"tertiary");SingularityButton(confirm,onConfirm,Modifier.weight(1f))}}}
}
@Composable fun Toast(text: String,modifier: Modifier=Modifier,action: String?=null,onAction: ()->Unit={}) {
    val c=SingularityTheme.colors
    Row(modifier.clip(RoundedCornerShape(14.dp)).background(c.top).padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){Box(Modifier.size(8.dp).background(c.success,CircleShape));Text(text,color=c.text,style=SingularityTheme.type.label,modifier=Modifier.weight(1f));action?.let{Text(it,color=c.accent,modifier=Modifier.clickable(onClick=onAction).padding(8.dp))}}
}
@Composable fun Skeleton(modifier: Modifier=Modifier) {
    val c=SingularityTheme.colors
    val enabled=SingularityTheme.motion.enabled
    val phase=if(enabled)rememberInfiniteTransition(label="skeleton").animateFloat(-1f,2f,infiniteRepeatable(tween(1200,easing=LinearEasing)),label="shimmer") else remember{mutableFloatStateOf(0f)}
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(c.high).drawWithCache {
        val brush=Brush.horizontalGradient(listOf(Color.Transparent,c.top.copy(alpha=.7f),Color.Transparent),0f,size.width)
        onDrawBehind{if(enabled)translate(left=size.width*phase.value){drawRect(brush)}}
    })
}
@Composable fun EmptyState(title: String,body: String,button: String?=null,onAction: ()->Unit={}) {
    Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)){BlackHoleHero(120.dp,false);Text(title,color=SingularityTheme.colors.text,style=SingularityTheme.type.title,textAlign=TextAlign.Center);Text(body,color=SingularityTheme.colors.mid,style=SingularityTheme.type.body,textAlign=TextAlign.Center);button?.let{SingularityButton(it,onAction)}}
}
@Composable fun SingularitySwitch(checked: Boolean,onChange: (Boolean)->Unit,label: String) {
    val c=SingularityTheme.colors
    val haptics=LocalHapticFeedback.current
    Box(Modifier.size(56.dp,48.dp).semantics { contentDescription=label;toggleableState=if(checked)androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off }.toggleable(value=checked,role=Role.Switch,onValueChange={haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.ContextClick);onChange(it)}),contentAlignment=Alignment.Center){Box(Modifier.size(52.dp,30.dp).clip(CircleShape).background(Brush.linearGradient(if(checked)listOf(c.accent,c.accent)else listOf(c.top,c.top))).padding(4.dp)){Box(Modifier.size(22.dp).align(if(checked)Alignment.CenterEnd else Alignment.CenterStart).background(c.text,CircleShape))}}
}
@Composable fun SingularitySlider(value: Int,range: IntRange,label: String,showLabel:Boolean=true,onChange: (Int)->Unit) {
    val c=SingularityTheme.colors
    Column {if(showLabel)Text("$label · $value",color=c.text,style=SingularityTheme.type.label);BoxWithConstraints(Modifier.fillMaxWidth().height(48.dp).semantics{contentDescription=label;progressBarRangeInfo=ProgressBarRangeInfo(value.toFloat(),range.first.toFloat()..range.last.toFloat(),range.count()-2);setProgress{target->onChange(target.roundToInt().coerceIn(range));true}}) {
        val width=constraints.maxWidth.toFloat();val fraction=(value-range.first).toFloat()/(range.last-range.first)
        Canvas(Modifier.fillMaxSize().pointerInput(range){detectTapGestures{offset->onChange((range.first+offset.x/width*(range.last-range.first)).roundToInt().coerceIn(range))}}.pointerInput(range){detectHorizontalDragGestures{change,_->change.consume();onChange((range.first+change.position.x/width*(range.last-range.first)).roundToInt().coerceIn(range))}}){drawLine(c.hairline,Offset(0f,center.y),Offset(size.width,center.y),6.dp.toPx(),StrokeCap.Round);drawLine(c.accent,Offset(0f,center.y),Offset(size.width*fraction,center.y),6.dp.toPx(),StrokeCap.Round);drawCircle(c.accent,10.dp.toPx(),Offset((size.width*fraction).coerceIn(10.dp.toPx(),size.width-10.dp.toPx()),center.y))}
    }}
}
@Composable fun SettingsRow(icon: ImageVector,title: String,subtitle: String="",value: String="",onClick: (()->Unit)?=null,trailing: (@Composable ()->Unit)?=null) {
    val c=SingularityTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min=64.dp).clip(RoundedCornerShape(10.dp)).then(if(onClick!=null)Modifier.clickable(onClick=onClick)else Modifier).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        Icon(icon,null,tint=c.mid,modifier=Modifier.size(22.dp))
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(title,color=c.text,style=SingularityTheme.type.body);if(subtitle.isNotEmpty())Text(subtitle,color=c.low,style=SingularityTheme.type.caption)}
        if(trailing!=null)trailing() else Column(horizontalAlignment=Alignment.End,modifier=Modifier.widthIn(max=110.dp)) {if(value.isNotBlank())Text(value,color=c.mid,style=SingularityTheme.type.label,textAlign=TextAlign.End);if(onClick!=null)Icon(Icons.Rounded.ChevronRight,null,tint=c.low,modifier=Modifier.size(18.dp))}
    }
}

@Composable fun SectionHeader(text: String) {
    Text(text.uppercase(),color=SingularityTheme.colors.mid,style=SingularityTheme.type.eyebrow,modifier=Modifier.padding(top=12.dp,bottom=4.dp))
}
