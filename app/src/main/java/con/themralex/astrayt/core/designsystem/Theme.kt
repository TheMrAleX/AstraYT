package con.themralex.astrayt.core.designsystem

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.*
import androidx.compose.material3.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import con.themralex.astrayt.R

@Immutable
data class SingularityColors(val void: Color = Color(0xFF08090B), val abyss: Color = Color(0xFF0B0D10), val surface: Color = Color(0xFF111317), val high: Color = Color(0xFF171A1F), val top: Color = Color(0xFF22262D), val hairline: Color = Color(0x1FFFFFFF), val strong: Color = Color(0x33FFFFFF), val text: Color = Color(0xFFF3F2EE), val mid: Color = Color(0xFFB8BBC2), val low: Color = Color(0xFF8B919B), val corona: Color = Color(0xFFF3CEAA), val ember: Color = Color(0xFFEBA66C), val plasma: Color = Color(0xFFD48FA7), val photon: Color = Color(0xFFA394C7), val blueshift: Color = Color(0xFF8BAADC), val info: Color = Color(0xFF5CC8FF), val success: Color = Color(0xFF46E3A0), val warning: Color = Color(0xFFF3CEAA), val error: Color = Color(0xFFFF4D72), val accent: Color = Color(0xFFEBA66C)) {
    val linear: Brush get() = Brush.horizontalGradient(listOf(accent, corona))
    val sweep: Brush get() = Brush.sweepGradient(listOf(accent.copy(alpha=.15f), accent, corona, accent.copy(alpha=.15f)))
    val border: Brush get() = Brush.verticalGradient(listOf(Color(0x19FFFFFF), Color(0x0DFFFFFF)))
}
private val displayFont=FontFamily(Font(R.font.space_grotesk))
private val bodyFont=FontFamily(Font(R.font.inter))
@Immutable data class SingularityType(
    val display: TextStyle = TextStyle(fontFamily=displayFont,fontSize=36.sp, lineHeight=42.sp, fontWeight=FontWeight.Bold, letterSpacing=(-0.5).sp),
    val headline: TextStyle = TextStyle(fontFamily=displayFont,fontSize=28.sp, lineHeight=34.sp, fontWeight=FontWeight.SemiBold, letterSpacing=(-0.2).sp),
    val title: TextStyle = TextStyle(fontFamily=displayFont,fontSize=18.sp, lineHeight=24.sp, fontWeight=FontWeight.SemiBold),
    val small: TextStyle = TextStyle(fontFamily=displayFont,fontSize=16.sp, lineHeight=22.sp, fontWeight=FontWeight.SemiBold),
    val body: TextStyle = TextStyle(fontFamily=bodyFont,fontSize=15.sp, lineHeight=22.sp),
    val strong: TextStyle = TextStyle(fontFamily=bodyFont,fontSize=15.sp, lineHeight=22.sp, fontWeight=FontWeight.SemiBold),
    val label: TextStyle = TextStyle(fontFamily=bodyFont,fontSize=13.sp, lineHeight=18.sp, fontWeight=FontWeight.Medium, letterSpacing=0.1.sp),
    val caption: TextStyle = TextStyle(fontFamily=bodyFont,fontSize=12.sp, lineHeight=17.sp, fontWeight=FontWeight.Medium, letterSpacing=0.4.sp),
    val eyebrow: TextStyle = TextStyle(fontFamily=bodyFont,fontSize=12.sp, lineHeight=17.sp, fontWeight=FontWeight.SemiBold, letterSpacing=1.2.sp)
)
@Immutable data class SingularityShapes(val xs: Dp=8.dp,val sm: Dp=12.dp,val md: Dp=16.dp,val lg: Dp=22.dp,val xl: Dp=28.dp)
@Immutable data class SingularityMotion(val enabled: Boolean=true,val short: Int=120,val standard: Int=220,val emphasized: Int=360,val long: Int=600,val ambientEnabled:Boolean=enabled,val progressEnabled:Boolean=true)
private val LocalColors=staticCompositionLocalOf { SingularityColors() }
private val LocalType=staticCompositionLocalOf { SingularityType() }
private val LocalMotion=staticCompositionLocalOf { SingularityMotion() }
object SingularityTheme {
    val colors: SingularityColors @Composable get()=LocalColors.current
    val type: SingularityType @Composable get()=LocalType.current
    val shapes=SingularityShapes()
    val motion: SingularityMotion @Composable get()=LocalMotion.current
}
@Composable fun AstraTheme(pureBlack: Boolean=false, preset: String="EMBER", animations: Boolean=true, ambientAnimations:Boolean=animations, progressAnimations:Boolean=true, content: @Composable ()->Unit) {
    val c=SingularityColors(void=if(pureBlack) Color.Black else Color(0xFF08090B), accent=when(preset){"PLASMA"->Color(0xFFD48FA7);"BLUESHIFT"->Color(0xFF8BAADC);else->Color(0xFFEBA66C)})
    CompositionLocalProvider(LocalColors provides c,LocalType provides SingularityType(),LocalMotion provides SingularityMotion(animations,ambientEnabled=ambientAnimations,progressEnabled=progressAnimations)) {
        val t=SingularityType()
        MaterialTheme(typography=Typography(bodyLarge=t.body,bodyMedium=t.body,bodySmall=t.caption,titleLarge=t.title,titleMedium=t.small,labelLarge=t.label),colorScheme=darkColorScheme(primary=c.accent, onPrimary=c.void, background=c.void,onBackground=c.text,surface=c.surface,onSurface=c.text,error=c.error), content=content)
    }
}
