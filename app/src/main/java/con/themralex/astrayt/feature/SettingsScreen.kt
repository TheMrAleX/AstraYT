package con.themralex.astrayt.feature

import android.content.Intent
import android.provider.Settings as SystemSettings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import con.themralex.astrayt.BuildConfig
import con.themralex.astrayt.R
import con.themralex.astrayt.core.designsystem.*
import con.themralex.astrayt.core.util.Formatters
import con.themralex.astrayt.data.db.VideoEntity
import java.util.Locale

@Composable fun SettingsScreen(model:SettingsModel,all:List<VideoEntity>) {
    val settings by model.state.collectAsStateWithLifecycle()
    val channels by model.channels.collectAsStateWithLifecycle()
    val update by model.update.collectAsStateWithLifecycle()
    val checking by model.checking.collectAsStateWithLifecycle()
    val c=SingularityTheme.colors
    val context=LocalContext.current
    val storedBytes=remember(all){all.sumOf{it.fileSizeBytes}}
    var sheet by rememberSaveable{mutableStateOf<String?>(null)}
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter){
    LazyColumn(Modifier.widthIn(max=680.dp).fillMaxSize(),contentPadding=PaddingValues(start=16.dp,end=16.dp,top=20.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        item(key="heading",contentType="heading"){
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text(stringResource(R.string.settings),color=c.text,style=SingularityTheme.type.headline)
            }
        }
        item(key="identity",contentType="identity"){
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp,c.border,RoundedCornerShape(16.dp)).padding(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    AstraLogo(Modifier.size(44.dp))
                    Text("AstraYT",color=c.text,style=SingularityTheme.type.small,modifier=Modifier.weight(1f))
                    Text(BuildConfig.VERSION_NAME,color=c.mid,style=SingularityTheme.type.label)
                }
                SettingDivider()
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(role=Role.Button,onClick=model::checkUpdate).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    Icon(Icons.Rounded.SystemUpdate,null,tint=c.accent,modifier=Modifier.size(24.dp))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                        Text(stringResource(R.string.updates),color=c.text,style=SingularityTheme.type.strong)
                        Text(when{checking.checking->stringResource(R.string.update_checking);update.stage=="READY"->stringResource(R.string.update_ready,update.release?.version.orEmpty());update.busy->stringResource(R.string.update_downloading,update.release?.version.orEmpty());update.available!=null->stringResource(R.string.update_banner_title,update.available!!.version);else->stringResource(R.string.settings_auto_updates)},color=c.mid,style=SingularityTheme.type.caption)
                    }
                    Icon(Icons.Rounded.ChevronRight,null,tint=c.low,modifier=Modifier.size(20.dp))
                }
                if(checking.checking || update.busy)IndeterminateProgressLine()
            }
        }
        item(key="appearance",contentType="settings-group"){
            SettingsGroup(Icons.Rounded.Palette,stringResource(R.string.appearance)){
                Row(Modifier.fillMaxWidth().selectableGroup(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    listOf("EMBER" to c.ember,"PLASMA" to c.plasma,"BLUESHIFT" to c.blueshift).forEach{(preset,color)->
                        val selected=settings.accentPreset==preset
                        val fill by animateColorAsState(if(selected)color.copy(alpha=.12f)else c.abyss,tween(if(SingularityTheme.motion.enabled)180 else 0),label="accent")
                        Row(Modifier.weight(1f).heightIn(min=48.dp).clip(RoundedCornerShape(10.dp)).background(fill).border(1.dp,if(selected)color else c.hairline,RoundedCornerShape(12.dp)).selectable(selected,role=Role.RadioButton){model.change{it.copy(accentPreset=preset)}}.padding(horizontal=8.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){
                            Box(Modifier.padding(end=6.dp).size(14.dp).background(color,CircleShape),contentAlignment=Alignment.Center){if(selected)Icon(Icons.Rounded.Check,null,tint=c.void,modifier=Modifier.size(12.dp))}
                            Text(stringResource(when(preset){"PLASMA"->R.string.accent_rose;"BLUESHIFT"->R.string.accent_blue;else->R.string.accent_amber}),color=if(selected)c.text else c.mid,style=SingularityTheme.type.caption,maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                    }
                }
                SettingDivider()
                SettingToggle(stringResource(R.string.amoled),stringResource(R.string.settings_amoled_body),settings.amoledPureBlack){v->model.change{it.copy(amoledPureBlack=v)}}
                SettingDivider()
                SettingToggle(stringResource(R.string.animations),stringResource(R.string.settings_motion_body),settings.animationsEnabled){v->model.change{it.copy(animationsEnabled=v)}}
            }
        }
        item(key="audio",contentType="settings-group"){
            SettingsGroup(Icons.Rounded.Headphones,stringResource(R.string.settings_audio)){
                SettingsRow(Icons.Rounded.Language,stringResource(R.string.preferred_language),value=Locale.forLanguageTag(settings.preferredAudioLanguage).displayName,onClick={sheet="language"})
                SettingDivider()
                SettingToggle(stringResource(R.string.ask_alternatives),stringResource(R.string.settings_ask_body),settings.askWhenAlternatives){v->model.change{it.copy(askWhenAlternatives=v)}}
                SettingDivider()
                SettingToggle(stringResource(R.string.auto_dub),stringResource(R.string.settings_dub_body),settings.autoPickDubbed){v->model.change{it.copy(autoPickDubbed=v)}}
                settings.rememberedAudioLanguage?.let{language->SettingDivider();SettingsRow(Icons.Rounded.Bookmark,stringResource(R.string.remembered_audio),subtitle=Locale.forLanguageTag(language).displayName,trailing={SingularityIcon(Icons.Rounded.Close,stringResource(R.string.forget_audio),{model.change{it.copy(rememberedAudioLanguage=null)}})})}
                SettingDivider()
                SettingsRow(Icons.Rounded.History,stringResource(R.string.remembered_channels),value=channels.size.toString(),onClick={sheet="channels"})
            }
        }
        item(key="subtitles",contentType="settings-group"){
            SettingsGroup(Icons.Rounded.ClosedCaption,stringResource(R.string.subtitles)){
                SettingsRow(Icons.Rounded.Language,stringResource(R.string.subtitle_remembered),value=settings.subtitleLanguage?.let{Locale.forLanguageTag(it).displayLanguage} ?: stringResource(R.string.subtitles_off),onClick={sheet="subtitle-language"})
                Text(stringResource(R.string.subtitle_settings_body),color=c.low,style=SingularityTheme.type.caption,modifier=Modifier.padding(bottom=8.dp))
            }
        }
        item(key="downloads",contentType="settings-group"){
            SettingsGroup(Icons.Rounded.Downloading,stringResource(R.string.downloads)){
                SettingsRow(Icons.Rounded.HighQuality,stringResource(R.string.max_quality),value=if(settings.defaultMaxQuality==Int.MAX_VALUE)stringResource(R.string.max)else "${settings.defaultMaxQuality}p",onClick={sheet="quality"})
                SettingDivider()
                SettingNumber(stringResource(R.string.concurrent),stringResource(R.string.settings_concurrent_body),settings.maxConcurrentDownloads,1..5){v->model.change{it.copy(maxConcurrentDownloads=v)}}
                SettingDivider()
                SettingNumber(stringResource(R.string.connections),stringResource(R.string.settings_connections_body),settings.connectionsPerStream,1..6){v->model.change{it.copy(connectionsPerStream=v)}}
                SettingDivider()
                SettingToggle(stringResource(R.string.wifi_only),stringResource(R.string.settings_wifi_body),settings.wifiOnly){v->model.change{it.copy(wifiOnly=v)}}
                SettingDivider()
                SettingsRow(Icons.Rounded.Folder,stringResource(R.string.folder),subtitle="Movies/AstraYT · Music/AstraYT")
                SettingDivider()
                SettingsRow(Icons.Rounded.BatteryChargingFull,stringResource(R.string.battery),onClick={runCatching{context.startActivity(Intent(SystemSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))}})
            }
        }
        item(key="sponsorblock",contentType="settings-group"){
            SettingsGroup(Icons.Rounded.ContentCut,"SponsorBlock"){
                SettingToggle(stringResource(R.string.sponsorblock_enable),stringResource(R.string.sponsorblock_explanation),settings.sponsorBlockEnabled){v->model.change{it.copy(sponsorBlockEnabled=v)}}
                AnimatedVisibility(settings.sponsorBlockEnabled,enter=fadeIn(tween(if(SingularityTheme.motion.enabled)180 else 0))+expandVertically(tween(if(SingularityTheme.motion.enabled)220 else 0)),exit=fadeOut(tween(if(SingularityTheme.motion.enabled)140 else 0))+shrinkVertically(tween(if(SingularityTheme.motion.enabled)180 else 0))){Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                    listOf("sponsor" to R.string.sb_sponsor,"intro" to R.string.sb_intro,"outro" to R.string.sb_outro,"selfpromo" to R.string.sb_selfpromo,"interaction" to R.string.sb_interaction,"music_offtopic" to R.string.sb_music,"preview" to R.string.sb_preview,"filler" to R.string.sb_filler).forEach{(key,label)->
                        SettingDivider();SettingToggle(stringResource(label),"",key in settings.sponsorBlockCategories){v->model.change{it.copy(sponsorBlockCategories=if(v)it.sponsorBlockCategories+key else it.sponsorBlockCategories-key)}}
                    }
                    Text(stringResource(R.string.sponsorblock_cuts),color=c.low,style=SingularityTheme.type.caption)
                }}
            }
        }
        item(key="storage",contentType="settings-group"){
            SettingsGroup(Icons.Rounded.Storage,stringResource(R.string.storage)){
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(Formatters.bytes(storedBytes),color=c.text,style=SingularityTheme.type.headline,modifier=Modifier.weight(1f));Text(stringResource(R.string.library),color=c.mid,style=SingularityTheme.type.label)}
                SettingsRow(Icons.Rounded.CleaningServices,stringResource(R.string.clear_cache),subtitle=stringResource(R.string.settings_cache_body),onClick={model.clearCache(context)})
            }
        }
        item(key="about",contentType="settings-group"){
            SettingsGroup(Icons.Rounded.Info,stringResource(R.string.about)){
                SettingsRow(Icons.Rounded.Code,stringResource(R.string.repository),onClick={external(context,"https://github.com/${BuildConfig.GITHUB_REPOSITORY}")})
                SettingDivider()
                SettingsRow(Icons.Rounded.Description,stringResource(R.string.licenses),subtitle="NewPipeExtractor · GPL-3.0",onClick={external(context,"https://github.com/${BuildConfig.GITHUB_REPOSITORY}/blob/main/LICENSE")})
                Text(stringResource(R.string.legal),color=c.low,style=SingularityTheme.type.caption)
            }
        }
    }
    }
    sheet?.let{kind->SingularitySheet({sheet=null}){when(kind){
        "language"->LanguagePicker(settings.preferredAudioLanguage){language->model.change{it.copy(preferredAudioLanguage=language,rememberedAudioLanguage=null)};sheet=null}
        "subtitle-language"-> {SettingsRow(Icons.Rounded.ClosedCaptionOff,stringResource(R.string.subtitles_off),onClick={model.change{it.copy(subtitleLanguage=null)};sheet=null});LanguagePicker(settings.subtitleLanguage ?: settings.preferredAudioLanguage){language->model.change{it.copy(subtitleLanguage=language)};sheet=null}}
        "quality"->{
            SheetHeading(stringResource(R.string.max_quality),{sheet=null})
            val values=listOf(720,1080,Int.MAX_VALUE)
            ChoiceGrid(values.map{if(it==Int.MAX_VALUE)stringResource(R.string.max)else "${it}p"},values.indexOf(settings.defaultMaxQuality)){i->model.change{it.copy(defaultMaxQuality=values[i])};sheet=null}
        }
        "channels"->if(channels.isEmpty())Text(stringResource(R.string.empty_channels),color=c.mid)else channels.forEach{pref->SettingsRow(Icons.Rounded.Bookmark,all.firstOrNull{it.channelId==pref.channelId}?.channelName ?: pref.channelId.substringAfterLast('/'),subtitle=Locale.forLanguageTag(pref.languageTag).displayName,trailing={SingularityIcon(Icons.Rounded.Delete,stringResource(R.string.delete),{model.forget(pref.channelId)})})}
    }}}
}
@Composable private fun SettingsGroup(icon:ImageVector,title:String,content:@Composable ColumnScope.()->Unit) {
    val c=SingularityTheme.colors
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){Icon(icon,null,tint=c.accent,modifier=Modifier.size(20.dp));Text(title,color=c.text,style=SingularityTheme.type.small)}
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp,c.border,RoundedCornerShape(16.dp)).padding(horizontal=16.dp,vertical=4.dp),verticalArrangement=Arrangement.spacedBy(0.dp),content=content)
    }
}
@Composable private fun SettingDivider(){Box(Modifier.fillMaxWidth().height(1.dp).background(SingularityTheme.colors.hairline.copy(alpha=.5f)))}
@Composable private fun SettingToggle(title:String,subtitle:String,checked:Boolean,onChange:(Boolean)->Unit) {
    val c=SingularityTheme.colors;val haptic=LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth().heightIn(min=64.dp).toggleable(checked,role=Role.Switch){haptic.performHapticFeedback(HapticFeedbackType.ContextClick);onChange(it)}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(title,color=c.text,style=SingularityTheme.type.body);if(subtitle.isNotBlank())Text(subtitle,color=c.low,style=SingularityTheme.type.caption)}
        SingularitySwitch(checked,{},title,interactive=false)
    }
}
@Composable private fun SettingNumber(title:String,subtitle:String,value:Int,range:IntRange,onChange:(Int)->Unit) {
    val c=SingularityTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min=72.dp).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
            Text(title,color=c.text,style=SingularityTheme.type.body)
            Text(subtitle,color=c.low,style=SingularityTheme.type.caption)
        }
        Row(Modifier.clip(RoundedCornerShape(10.dp)).background(c.abyss),verticalAlignment=Alignment.CenterVertically){
            SingularityIcon(Icons.Rounded.Remove,stringResource(R.string.settings_decrease,title),{onChange((value-1).coerceIn(range))},enabled=value>range.first)
            Text(value.toString(),color=c.text,style=SingularityTheme.type.strong.copy(fontFeatureSettings="tnum"),modifier=Modifier.widthIn(min=20.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            SingularityIcon(Icons.Rounded.Add,stringResource(R.string.settings_increase,title),{onChange((value+1).coerceIn(range))},enabled=value<range.last)
        }
    }
}
