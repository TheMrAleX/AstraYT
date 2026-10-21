package con.themralex.astrayt.feature
import androidx.lifecycle.*
import androidx.compose.runtime.Immutable
import con.themralex.astrayt.R
import con.themralex.astrayt.BuildConfig
import con.themralex.astrayt.core.util.Errors
import con.themralex.astrayt.data.db.*
import con.themralex.astrayt.data.prefs.*
import con.themralex.astrayt.download.*
import con.themralex.astrayt.youtube.*
import con.themralex.astrayt.feature.player.Playback
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import okhttp3.*
import javax.inject.Inject

abstract class ScreenModel:ViewModel() {
    val effects=Channel<Int>(Channel.BUFFERED)
    protected val handler=CoroutineExceptionHandler{_,error->effects.trySend(Errors.string(Errors.code(error)))}
    fun work(block:suspend CoroutineScope.()->Unit)=viewModelScope.launch(handler,block=block)
}
@Immutable data class CaptureState(val url:String="",val loading:Boolean=false,val result:VideoDetails?=null,val playlist:ResolvedLink.Playlist?=null,val selected:Set<String> = emptySet(),val error:String?=null,val audioOnly:Boolean=false,val trackId:String?=null,val height:Int=1080,val prompt:Boolean=false,val remember:Boolean=false,val busy:Boolean=false)
@HiltViewModel class CaptureModel @Inject constructor(private val saved:SavedStateHandle,private val backend:YoutubeBackend,private val prefs:Preferences,private val dao:AstraDao,private val downloads:DownloadManager):ScreenModel() {
    private val _state=MutableStateFlow(CaptureState(url=saved["url"] ?: ""))
    val state=_state.asStateFlow()
    private var fetch:Job?=null
    init {if(_state.value.url.isNotBlank())input(_state.value.url)}
    fun input(url:String,immediate:Boolean=false){saved["url"]=url;fetch?.cancel();_state.value=CaptureState(url=url);if(url.isBlank())return
        fetch=work {
            if(!immediate)delay(400)
            if(UrlParser.parse(url)==null){_state.value=_state.value.copy(error="URL");return@work}
            _state.value=_state.value.copy(loading=true)
            try {when(val resolved=backend.resolve(url)) {
                is ResolvedLink.Video->{val p=prefs.flow.first();val remembered=dao.channelLanguage(resolved.details.channelId);val selected=AudioTrackSelector.select(resolved.details.tracks,p.preferredAudioLanguage,remembered,p.autoPickDubbed,p.askWhenAlternatives);val quality=StreamPlanner.default(StreamPlanner.options(resolved.details,selected.track),p.defaultMaxQuality);_state.value=_state.value.copy(loading=false,result=resolved.details,trackId=selected.track?.id,height=quality?.height ?: 0,prompt=selected.prompt)}
                is ResolvedLink.Playlist->_state.value=_state.value.copy(loading=false,playlist=resolved,selected=resolved.entries.map{it.ytId}.toSet())
            }}catch(e:CancellationException){throw e}catch(e:Exception){_state.value=_state.value.copy(loading=false,error=Errors.code(e))}
        }
    }
    fun track(id:String){_state.value=_state.value.copy(trackId=id)}
    fun kind(audio:Boolean){_state.value=_state.value.copy(audioOnly=audio)}
    fun height(height:Int){_state.value=_state.value.copy(height=height)}
    fun remember(value:Boolean){_state.value=_state.value.copy(remember=value)}
    fun showTracks(){_state.value=_state.value.copy(prompt=true)}
    fun select(id:String){_state.value=_state.value.copy(selected=if(id in _state.value.selected)_state.value.selected-id else _state.value.selected+id)}
    fun selectAll(){_state.value=_state.value.copy(selected=_state.value.playlist?.entries?.map{it.ytId}?.toSet().orEmpty())}
    fun enqueue()=work {
        val state=_state.value;_state.value=state.copy(busy=true)
        try {
            val p=prefs.flow.first()
            if(state.playlist!=null){for(entry in state.playlist.entries.filter{it.ytId in state.selected}){val d=backend.details(entry.ytId);val t=AudioTrackSelector.select(d.tracks,p.preferredAudioLanguage,dao.channelLanguage(d.channelId),p.autoPickDubbed,p.askWhenAlternatives,batch=true).track;val q=StreamPlanner.default(StreamPlanner.options(d,t,state.audioOnly),p.defaultMaxQuality) ?: continue;downloads.enqueue(d,t,q,state.audioOnly)}}
            else {val d=state.result ?: return@work;val t=d.tracks.firstOrNull{it.id==state.trackId};val options=StreamPlanner.options(d,t,state.audioOnly);val q=options.firstOrNull{it.height==state.height} ?: options.lastOrNull() ?: return@work;if(state.remember && t!=null)dao.remember(ChannelAudioPref(d.channelId,t.locale));downloads.enqueue(d,t,q,state.audioOnly)}
            saved["url"]="";_state.value=CaptureState();effects.send(R.string.added_queue)
        }finally{_state.value=_state.value.copy(busy=false)}
    }
}
@HiltViewModel class QueueModel @Inject constructor(dao:AstraDao,val manager:DownloadManager):ScreenModel() {
    val state=dao.downloads().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val progress=manager.progress
    init{work{manager.initialize()}}
    fun pause(id:Long)=work{manager.pause(id)}
    fun resume(id:Long)=work{manager.resume(id)}
    fun cancel(id:Long)=work{manager.cancel(id)}
    fun priority(id:Long)=work{manager.prioritize(id)}
    fun pauseAll()=work{manager.pauseAll()}
    fun resumeAll()=work{manager.resumeAll()}
    fun clear()=work{manager.clearFinished()}
}
@HiltViewModel class LibraryModel @Inject constructor(private val dao:AstraDao,private val storage:MediaStorage,private val prefs:Preferences):ScreenModel() {
    private val query=MutableStateFlow("")
    val search=query.asStateFlow()
    val state=query.debounce(150).flatMapLatest{q->val tokens=q.split(Regex("[^\\p{L}\\p{N}]+" )).filter{it.isNotBlank()};if(tokens.isEmpty())dao.videos()else dao.search(tokens.joinToString(" AND "){"\"${it.replace("\"", "")}\"*"})}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val all=dao.videos().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    init{work{storage.pruneMissing()}}
    fun search(text:String){query.value=text}
    fun rememberSearch()=work{val q=query.value.trim();if(q.isNotBlank())prefs.update{it.copy(recentSearches=(listOf(q)+it.recentSearches).distinct().take(8))}}
    fun delete(video:VideoEntity)=work{storage.delete(video)}
}
@HiltViewModel class SettingsModel @Inject constructor(private val prefs:Preferences,private val dao:AstraDao,private val client:OkHttpClient):ScreenModel() {
    val state=prefs.flow.stateIn(viewModelScope,SharingStarted.Eagerly,Settings())
    val channels=dao.channels().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    private val _update=MutableStateFlow<String?>(null);val update=_update.asStateFlow()
    fun change(transform:(Settings)->Settings)=work{prefs.update(transform)}
    fun forget(id:String)=work{dao.forget(id)}
    fun checkUpdate()=work {
        val url=withContext(Dispatchers.IO){client.newCall(Request.Builder().url("https://api.github.com/repos/${BuildConfig.GITHUB_REPOSITORY}/releases/latest").header("Accept","application/vnd.github+json").build()).execute().use{response->if(response.code==404)return@use null;if(!response.isSuccessful)throw java.io.IOException("HTTP ${response.code}");val root=Json.parseToJsonElement(response.body.string()).jsonObject;val remote=root["tag_name"]?.jsonPrimitive?.content.orEmpty().removePrefix("v").split('.').mapNotNull{it.toIntOrNull()};val local=BuildConfig.VERSION_NAME.split('.').mapNotNull{it.toIntOrNull()};val newer=(0 until maxOf(remote.size,local.size)).firstOrNull{remote.getOrElse(it){0}!=local.getOrElse(it){0}}?.let{remote.getOrElse(it){0}>local.getOrElse(it){0}} ?: false;if(newer)root["assets"]?.jsonArray?.firstOrNull{it.jsonObject["name"]?.jsonPrimitive?.content?.endsWith(".apk")==true}?.jsonObject?.get("browser_download_url")?.jsonPrimitive?.content else null}}
        _update.value=url;prefs.update{it.copy(lastUpdateCheck=System.currentTimeMillis())};effects.send(if(url==null)R.string.up_to_date else R.string.update_available)
    }
}
@HiltViewModel class MainModel @Inject constructor(val playback:Playback,private val downloads:DownloadManager,private val dao:AstraDao):ScreenModel() {
    init{work{downloads.initialize()}}
    fun play(video:VideoEntity,playlist:List<VideoEntity>)=work{playback.play(video,playlist.ifEmpty{listOf(video)})}
    fun playDownloaded(id:Long)=work{val row=dao.download(id) ?: return@work;val all=dao.allVideos();all.firstOrNull{it.ytId==row.ytId}?.let{playback.play(it,all)}}
    fun toggle()=work{playback.toggle()}
    fun close()=work{playback.close()}
    fun save()=work{playback.save()}
}
