package con.themralex.astrayt.feature

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import con.themralex.astrayt.R
import con.themralex.astrayt.core.designsystem.*
import con.themralex.astrayt.core.util.Formatters
import con.themralex.astrayt.update.*

@Composable fun UpdateBanner(model:SettingsModel,snapshot:AppUpdateState) {
    val c=SingularityTheme.colors
    val motion=SingularityTheme.motion.enabled
    AnimatedVisibility(snapshot.showBanner,enter=fadeIn(androidx.compose.animation.core.tween(if(motion)180 else 0))+expandVertically(animationSpec=androidx.compose.animation.core.tween(if(motion)220 else 0)),exit=fadeOut(androidx.compose.animation.core.tween(if(motion)140 else 0))+shrinkVertically(animationSpec=androidx.compose.animation.core.tween(if(motion)180 else 0))) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.high).border(1.dp,c.accent.copy(alpha=.24f),RoundedCornerShape(14.dp)).clickable(role=Role.Button,onClick=model::openUpdate).padding(start=14.dp,top=8.dp,bottom=8.dp,end=4.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Icon(if(snapshot.stage=="READY")Icons.Rounded.InstallMobile else Icons.Rounded.SystemUpdate,null,tint=c.accent,modifier=Modifier.size(22.dp))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(when{snapshot.stage=="READY"->R.string.update_ready;snapshot.busy->R.string.update_downloading;else->R.string.update_banner_title},snapshot.release?.version.orEmpty()),color=c.text,style=SingularityTheme.type.label,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text(if(snapshot.busy)"${Formatters.bytes(snapshot.done)} / ${Formatters.bytes(snapshot.total)}"else stringResource(R.string.update_banner_body),color=c.mid,style=SingularityTheme.type.caption.copy(fontFeatureSettings="tnum"),maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                if(!snapshot.busy && snapshot.stage!="READY")SingularityIcon(Icons.Rounded.Close,stringResource(R.string.update_dismiss),{model.dismissUpdate()})else SingularityIcon(Icons.Rounded.ChevronRight,stringResource(R.string.update_details),model::openUpdate)
            }
            if(snapshot.busy && snapshot.total>0)AccretionProgressBar((snapshot.done.toFloat()/snapshot.total).coerceIn(0f,1f),Modifier.padding(end=10.dp))else Spacer(Modifier.height(3.dp))
        }
    }
}

