package con.themralex.astrayt.core.designsystem

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.*
import androidx.compose.material3.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import con.themralex.astrayt.R

@Immutable
data class SingularityColors(val void: Color = Color(0xFF040208), val abyss: Color = Color(0xFF0A0613), val surface: Color = Color(0xFF110B1E), val high: Color = Color(0xFF191229), val top: Color = Color(0xFF241A38), val hairline: Color = Color(0x1FFFFFFF), val strong: Color = Color(0x33FFFFFF), val text: Color = Color(0xFFF5F0FF), val mid: Color = Color(0xFFB9AFD3), val low: Color = Color(0xFF7C7394), val corona: Color = Color(0xFFFFC46B), val ember: Color = Color(0xFFFF8A3D), val plasma: Color = Color(0xFFF0467A), val photon: Color = Color(0xFF9B4DFF), val blueshift: Color = Color(0xFF4F6BFF), val info: Color = Color(0xFF5CC8FF), val success: Color = Color(0xFF46E3A0), val warning: Color = Color(0xFFFFC46B), val error: Color = Color(0xFFFF4D72), val accent: Color = Color(0xFFFF8A3D)) {
    val linear: Brush get() = Brush.horizontalGradient(listOf(corona, accent, plasma, photon))
    val sweep: Brush get() = Brush.sweepGradient(listOf(corona, accent, plasma, photon, blueshift, corona))
    val border: Brush get() = Brush.verticalGradient(listOf(Color(0x29FFFFFF), Color(0x0AFFFFFF)))
}
private val displayFont=FontFamily(Font(R.font.space_grotesk,FontWeight.Medium),Font(R.font.space_grotesk,FontWeight.SemiBold),Font(R.font.space_grotesk,FontWeight.Bold))
private val bodyFont=FontFamily(Font(R.font.inter,FontWeight.Normal),Font(R.font.inter,FontWeight.Medium),Font(R.font.inter,FontWeight.SemiBold))
@Immutable data class SingularityType(
    val display: TextStyle = TextStyle(fontFamily=displayFont,fontSize=34.sp, lineHeight=40.sp, fontWeight=FontWeight.Bold, letterSpacing=(-0.5).sp),
    val headline: TextStyle = TextStyle(fontFamily=displayFont,fontSize=24.sp, lineHeight=30.sp, fontWeight=FontWeight.SemiBold, letterSpacing=(-0.2).sp),
    val title: TextStyle = TextStyle(fontFamily=displayFont,fontSize=18.sp, lineHeight=24.sp, fontWeight=FontWeight.SemiBold),
    val small: TextStyle = TextStyle(fontFamily=displayFont,fontSize=16.sp, lineHeight=22.sp, fontWeight=FontWeight.SemiBold),
    val body: TextStyle = TextStyle(fontFamily=bodyFont,fontSize=15.sp, lineHeight=22.sp),
    val strong: TextStyle = TextStyle(fontFamily=bodyFont,fontSize=15.sp, lineHeight=22.sp, fontWeight=FontWeight.SemiBold),
    val label: TextStyle = TextStyle(fontFamily=bodyFont,fontSize=13.sp, lineHeight=18.sp, fontWeight=FontWeight.Medium, letterSpacing=0.1.sp),
    val caption: TextStyle = TextStyle(fontFamily=bodyFont,fontSize=11.sp, lineHeight=14.sp, fontWeight=FontWeight.Medium, letterSpacing=0.4.sp),
    val eyebrow: TextStyle = TextStyle(fontFamily=bodyFont,fontSize=11.sp, lineHeight=14.sp, fontWeight=FontWeight.SemiBold, letterSpacing=1.2.sp)
)
@Immutable data class SingularityShapes(val xs: Dp=8.dp,val sm: Dp=12.dp,val md: Dp=16.dp,val lg: Dp=22.dp,val xl: Dp=28.dp)
@Immutable data class SingularityMotion(val enabled: Boolean=true,val short: Int=120,val standard: Int=220,val emphasized: Int=360,val long: Int=600)
private val LocalColors=staticCompositionLocalOf { SingularityColors() }
private val LocalType=staticCompositionLocalOf { SingularityType() }
private val LocalMotion=staticCompositionLocalOf { SingularityMotion() }
object SingularityTheme {
    val colors: SingularityColors @Composable get()=LocalColors.current
    val type: SingularityType @Composable get()=LocalType.current
    val shapes=SingularityShapes()
    val motion: SingularityMotion @Composable get()=LocalMotion.current
}
@Composable fun AstraTheme(pureBlack: Boolean=false, preset: String="EMBER", animations: Boolean=true, content: @Composable ()->Unit) {
    val c=SingularityColors(void=if(pureBlack) Color.Black else Color(0xFF040208), accent=when(preset){"PLASMA"->Color(0xFFF0467A);"BLUESHIFT"->Color(0xFF4F6BFF);else->Color(0xFFFF8A3D)})
    CompositionLocalProvider(LocalColors provides c,LocalType provides SingularityType(),LocalMotion provides SingularityMotion(animations)) {
        MaterialTheme(colorScheme=darkColorScheme(primary=c.accent, onPrimary=c.void, background=c.void,onBackground=c.text,surface=c.surface,onSurface=c.text,error=c.error), content=content)
    }
}
