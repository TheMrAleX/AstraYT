package con.themralex.astrayt.feature
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import con.themralex.astrayt.core.designsystem.SingularityTheme
@Composable internal fun QueueActivityLine() {
    val c=SingularityTheme.colors
    val phase=if(SingularityTheme.motion.enabled)rememberInfiniteTransition(label="queue").animateFloat(0f,1f,infiniteRepeatable(tween(1400,easing=LinearEasing)),label="activity").value else .4f
    BoxWithConstraints(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(c.hairline)) {
        Box(Modifier.offset(x=maxWidth*(phase*.7f)).fillMaxWidth(.3f).fillMaxHeight().background(c.accent))
    }
}
