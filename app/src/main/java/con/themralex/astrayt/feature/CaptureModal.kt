package con.themralex.astrayt.feature

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import con.themralex.astrayt.R
import con.themralex.astrayt.core.designsystem.*
import con.themralex.astrayt.data.prefs.Settings

/** One global capture surface: pasting or sharing leaves the current destination in place. */
@Composable fun CaptureModal(model:CaptureModel,settings:Settings) {
    val state by model.state.collectAsStateWithLifecycle()
    val visible=remember{MutableTransitionState(false)}.apply{targetState=state.modalOpen}
    val c=SingularityTheme.colors;val motion=SingularityTheme.motion.enabled
    val scrim=remember{Animatable(0f)}
    LaunchedEffect(state.modalOpen,motion){scrim.animateTo(if(state.modalOpen).6f else 0f,tween(if(motion)180 else 0))}
    val snapshot=remember{mutableStateOf(state)}
    if(state.modalOpen)SideEffect{snapshot.value=state}
    val displayed=if(state.modalOpen)state else snapshot.value
    val keyboard=LocalSoftwareKeyboardController.current;val focus=LocalFocusManager.current
    LaunchedEffect(state.modalOpen){if(state.modalOpen){focus.clearFocus();keyboard?.hide()}}
    if(visible.currentState || visible.targetState)Dialog(onDismissRequest=model::dismissCapture,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        val view=LocalView.current
        DisposableEffect(view){
            (view.parent as? DialogWindowProvider)?.window?.let{it.setDimAmount(0f);it.setWindowAnimations(0)}
            onDispose{}
        }
        BoxWithConstraints(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha=scrim.value)).clickable(interactionSource=remember{MutableInteractionSource()},indication=null){model.dismissCapture()}.statusBarsPadding().navigationBarsPadding().imePadding(),contentAlignment=Alignment.BottomCenter) {
            val height=maxHeight*.94f
            val compact=maxHeight<360.dp
            AnimatedVisibility(visibleState=visible,enter=fadeIn(tween(if(motion)160 else 0))+slideInVertically(tween(if(motion)260 else 0,easing=FastOutSlowInEasing)){it/8},exit=fadeOut(tween(if(motion)140 else 0))+slideOutVertically(tween(if(motion)180 else 0)){it/10}) {
                Column(Modifier.widthIn(max=680.dp).fillMaxWidth().height(height).clip(RoundedCornerShape(topStart=24.dp,topEnd=24.dp)).background(c.surface).border(1.dp,c.hairline,RoundedCornerShape(topStart=24.dp,topEnd=24.dp)).clickable(interactionSource=remember{MutableInteractionSource()},indication=null){}) {
                    if(!compact)Box(Modifier.align(Alignment.CenterHorizontally).padding(top=10.dp,bottom=4.dp).size(32.dp,3.dp).background(c.low,RoundedCornerShape(2.dp)))
                    Row(Modifier.fillMaxWidth().padding(start=20.dp,end=8.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(R.string.new_capture),color=c.text,style=SingularityTheme.type.title)
                            if(!compact)Text(stringResource(R.string.capture_modal_hint),color=c.low,style=SingularityTheme.type.caption)
                        }
                        SingularityIcon(Icons.Rounded.Close,stringResource(R.string.close),model::dismissCapture,enabled=!state.busy)
                    }
                    Box(Modifier.weight(1f).padding(top=12.dp)){CaptureOptions(model,settings,displayed)}
                }
            }
        }
    }
}
