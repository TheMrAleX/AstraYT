package con.themralex.astrayt.feature

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
    val motion=SingularityTheme.motion.enabled
    val c=SingularityTheme.colors
    val context=LocalContext.current
    val storedBytes=remember(all){all.sumOf{it.fileSizeBytes}}
    var sheet by rememberSaveable{mutableStateOf<String?>(null)}
    var page by rememberSaveable { mutableStateOf("home") }
    val stateHolder = rememberSaveableStateHolder()
    val titles = mapOf("appearance" to stringResource(R.string.appearance), "audio" to stringResource(R.string.settings_audio),
        "subtitles" to stringResource(R.string.subtitles), "downloads" to stringResource(R.string.downloads),
        "sponsorblock" to "SponsorBlock", "storage" to stringResource(R.string.storage), "about" to stringResource(R.string.about))
    BackHandler(page != "home" && sheet == null) { page = "home" }
    AnimatedContent(page, modifier = Modifier.fillMaxSize(), transitionSpec = {
        val duration = if (motion) 220 else 0
        (fadeIn(tween(duration)) + slideInHorizontally(tween(duration)) { if(targetState == "home") -it/12 else it/12 }) togetherWith
            fadeOut(tween(duration / 2))
    }, label = "settings-page") { destination ->
    stateHolder.SaveableStateProvider(destination) {
    Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally){
        Row(Modifier.widthIn(max=640.dp).fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(destination!="home")SingularityIcon(Icons.Rounded.ArrowBack,stringResource(R.string.back),{page="home"})
            Text(if(destination=="home")stringResource(R.string.settings)else titles[destination].orEmpty(),color=c.text,
                style=if(destination=="home")SingularityTheme.type.headline else SingularityTheme.type.title,modifier=Modifier.weight(1f))
            if(destination=="home")AstraLogo(Modifier.size(44.dp))
        }

    LazyColumn(Modifier.widthIn(max=640.dp).fillMaxWidth().weight(1f),contentPadding=PaddingValues(start=20.dp,end=20.dp,top=12.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
        if(destination=="home") {
            item(key="personal",contentType="settings-group") {
                SettingsGroup(title=stringResource(R.string.settings_personal)) {
                    SettingsEntry(Icons.Rounded.Palette,stringResource(R.string.appearance),subtitle=stringResource(R.string.settings_appearance_summary),onClick={page="appearance"})
                    SettingDivider()
                    SettingsEntry(Icons.Rounded.Headphones,stringResource(R.string.settings_audio),subtitle=Locale.forLanguageTag(settings.preferredAudioLanguage).displayName,onClick={page="audio"})
                    SettingDivider()
                    SettingsEntry(Icons.Rounded.ClosedCaption,stringResource(R.string.subtitles),subtitle=settings.subtitleLanguage?.let{Locale.forLanguageTag(it).displayLanguage} ?: stringResource(R.string.subtitles_off),onClick={page="subtitles"})
                }
            }
            item(key="management",contentType="settings-group") {
                SettingsGroup(title=stringResource(R.string.settings_manage)) {
                    SettingsEntry(Icons.Rounded.Downloading,stringResource(R.string.downloads),subtitle=stringResource(R.string.settings_downloads_summary,if(settings.defaultMaxQuality==Int.MAX_VALUE)stringResource(R.string.max)else "${settings.defaultMaxQuality}p",settings.maxConcurrentDownloads),onClick={page="downloads"})
                    SettingDivider()
                    SettingsEntry(Icons.Rounded.ContentCut,"SponsorBlock",subtitle=stringResource(if(settings.sponsorBlockEnabled)R.string.settings_cuts_on else R.string.settings_cuts_off),onClick={page="sponsorblock"})
                    SettingDivider()
                    SettingsEntry(Icons.Rounded.Storage,stringResource(R.string.storage),subtitle=stringResource(R.string.settings_library_size,Formatters.bytes(storedBytes)),onClick={page="storage"})
                }
            }
        item(key="identity",contentType="identity"){
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp,c.border,RoundedCornerShape(16.dp)).padding(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
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
                SettingDivider()
                SettingsEntry(Icons.Rounded.Info,stringResource(R.string.about),onClick={page="about"})
            }
        }
        }
        if(destination=="appearance")item(key="appearance",contentType="settings-group"){
            SettingsGroup {
                FlowRow(Modifier.fillMaxWidth().padding(vertical=12.dp).selectableGroup(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    listOf("EMBER" to c.ember,"PLASMA" to c.plasma,"BLUESHIFT" to c.blueshift).forEach{(preset,color)->
                        val selected=settings.accentPreset==preset
                        val fill by animateColorAsState(if(selected)color.copy(alpha=.12f)else c.abyss,tween(if(SingularityTheme.motion.enabled)180 else 0),label="accent")
                        Row(Modifier.widthIn(min=88.dp).heightIn(min=48.dp).clip(RoundedCornerShape(10.dp)).background(fill).border(1.dp,if(selected)color else c.hairline,RoundedCornerShape(12.dp)).selectable(selected,role=Role.RadioButton){model.change{it.copy(accentPreset=preset)}}.padding(horizontal=8.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){
                            Box(Modifier.padding(end=6.dp).size(14.dp).background(color,CircleShape),contentAlignment=Alignment.Center){if(selected)Icon(Icons.Rounded.Check,null,tint=c.void,modifier=Modifier.size(12.dp))}
                            Text(stringResource(when(preset){"PLASMA"->R.string.accent_rose;"BLUESHIFT"->R.string.accent_blue;else->R.string.accent_amber}),color=if(selected)c.text else c.mid,style=SingularityTheme.type.label)
                        }
                    }
                }
                SettingDivider()
                SettingToggle(stringResource(R.string.amoled),stringResource(R.string.settings_amoled_body),settings.amoledPureBlack){v->model.change{it.copy(amoledPureBlack=v)}}
                SettingDivider()
                SettingToggle(stringResource(R.string.animations),stringResource(R.string.settings_motion_body),settings.animationsEnabled){v->model.change{it.copy(animationsEnabled=v)}}
            }
        }
        if(destination=="audio")item(key="audio",contentType="settings-group"){
            SettingsGroup {
                SettingsEntry(Icons.Rounded.Language,stringResource(R.string.preferred_language),value=Locale.forLanguageTag(settings.preferredAudioLanguage).displayName,onClick={sheet="language"})
                SettingDivider()
                SettingToggle(stringResource(R.string.ask_alternatives),stringResource(R.string.settings_ask_body),settings.askWhenAlternatives){v->model.change{it.copy(askWhenAlternatives=v)}}
                SettingDivider()
                SettingToggle(stringResource(R.string.auto_dub),stringResource(R.string.settings_dub_body),settings.autoPickDubbed){v->model.change{it.copy(autoPickDubbed=v)}}
                settings.rememberedAudioLanguage?.let{language->SettingDivider();SettingsEntry(Icons.Rounded.Bookmark,stringResource(R.string.remembered_audio),subtitle=Locale.forLanguageTag(language).displayName,trailing={SingularityIcon(Icons.Rounded.Close,stringResource(R.string.forget_audio),{model.change{it.copy(rememberedAudioLanguage=null)}})})}
                SettingDivider()
                SettingsEntry(Icons.Rounded.History,stringResource(R.string.remembered_channels),value=channels.size.toString(),onClick={sheet="channels"})
            }
        }
        if(destination=="subtitles")item(key="subtitles",contentType="settings-group"){
            SettingsGroup {
                SettingsEntry(Icons.Rounded.Language,stringResource(R.string.subtitle_remembered),value=settings.subtitleLanguage?.let{Locale.forLanguageTag(it).displayLanguage} ?: stringResource(R.string.subtitles_off),onClick={sheet="subtitle-language"})
                Text(stringResource(R.string.subtitle_settings_body),color=c.mid,style=SingularityTheme.type.label,modifier=Modifier.padding(bottom=8.dp))
            }
        }
        if(destination=="downloads")item(key="downloads",contentType="settings-group"){
            SettingsGroup {
                SettingsEntry(Icons.Rounded.HighQuality,stringResource(R.string.max_quality),value=if(settings.defaultMaxQuality==Int.MAX_VALUE)stringResource(R.string.max)else "${settings.defaultMaxQuality}p",onClick={sheet="quality"})
                SettingDivider()
                SettingNumber(stringResource(R.string.concurrent),stringResource(R.string.settings_concurrent_body),settings.maxConcurrentDownloads,1..5){v->model.change{it.copy(maxConcurrentDownloads=v)}}
                SettingDivider()
                SettingNumber(stringResource(R.string.connections),stringResource(R.string.settings_connections_body),settings.connectionsPerStream,1..6){v->model.change{it.copy(connectionsPerStream=v)}}
                SettingDivider()
                SettingToggle(stringResource(R.string.wifi_only),stringResource(R.string.settings_wifi_body),settings.wifiOnly){v->model.change{it.copy(wifiOnly=v)}}
                SettingDivider()
                SettingsEntry(Icons.Rounded.Folder,stringResource(R.string.folder),subtitle="Movies/AstraYT · Music/AstraYT")
                SettingDivider()
                SettingsEntry(Icons.Rounded.BatteryChargingFull,stringResource(R.string.battery),onClick={runCatching{context.startActivity(Intent(SystemSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))}})
            }
        }
        if(destination=="sponsorblock")item(key="sponsorblock",contentType="settings-group"){
            SettingsGroup {
                SettingToggle(stringResource(R.string.sponsorblock_enable),stringResource(R.string.sponsorblock_explanation),settings.sponsorBlockEnabled){v->model.change{it.copy(sponsorBlockEnabled=v)}}
                AnimatedVisibility(settings.sponsorBlockEnabled,enter=fadeIn(tween(if(SingularityTheme.motion.enabled)180 else 0))+expandVertically(tween(if(SingularityTheme.motion.enabled)220 else 0)),exit=fadeOut(tween(if(SingularityTheme.motion.enabled)140 else 0))+shrinkVertically(tween(if(SingularityTheme.motion.enabled)180 else 0))){Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                    listOf("sponsor" to R.string.sb_sponsor,"intro" to R.string.sb_intro,"outro" to R.string.sb_outro,"selfpromo" to R.string.sb_selfpromo,"interaction" to R.string.sb_interaction,"music_offtopic" to R.string.sb_music,"preview" to R.string.sb_preview,"filler" to R.string.sb_filler).forEach{(key,label)->
                        SettingDivider();SettingToggle(stringResource(label),"",key in settings.sponsorBlockCategories){v->model.change{it.copy(sponsorBlockCategories=if(v)it.sponsorBlockCategories+key else it.sponsorBlockCategories-key)}}
                    }
                    Text(stringResource(R.string.sponsorblock_cuts),color=c.mid,style=SingularityTheme.type.label)
                }}
            }
        }
        if(destination=="storage")item(key="storage",contentType="settings-group"){
            SettingsGroup {
                Column(Modifier.padding(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(stringResource(R.string.library),color=c.mid,style=SingularityTheme.type.label);Text(Formatters.bytes(storedBytes),color=c.text,style=SingularityTheme.type.headline)}
                SettingsEntry(Icons.Rounded.CleaningServices,stringResource(R.string.clear_cache),subtitle=stringResource(R.string.settings_cache_body),onClick={model.clearCache(context)})
            }
        }
        if(destination=="about")item(key="about",contentType="settings-group"){
            SettingsGroup {
                SettingsEntry(Icons.Rounded.Code,stringResource(R.string.repository),onClick={external(context,"https://github.com/${BuildConfig.GITHUB_REPOSITORY}")})
                SettingDivider()
                SettingsEntry(Icons.Rounded.Description,stringResource(R.string.licenses),subtitle="NewPipeExtractor · GPL-3.0",onClick={external(context,"https://github.com/${BuildConfig.GITHUB_REPOSITORY}/blob/main/LICENSE")})
                Text(stringResource(R.string.legal),color=c.mid,style=SingularityTheme.type.label,modifier=Modifier.padding(vertical=12.dp))
            }
        }
    }
    }
    }
    }
    sheet?.let{kind->SingularitySheet({sheet=null}){
        if(kind!="quality")SheetHeading(stringResource(when(kind){"language"->R.string.preferred_language;"subtitle-language"->R.string.subtitles;else->R.string.remembered_channels}),{sheet=null})
        when(kind){
        "language"->LanguagePicker(settings.preferredAudioLanguage){language->model.change{it.copy(preferredAudioLanguage=language,rememberedAudioLanguage=null)};sheet=null}
        "subtitle-language"-> {SettingsEntry(Icons.Rounded.ClosedCaptionOff,stringResource(R.string.subtitles_off),onClick={model.change{it.copy(subtitleLanguage=null)};sheet=null});LanguagePicker(settings.subtitleLanguage ?: settings.preferredAudioLanguage){language->model.change{it.copy(subtitleLanguage=language)};sheet=null}}
        "quality"->{
            SheetHeading(stringResource(R.string.max_quality),{sheet=null})
            val values=listOf(720,1080,Int.MAX_VALUE)
            ChoiceGrid(values.map{if(it==Int.MAX_VALUE)stringResource(R.string.max)else "${it}p"},values.indexOf(settings.defaultMaxQuality)){i->model.change{it.copy(defaultMaxQuality=values[i])};sheet=null}
        }
        "channels"->if(channels.isEmpty())Text(stringResource(R.string.empty_channels),color=c.mid)else channels.forEach{pref->SettingsEntry(Icons.Rounded.Bookmark,all.firstOrNull{it.channelId==pref.channelId}?.channelName ?: pref.channelId.substringAfterLast('/'),subtitle=Locale.forLanguageTag(pref.languageTag).displayName,trailing={SingularityIcon(Icons.Rounded.Delete,stringResource(R.string.delete),{model.forget(pref.channelId)})})}
    }}}
}
@Composable private fun SettingsGroup(title:String="",content:@Composable ColumnScope.()->Unit) {
    val c=SingularityTheme.colors
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        if(title.isNotBlank())Text(title.uppercase(),color=c.mid,style=SingularityTheme.type.eyebrow,modifier=Modifier.padding(start=4.dp,bottom=4.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp,c.border,RoundedCornerShape(16.dp)).padding(horizontal=16.dp,vertical=4.dp),verticalArrangement=Arrangement.spacedBy(0.dp),content=content)
    }
}
@Composable private fun SettingDivider(){Box(Modifier.fillMaxWidth().height(1.dp).background(SingularityTheme.colors.hairline.copy(alpha=.5f)))}
@Composable private fun SettingToggle(title:String,subtitle:String,checked:Boolean,onChange:(Boolean)->Unit) {
    val c=SingularityTheme.colors;val haptic=LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth().heightIn(min=64.dp).toggleable(checked,role=Role.Switch){haptic.performHapticFeedback(HapticFeedbackType.ContextClick);onChange(it)}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(title,color=c.text,style=SingularityTheme.type.body);if(subtitle.isNotBlank())Text(subtitle,color=c.mid,style=SingularityTheme.type.label)}
        SingularitySwitch(checked,{},title,interactive=false)
    }
}
@Composable private fun SettingNumber(title:String,subtitle:String,value:Int,range:IntRange,onChange:(Int)->Unit) {
    val c=SingularityTheme.colors
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical=14.dp)) {
        val stacked=maxWidth<300.dp || LocalDensity.current.fontScale>1.2f
        val text: @Composable (Modifier)->Unit = { modifier ->
            Column(modifier,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(title,color=c.text,style=SingularityTheme.type.body)
                Text(subtitle,color=c.mid,style=SingularityTheme.type.label)
            }
        }
        val control: @Composable ()->Unit = {
            Row(Modifier.clip(RoundedCornerShape(12.dp)).background(c.high).border(1.dp,c.hairline,RoundedCornerShape(12.dp)),verticalAlignment=Alignment.CenterVertically) {
                SingularityIcon(Icons.Rounded.Remove,stringResource(R.string.settings_decrease,title),{onChange((value-1).coerceIn(range))},enabled=value>range.first)
                Text(value.toString(),color=c.text,style=SingularityTheme.type.strong.copy(fontFeatureSettings="tnum"),modifier=Modifier.widthIn(min=24.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                SingularityIcon(Icons.Rounded.Add,stringResource(R.string.settings_increase,title),{onChange((value+1).coerceIn(range))},enabled=value<range.last)
            }
        }
        if(stacked)Column(verticalArrangement=Arrangement.spacedBy(12.dp)){text(Modifier);control()}
        else Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){text(Modifier.weight(1f));control()}
    }
}

@Composable private fun SettingsEntry(icon:ImageVector,title:String,subtitle:String="",value:String="",onClick:(()->Unit)?=null,trailing:(@Composable ()->Unit)?=null) {
    val c=SingularityTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min=64.dp).clip(RoundedCornerShape(10.dp))
        .then(if(onClick!=null)Modifier.clickable(role=Role.Button,onClick=onClick)else Modifier)
        .padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        Icon(icon,null,tint=c.mid,modifier=Modifier.size(22.dp))
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(title,color=c.text,style=SingularityTheme.type.strong)
            if(subtitle.isNotBlank())Text(subtitle,color=c.mid,style=SingularityTheme.type.label)
            if(value.isNotBlank())Text(value,color=c.accent,style=SingularityTheme.type.label)
        }
        if(trailing!=null)trailing()else if(onClick!=null)Icon(Icons.Rounded.ChevronRight,null,tint=c.low,modifier=Modifier.size(20.dp))
    }
}
