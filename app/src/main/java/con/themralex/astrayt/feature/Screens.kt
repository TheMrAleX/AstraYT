package con.themralex.astrayt.feature
import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as SystemSettings
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.*
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import con.themralex.astrayt.R
import con.themralex.astrayt.BuildConfig
import con.themralex.astrayt.core.designsystem.*
import con.themralex.astrayt.core.util.*
import con.themralex.astrayt.data.db.*
import con.themralex.astrayt.data.prefs.Settings
import con.themralex.astrayt.youtube.*
import java.io.File
import java.util.Locale

fun external(context:Context,url:String){runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}}
fun share(context:Context,videos:List<VideoEntity>){context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,videos.joinToString("\n"){UrlParser.canonical(it.ytId)})},null))}
@Composable fun CaptureScreen(model:CaptureModel,settings:Settings,recent:List<VideoEntity>,onPlay:(VideoEntity)->Unit) {
    val state by model.state.collectAsStateWithLifecycle();val c=SingularityTheme.colors
    val clipboard=LocalClipboardManager.current;val context=LocalContext.current
    val storagePermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)model.enqueue()}
    fun enqueue(){if(Build.VERSION.SDK_INT<=28 && ContextCompat.checkSelfPermission(context,Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)else model.enqueue()}
    val details=state.result;val track=details?.tracks?.firstOrNull{it.id==state.trackId};val choices=details?.let{StreamPlanner.options(it,track,state.audioOnly)}.orEmpty();val selected=choices.firstOrNull{it.height==state.height} ?: choices.lastOrNull()
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.app_name),style=SingularityTheme.type.headline.copy(brush=c.linear),modifier=Modifier.padding(vertical=12.dp))
            Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally){BlackHoleHero(if(details==null)220.dp else 96.dp);if(details==null){Text(stringResource(R.string.capture_title),color=c.text,style=SingularityTheme.type.display);Text(stringResource(R.string.capture_body),color=c.mid,style=SingularityTheme.type.body,modifier=Modifier.padding(top=12.dp,bottom=16.dp))}}
            LinkField(state.url,{model.input(it)},stringResource(R.string.paste_hint),{model.input(state.url,true)},error=state.error?.let{stringResource(Errors.string(it))},loading=state.loading)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                SingularityButton(stringResource(R.string.paste),{clipboard.getText()?.text?.let{model.input(it,true)}},variant="secondary")
                if(state.url.isNotEmpty())SingularityButton(stringResource(if(state.error!=null)R.string.retry else R.string.resolve),{model.input(state.url,true)},Modifier.weight(1f),enabled=!state.loading)
            }
            if(state.loading){Skeleton(Modifier.fillMaxWidth().aspectRatio(16f/9));Skeleton(Modifier.fillMaxWidth().height(64.dp))}
            details?.let { d->
                MetadataCard(d.title,d.channelName,d.thumbnail,Formatters.duration(d.durationMs))
                GlassCard {
                    Text(stringResource(R.string.options),color=c.text,style=SingularityTheme.type.title)
                    SegmentedToggle(listOf(stringResource(R.string.video),stringResource(R.string.audio)),if(state.audioOnly)1 else 0,{model.kind(it==1)})
                    if(state.audioOnly)Tag(stringResource(R.string.best_audio)) else {
                        SectionHeader(stringResource(R.string.quality))
                        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){choices.forEach{q->QualityChip(q.label,if(q.estimatedBytes>0)"≈ ${Formatters.bytes(q.estimatedBytes)}"else stringResource(R.string.unknown_size),selected==q,{model.height(q.height)})}}
                    }
                    SectionHeader(stringResource(R.string.audio))
                    if(state.prompt){Text(stringResource(R.string.audio_tracks),color=c.text,style=SingularityTheme.type.small);Text(stringResource(R.string.choose_audio),color=c.mid,style=SingularityTheme.type.body)
                        AudioTrackSelector.sorted(d.tracks,settings.preferredAudioLanguage).forEach { t->val locale=Locale.forLanguageTag(t.locale);LanguageRow(locale.getDisplayLanguage(locale).ifBlank{t.displayName.ifBlank{context.getString(R.string.unknown_language)}},locale.getDisplayCountry(locale)+" · "+stringResource(when(t.type){"DUBBED"->R.string.dubbed;"DESCRIPTIVE"->R.string.descriptive;else->R.string.original}),t.locale,state.trackId==t.id,{model.track(t.id)})}
                        SettingsRow(Icons.Rounded.Bookmark,stringResource(R.string.remember_channel),trailing={SingularitySwitch(state.remember,model::remember,stringResource(R.string.remember_channel))})
                    }else{Row(verticalAlignment=Alignment.CenterVertically){Text(track?.displayName?.ifBlank{Locale.forLanguageTag(track.locale).displayLanguage}.orEmpty(),color=c.mid,modifier=Modifier.weight(1f));if(d.tracks.size>1)SingularityButton(stringResource(R.string.select_audio),model::showTracks,variant="tertiary")}}
                }
            }
            state.playlist?.let { list->
                GlassCard {Text(list.title,color=c.text,style=SingularityTheme.type.title);SingularityButton(stringResource(R.string.select_all),model::selectAll,variant="secondary")
                    list.entries.forEach { item->Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(48.dp).clickable{model.select(item.ytId)}.padding(12.dp).background(if(item.ytId in state.selected)c.accent else c.top,RoundedCornerShape(8.dp)));Box(Modifier.weight(1f)){VideoCardCompact(item.title,item.channel,item.thumbnail){model.select(item.ytId)}}}}
                }
            }
            if(details==null && recent.isNotEmpty()){SectionHeader(stringResource(R.string.recent_captures));LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)){items(recent.take(8),key={it.id},contentType={"shelf"}){v->VideoCardShelf(v.title,v.channelName,v.thumbnailPath,Formatters.duration(v.durationMs)){onPlay(v)}}}}
            Spacer(Modifier.height(16.dp))
        }
        if(details!=null || state.playlist!=null)SingularityButton(if(state.playlist!=null)stringResource(R.string.playlist_download,state.selected.size)else stringResource(R.string.download_size,selected?.estimatedBytes?.takeIf{it>0}?.let{Formatters.bytes(it)} ?: stringResource(R.string.unknown_size)),{enqueue()},Modifier.fillMaxWidth().padding(vertical=12.dp),enabled=if(state.playlist!=null)state.selected.isNotEmpty()else selected!=null,loading=state.busy)
    }
}
@Composable fun QueueScreen(model:QueueModel) {
    val rows by model.state.collectAsStateWithLifecycle();val progress by model.progress.collectAsStateWithLifecycle();val c=SingularityTheme.colors
    var menu by remember{mutableStateOf<DownloadEntity?>(null)}
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp)) {
        Text(stringResource(R.string.queue),color=c.text,style=SingularityTheme.type.headline,modifier=Modifier.padding(vertical=16.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){SingularityButton(stringResource(R.string.pause_all),{model.pauseAll()},variant="secondary");SingularityButton(stringResource(R.string.resume_all),{model.resumeAll()},variant="secondary");SingularityButton(stringResource(R.string.clear_done),{model.clear()},variant="tertiary")}
        if(rows.isEmpty())EmptyState(stringResource(R.string.empty_queue),stringResource(R.string.empty_queue_body))
        LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(vertical=16.dp)) {
            val groups=listOf("DOWNLOADING","FETCHING","MUXING","QUEUED","WAITING_NETWORK","PAUSED","FAILED","DONE","CANCELED")
            groups.forEach { group->val entries=rows.filter{it.state==group};if(entries.isNotEmpty()) {
                item(key="section-$group",contentType="header"){SectionHeader(stringResource(stateResource(group)))}
                items(entries,key={it.id},contentType={"download"}){row->val p=progress[row.id];GlassCard {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(row.title,color=c.text,style=SingularityTheme.type.strong,modifier=Modifier.weight(1f),maxLines=2)
                        AccretionRing(p?.fraction ?: if(row.bytesTotal>0)row.bytesDone.toFloat()/row.bytesTotal else 0f,48.dp,when(row.state){"PAUSED"->"paused";"FAILED"->"error";"DONE"->"done";"MUXING","FETCHING"->"indeterminate";else->"active"},Modifier.clickable{if(row.state in setOf("PAUSED","FAILED"))model.resume(row.id)else if(row.state in setOf("DOWNLOADING","FETCHING","MUXING","WAITING_NETWORK","QUEUED"))model.pause(row.id)})
                        SingularityIcon(Icons.Rounded.MoreVert,stringResource(R.string.options),{menu=row})
                    }
                    Text(row.channelName,color=c.mid,style=SingularityTheme.type.caption)
                    AccretionProgressBar(p?.fraction ?: if(row.bytesTotal>0)row.bytesDone.toFloat()/row.bytesTotal else 0f)
                    Text(if(row.state=="FAILED")stringResource(Errors.string(row.errorCode))else if(p!=null && row.state=="DOWNLOADING")"${(p.fraction*100).toInt()} % · ${Formatters.bytes(p.speed)}/s · ${Formatters.duration(p.eta.coerceAtLeast(0)*1000)}"else stringResource(stateResource(row.state)),color=if(row.state=="FAILED")c.error else c.mid,style=SingularityTheme.type.caption)
                    if(row.state=="FAILED")SingularityButton(stringResource(R.string.retry),{model.resume(row.id)},variant="tertiary")
                }}
            }}
        }
    }
    menu?.let{row->SingularitySheet({menu=null}){SingularityButton(stringResource(if(row.state in setOf("PAUSED","FAILED"))R.string.resume else R.string.pause),{if(row.state in setOf("PAUSED","FAILED"))model.resume(row.id)else model.pause(row.id);menu=null});SingularityButton(stringResource(R.string.priority),{model.priority(row.id);menu=null},variant="secondary");SingularityButton(stringResource(R.string.cancel),{model.cancel(row.id);menu=null},variant="tertiary")}}
}
fun stateResource(state:String)=when(state){"FETCHING"->R.string.fetching;"DOWNLOADING"->R.string.downloading;"MUXING"->R.string.muxing;"WAITING_NETWORK"->R.string.waiting_network;"PAUSED"->R.string.paused;"DONE"->R.string.done;"FAILED"->R.string.failed;"CANCELED"->R.string.canceled;else->R.string.queued}
@Composable fun LibraryScreen(model:LibraryModel,onPlay:(VideoEntity,List<VideoEntity>)->Unit,onCapture:()->Unit,onChannel:(String)->Unit) {
    val rows by model.state.collectAsStateWithLifecycle();val search by model.search.collectAsStateWithLifecycle();val all by model.all.collectAsStateWithLifecycle();val c=SingularityTheme.colors;val context=LocalContext.current
    var filter by rememberSaveable{mutableIntStateOf(0)};var sort by rememberSaveable{mutableIntStateOf(0)};var searching by rememberSaveable{mutableStateOf(false)};var sorting by remember{mutableStateOf(false)};var menu by remember{mutableStateOf<VideoEntity?>(null)};var deleting by remember{mutableStateOf<VideoEntity?>(null)}
    val channelList=all.groupBy{it.channelId}.entries.sortedByDescending{it.value.size}.take(12)
    val labels=listOf(stringResource(R.string.all),stringResource(R.string.videos),stringResource(R.string.audio),stringResource(R.string.unwatched))+channelList.map{it.value.first().channelName}
    val filtered=rows.filter{v->when(filter){0->true;1->v.kind=="VIDEO";2->v.kind=="AUDIO";3->v.positionMs<v.durationMs*.95;else->v.channelId==channelList.getOrNull(filter-4)?.key}}
    val visible=when(sort){1->filtered.sortedBy{it.title.lowercase()};2->filtered.sortedByDescending{it.durationMs};3->filtered.sortedByDescending{it.fileSizeBytes};4->filtered.sortedBy{it.channelName};else->filtered}
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically){Text(stringResource(R.string.library),color=c.text,style=SingularityTheme.type.headline,modifier=Modifier.weight(1f));SingularityIcon(Icons.Rounded.Search,stringResource(R.string.search),{searching=!searching;if(!searching)model.search("")});Spacer(Modifier.width(8.dp));SingularityIcon(Icons.Rounded.Sort,stringResource(R.string.sort),{sorting=true})}
        if(searching)LinkField(search,model::search,stringResource(R.string.search),{model.rememberSearch()})
        FilterChipRow(labels,filter,{filter=it})
        if(all.isEmpty())EmptyState(stringResource(R.string.empty_library),stringResource(R.string.empty_library_body),stringResource(R.string.capture_video),onCapture)
        LazyColumn(verticalArrangement=Arrangement.spacedBy(20.dp)) {
            val continued=all.filter{it.positionMs>it.durationMs*.03 && it.positionMs<it.durationMs*.95}
            if(continued.isNotEmpty() && search.isBlank() && filter==0)item(key="continue",contentType="shelf"){Column{SectionHeader(stringResource(R.string.continue_watching));LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)){items(continued,key={it.id},contentType={"shelf"}){v->VideoCardShelf(v.title,v.channelName,v.thumbnailPath,Formatters.duration(v.durationMs)){onPlay(v,all)}}}}}
            items(visible,key={it.id},contentType={it.kind}){v->VideoCard(v.title,"${v.channelName} · ${Formatters.bytes(v.fileSizeBytes)}",v.thumbnailPath,Formatters.duration(v.durationMs),if(v.durationMs>0)v.positionMs.toFloat()/v.durationMs else 0f,{onPlay(v,all)},{menu=v})}
            item(key="summary",contentType="footer"){Text(stringResource(R.string.library_summary,all.size,Formatters.bytes(all.sumOf{it.fileSizeBytes})),color=c.low,style=SingularityTheme.type.caption,modifier=Modifier.padding(vertical=16.dp))}
        }
    }
    if(sorting)SingularitySheet({sorting=false}){listOf(R.string.recent,R.string.title,R.string.duration,R.string.size,R.string.channel).forEachIndexed{i,id->SingularityButton(stringResource(id),{sort=i;sorting=false},variant=if(i==sort)"primary"else"secondary")}}
    menu?.let{v->SingularitySheet({menu=null}){listOf(R.string.play,R.string.audio_only,R.string.share,R.string.open_youtube,R.string.channel,R.string.delete).forEach{id->SingularityButton(stringResource(id),{when(id){R.string.play,R.string.audio_only->onPlay(v,all);R.string.share->share(context,listOf(v));R.string.open_youtube->external(context,UrlParser.canonical(v.ytId));R.string.channel->onChannel(v.channelId);R.string.delete->deleting=v};menu=null},variant="secondary")}}}
    deleting?.let{v->SingularityDialog(stringResource(R.string.delete),stringResource(R.string.delete_question),stringResource(R.string.delete),stringResource(R.string.cancel),{model.delete(v);deleting=null},{deleting=null})}
}
@Composable fun ChannelScreen(id:String,all:List<VideoEntity>,onPlay:(VideoEntity,List<VideoEntity>)->Unit) {
    val rows=all.filter{it.channelId==id};val c=SingularityTheme.colors
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item(key="channel",contentType="header"){Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.fillMaxWidth()){BlackHoleHero(120.dp,false);Text(rows.firstOrNull()?.channelName.orEmpty(),color=c.text,style=SingularityTheme.type.headline);Text(stringResource(R.string.library_summary,rows.size,Formatters.bytes(rows.sumOf{it.fileSizeBytes})),color=c.mid);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){SingularityButton(stringResource(R.string.play_all),{rows.firstOrNull()?.let{onPlay(it,rows)}});SingularityButton(stringResource(R.string.shuffle),{val shuffled=rows.shuffled();shuffled.firstOrNull()?.let{onPlay(it,shuffled)}},variant="secondary")}}}
        items(rows,key={it.id},contentType={it.kind}){v->VideoCardCompact(v.title,Formatters.duration(v.durationMs),v.thumbnailPath){onPlay(v,rows)}}
    }
}
@Composable fun SettingsScreen(model:SettingsModel,all:List<VideoEntity>) {
    val settings by model.state.collectAsStateWithLifecycle();val channels by model.channels.collectAsStateWithLifecycle();val c=SingularityTheme.colors;val context=LocalContext.current
    var sheet by remember{mutableStateOf<String?>(null)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.settings),color=c.text,style=SingularityTheme.type.headline)
        SectionHeader(stringResource(R.string.audio))
        GlassCard {
            SettingsRow(Icons.Rounded.Language,stringResource(R.string.preferred_language),value=Locale.forLanguageTag(settings.preferredAudioLanguage).displayLanguage,onClick={sheet="language"})
            SettingsRow(Icons.Rounded.QuestionAnswer,stringResource(R.string.ask_alternatives),trailing={SingularitySwitch(settings.askWhenAlternatives,{v->model.change{it.copy(askWhenAlternatives=v)}},stringResource(R.string.ask_alternatives))})
            SettingsRow(Icons.Rounded.Translate,stringResource(R.string.auto_dub),trailing={SingularitySwitch(settings.autoPickDubbed,{v->model.change{it.copy(autoPickDubbed=v)}},stringResource(R.string.auto_dub))})
            SettingsRow(Icons.Rounded.Bookmark,stringResource(R.string.remembered_channels),value=channels.size.toString(),onClick={sheet="channels"})
        }
        SectionHeader(stringResource(R.string.downloads))
        GlassCard {
            SettingsRow(Icons.Rounded.HighQuality,stringResource(R.string.max_quality),value=if(settings.defaultMaxQuality==Int.MAX_VALUE)stringResource(R.string.max)else "${settings.defaultMaxQuality}p",onClick={sheet="quality"})
            SingularitySlider(settings.maxConcurrentDownloads,1..5,stringResource(R.string.concurrent)){v->model.change{it.copy(maxConcurrentDownloads=v)}}
            SingularitySlider(settings.connectionsPerStream,1..6,stringResource(R.string.connections)){v->model.change{it.copy(connectionsPerStream=v)}}
            SettingsRow(Icons.Rounded.Wifi,stringResource(R.string.wifi_only),trailing={SingularitySwitch(settings.wifiOnly,{v->model.change{it.copy(wifiOnly=v)}},stringResource(R.string.wifi_only))})
            SettingsRow(Icons.Rounded.Folder,stringResource(R.string.folder),subtitle="Movies/AstraYT · Music/AstraYT")
            SettingsRow(Icons.Rounded.BatteryChargingFull,stringResource(R.string.battery),onClick={runCatching{context.startActivity(Intent(SystemSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))}})
        }
        SectionHeader(stringResource(R.string.appearance))
        GlassCard {
            SettingsRow(Icons.Rounded.DarkMode,stringResource(R.string.amoled),trailing={SingularitySwitch(settings.amoledPureBlack,{v->model.change{it.copy(amoledPureBlack=v)}},stringResource(R.string.amoled))})
            SettingsRow(Icons.Rounded.Animation,stringResource(R.string.animations),trailing={SingularitySwitch(settings.animationsEnabled,{v->model.change{it.copy(animationsEnabled=v)}},stringResource(R.string.animations))})
            SettingsRow(Icons.Rounded.Palette,stringResource(R.string.accent),value=settings.accentPreset,onClick={sheet="accent"})
        }
        SectionHeader(stringResource(R.string.storage))
        GlassCard {Text(Formatters.bytes(all.sumOf{it.fileSizeBytes}),color=c.text);SingularityButton(stringResource(R.string.clear_cache),{File(context.cacheDir,"image_cache").takeIf{it.exists()}?.deleteRecursively();model.work{model.effects.send(R.string.cache_cleared)}},variant="secondary")}
        SectionHeader(stringResource(R.string.about))
        GlassCard {
            Text("AstraYT ${BuildConfig.VERSION_NAME}",color=c.text,style=SingularityTheme.type.title)
            SettingsRow(Icons.Rounded.SystemUpdate,stringResource(R.string.updates),onClick={model.checkUpdate()})
            SettingsRow(Icons.Rounded.Code,stringResource(R.string.repository),onClick={external(context,"https://github.com/${BuildConfig.GITHUB_REPOSITORY}")})
            SettingsRow(Icons.Rounded.Description,stringResource(R.string.licenses),subtitle="NewPipeExtractor · GPL-3.0",onClick={external(context,"https://github.com/${BuildConfig.GITHUB_REPOSITORY}/blob/main/LICENSE")})
            Text(stringResource(R.string.legal),color=c.mid,style=SingularityTheme.type.body)
        }
    }
    sheet?.let{type->SingularitySheet({sheet=null}){when(type){
        "language"->LanguagePicker(settings.preferredAudioLanguage){language->model.change{it.copy(preferredAudioLanguage=language)};sheet=null}
        "quality"->listOf(720,1080,Int.MAX_VALUE).forEach{quality->SingularityButton(if(quality==Int.MAX_VALUE)stringResource(R.string.max)else "${quality}p",{model.change{it.copy(defaultMaxQuality=quality)};sheet=null},variant=if(quality==settings.defaultMaxQuality)"primary"else"secondary")}
        "accent"->listOf("EMBER","PLASMA","BLUESHIFT").forEach{preset->SingularityButton(preset,{model.change{it.copy(accentPreset=preset)};sheet=null},variant=if(preset==settings.accentPreset)"primary"else"secondary")}
        "channels"->if(channels.isEmpty())Text(stringResource(R.string.empty_channels),color=c.mid)else channels.forEach{pref->SettingsRow(Icons.Rounded.Bookmark,all.firstOrNull{it.channelId==pref.channelId}?.channelName ?: pref.channelId,subtitle=pref.languageTag,onClick={model.forget(pref.channelId)},trailing={SingularityIcon(Icons.Rounded.Delete,stringResource(R.string.delete),{model.forget(pref.channelId)})})}
    }}}
}
@Composable fun LanguagePicker(selected:String,onSelect:(String)->Unit) {
    var search by remember{mutableStateOf("")};val locales=remember{val common=listOf(Locale.getDefault().language,"es","en","pt","fr","de","it","ja","ko","zh","ru","ar","hi");(common+Locale.getAvailableLocales().map{it.language}).filter{it.isNotBlank()}.distinct().map{Locale.forLanguageTag(it)}}
    LinkField(search,{search=it},stringResource(R.string.search),{})
    LazyColumn(Modifier.heightIn(max=360.dp)){items(locales.filter{it.getDisplayLanguage(it).contains(search,true)||it.displayLanguage.contains(search,true)},key={it.language},contentType={"language"}){locale->LanguageRow(locale.getDisplayLanguage(locale),locale.displayLanguage,locale.language,locale.language==selected,{onSelect(locale.language)})}}
}
@Composable fun OnboardingScreen(model:SettingsModel) {
    val settings by model.state.collectAsStateWithLifecycle();var page by rememberSaveable{mutableIntStateOf(0)};val c=SingularityTheme.colors
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
    BackHandler(page>0){page--}
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){repeat(3){segment->AccretionProgressBar(if(segment<=page)1f else 0f,Modifier.weight(1f),3.dp)}}
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
            when(page){0->{BlackHoleHero(260.dp);Text(stringResource(R.string.welcome),color=c.text,style=SingularityTheme.type.display);Text(stringResource(R.string.welcome_body),color=c.mid,style=SingularityTheme.type.body)}
            1->{Text(stringResource(R.string.your_language),color=c.text,style=SingularityTheme.type.headline);LanguagePicker(settings.preferredAudioLanguage){language->model.change{it.copy(preferredAudioLanguage=language)}};SectionHeader(stringResource(R.string.max_quality));Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(720,1080,Int.MAX_VALUE).forEach{q->QualityChip(if(q==Int.MAX_VALUE)stringResource(R.string.max)else "${q}p","",settings.defaultMaxQuality==q,{model.change{it.copy(defaultMaxQuality=q)}})}}}
            2->{BlackHoleHero(160.dp);Text(stringResource(R.string.notifications_title),color=c.text,style=SingularityTheme.type.headline);Text(stringResource(R.string.notifications_body),color=c.mid,style=SingularityTheme.type.body);if(Build.VERSION.SDK_INT>=33)SingularityButton(stringResource(R.string.allow_notifications),{permission.launch(Manifest.permission.POST_NOTIFICATIONS)},variant="secondary")}}
        }
        SingularityButton(stringResource(if(page==2)R.string.enter else R.string.next),{if(page<2)page++ else model.change{it.copy(onboardingDone=true)}},Modifier.fillMaxWidth())
    }
}
