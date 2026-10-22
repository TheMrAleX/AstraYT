package con.themralex.astrayt.feature.player

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextDecoration
import con.themralex.astrayt.core.designsystem.SingularityTheme

@Composable internal fun FormattedDescription(source:String,expanded:Boolean) {
    val formatted=remember(source){DescriptionFormatter.format(source)}
    val c=SingularityTheme.colors
    val annotated=remember(formatted,c.accent){buildAnnotatedString {
        append(formatted.text)
        formatted.links.forEach { link->addLink(LinkAnnotation.Url(link.url,TextLinkStyles(SpanStyle(color=c.accent,textDecoration=TextDecoration.Underline))),link.start,link.end) }
    }}
    Text(annotated,color=c.mid,style=SingularityTheme.type.body,maxLines=if(expanded)Int.MAX_VALUE else 3)
}
