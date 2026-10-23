package con.themralex.astrayt.feature

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import con.themralex.astrayt.R
import con.themralex.astrayt.core.designsystem.*

@Composable fun OptionRow(icon:ImageVector?,title:String,onClick:()->Unit,detail:String="",value:String="",selected:Boolean?=null,enabled:Boolean=true) {
    val c=SingularityTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clip(RoundedCornerShape(10.dp)).background(if(selected==true)c.accent.copy(alpha=.09f)else androidx.compose.ui.graphics.Color.Transparent).then(if(selected!=null)Modifier.selectable(selected,enabled=enabled,role=Role.RadioButton,onClick=onClick)else Modifier.clickable(enabled=enabled,role=Role.Button,onClick=onClick)).padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        if(icon!=null)Icon(icon,null,tint=if(selected==true)c.accent else c.mid,modifier=Modifier.size(22.dp))
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
            Text(title,color=if(!enabled)c.low else if(selected==true)c.accent else c.text,style=SingularityTheme.type.body)
            if(detail.isNotBlank())Text(detail,color=c.low,style=SingularityTheme.type.caption)
        }
        if(value.isNotBlank())Text(value,color=c.mid,style=SingularityTheme.type.label,modifier=Modifier.widthIn(max=110.dp),maxLines=2,overflow=TextOverflow.Ellipsis)
        if(selected!=null)Box(Modifier.size(22.dp),contentAlignment=Alignment.Center){if(selected)Icon(Icons.Rounded.Check,null,tint=c.accent,modifier=Modifier.size(20.dp))}
        else Icon(Icons.Rounded.ChevronRight,null,tint=c.low,modifier=Modifier.size(18.dp))
    }
}
@Composable fun SheetHeading(title:String,onClose:()->Unit,onBack:(()->Unit)?=null){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        if(onBack!=null)SingularityIcon(Icons.Rounded.ArrowBack,stringResource(R.string.back),onBack)
        Text(title,color=SingularityTheme.colors.text,style=SingularityTheme.type.title,modifier=Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis)
        SingularityIcon(Icons.Rounded.Close,stringResource(R.string.close),onClose)
    }
}
@Composable fun RememberChoice(checked:Boolean,onChange:(Boolean)->Unit){
    val c=SingularityTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min=52.dp).clip(RoundedCornerShape(10.dp)).toggleable(checked,role=Role.Checkbox,onValueChange=onChange).padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Icon(if(checked)Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,null,tint=if(checked)c.accent else c.low,modifier=Modifier.size(24.dp))
        Text(stringResource(R.string.remember_choice),color=c.mid,style=SingularityTheme.type.label)
    }
}
@Composable fun ChoiceGrid(labels:List<String>,selected:Int,onSelect:(Int)->Unit){
    val c=SingularityTheme.colors;val scale=LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()){
        val count=minOf(labels.size.coerceAtLeast(1),if(maxWidth<320.dp || scale>1.3f)2 else 4)
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            labels.indices.chunked(count).forEach{row->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                row.forEach{i->Box(Modifier.weight(1f).heightIn(min=52.dp).clip(RoundedCornerShape(10.dp)).background(if(i==selected)c.accent.copy(alpha=.12f)else c.high).border(1.dp,if(i==selected)c.accent else c.hairline,RoundedCornerShape(10.dp)).selectable(i==selected,role=Role.RadioButton){onSelect(i)}.padding(horizontal=6.dp,vertical=14.dp),contentAlignment=Alignment.Center){Text(labels[i],color=if(i==selected)c.accent else c.text,style=SingularityTheme.type.label)}}
                repeat(count-row.size){Spacer(Modifier.weight(1f))}
            }}
        }
    }
}