@Composable fun UpdateHost(model:SettingsModel,eligible:Boolean=true) {
    val snapshot by model.update.collectAsStateWithLifecycle()
    val checking by model.checking.collectAsStateWithLifecycle()
    val visible by model.updateDialog.collectAsStateWithLifecycle()
    val installError by model.installError.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    var resumed by remember{mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
    var canInstall by remember{mutableStateOf(context.packageManager.canRequestPackageInstalls())}
    fun installPackage() {
        val file=model.updates.readyFile(snapshot)
        if(file==null){model.installationFailed();return}
        runCatching {
            val uri=FileProvider.getUriForFile(context,context.packageName+".updates",file)
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK).apply{clipData=ClipData.newRawUri("AstraYT",uri)})
        }.onFailure{model.installationFailed()}
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        canInstall=context.packageManager.canRequestPackageInstalls()
        if(canInstall)installPackage()
    }
    fun install() {
        canInstall=context.packageManager.canRequestPackageInstalls()
        if(canInstall)installPackage()else runCatching{permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+context.packageName)))}.onFailure{model.installationFailed()}
    }
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver{_,event->
            if(event==Lifecycle.Event.ON_RESUME){resumed=true;canInstall=context.packageManager.canRequestPackageInstalls();model.checkInBackground()}
            else if(event==Lifecycle.Event.ON_PAUSE)resumed=false
        }
        owner.lifecycle.addObserver(observer)
        onDispose{owner.lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(snapshot.stage,snapshot.release?.tag,resumed,eligible) {
        if(eligible && resumed && snapshot.stage=="READY" && model.consumeAutoInstall(snapshot.release?.tag)){model.openUpdate();install()}
    }
    if(!visible || !eligible)return
    val c=SingularityTheme.colors
    val release=snapshot.release
    val error=if(installError)"INSTALL"else if(snapshot.stage=="READY")null else snapshot.error ?: checking.error
    Dialog(model::closeUpdate,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp),contentAlignment=Alignment.Center) {
            Column(Modifier.widthIn(max=560.dp).fillMaxWidth().heightIn(max=maxHeight).clip(RoundedCornerShape(22.dp)).background(c.surface).border(1.dp,c.border,RoundedCornerShape(22.dp)).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    AstraLogo(Modifier.size(48.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.updates),color=c.mid,style=SingularityTheme.type.caption)
                        Text(if(release!=null)"AstraYT ${release.version}"else stringResource(R.string.update_dialog_title),color=c.text,style=SingularityTheme.type.title)
                    }
                    SingularityIcon(Icons.Rounded.Close,stringResource(R.string.close),model::closeUpdate)
                }
                if(checking.checking){IndeterminateProgressLine();Text(stringResource(R.string.update_checking),color=c.mid,style=SingularityTheme.type.body)}
                if(release!=null)Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.update_whats_new),color=c.text,style=SingularityTheme.type.small)
                    Text(UpdatePolicy.notes(release.notes).ifBlank{stringResource(R.string.update_notes_empty)},color=c.mid,style=SingularityTheme.type.body)
                }else if(!checking.checking && error==null)Text(stringResource(R.string.up_to_date),color=c.text,style=SingularityTheme.type.body)
                if(snapshot.busy) {
                    Text(stringResource(when(snapshot.stage){"VERIFYING"->R.string.update_verifying;"QUEUED"->R.string.update_queued;else->R.string.update_downloading},release?.version.orEmpty()),color=c.mid,style=SingularityTheme.type.label)
                    if(snapshot.stage=="VERIFYING" || snapshot.done==0L)IndeterminateProgressLine()else AccretionProgressBar((snapshot.done.toFloat()/snapshot.total.coerceAtLeast(1)).coerceIn(0f,1f))
                    Text("${Formatters.bytes(snapshot.done)} / ${Formatters.bytes(snapshot.total)}",color=c.mid,style=SingularityTheme.type.caption.copy(fontFeatureSettings="tnum"))
                    SingularityButton(stringResource(R.string.cancel),{model.cancelUpdate()},Modifier.fillMaxWidth(),variant="secondary")
                }else {
                    if(error!=null)Text(stringResource(updateError(error)),color=c.error,style=SingularityTheme.type.body)
                    if(snapshot.stage=="READY") {
                        if(!canInstall)Text(stringResource(R.string.update_install_permission),color=c.mid,style=SingularityTheme.type.caption)
                        SingularityButton(stringResource(if(canInstall)R.string.update_install else R.string.update_allow_install),{install()},Modifier.fillMaxWidth())
                    }else if(release!=null)SingularityButton(stringResource(if(snapshot.stage=="FAILED")R.string.update_retry_download else R.string.update_download,Formatters.bytes(release.size)),model::downloadUpdate,Modifier.fillMaxWidth(),enabled=!checking.checking)
                    else if(error!=null)SingularityButton(stringResource(R.string.retry),model::checkUpdate,Modifier.fillMaxWidth(),enabled=!checking.checking)
                    SingularityButton(stringResource(if(release==null && error==null && !checking.checking)R.string.accept else R.string.update_later),model::closeUpdate,Modifier.fillMaxWidth(),variant="tertiary")
                }
            }
        }
    }
}
private fun updateError(code:String)=when(code){"VERIFY"->R.string.update_error_verify;"COMPATIBLE"->R.string.no_compatible_update;"DISK"->R.string.error_disk;"DOWNLOAD"->R.string.update_error_download;"INSTALL"->R.string.update_error_install;else->R.string.update_error_check}
