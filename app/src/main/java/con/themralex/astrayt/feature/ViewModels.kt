package con.themralex.astrayt.feature
import androidx.lifecycle.*
import androidx.paging.cachedIn
import androidx.compose.runtime.Immutable
import con.themralex.astrayt.R
import con.themralex.astrayt.BuildConfig
import con.themralex.astrayt.core.util.Errors
import con.themralex.astrayt.core.util.ReleaseAsset
import con.themralex.astrayt.core.util.ReleaseAssetSelector
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
@Immutable data class CaptureState(val url:String="",val loading:Boolean=false,val result:VideoDetails?=null,val playlist:ResolvedLink.Playlist?=null,val selected:Set<String> = emptySet(),val error:String?=null,val audioOnly:Boolean=false,val trackId:String?=null,val height:Int=1080,val prompt:Boolean=false,val remember:Boolean=false,val busy:Boolean=false,val loadingMore:Boolean=false)
@HiltViewModel class CaptureModel @Inject constructor(private val saved:SavedStateHandle,private val backend:YoutubeBackend,private val prefs:Preferences,private val dao:AstraDao,private val downloads:DownloadManager):ScreenModel() {
    private val _state=MutableStateFlow(CaptureState(url=saved["url"] ?: ""))
    val state=_state.asStateFlow()
    private var fetch:Job?=null
    init {work{val p=prefs.flow.first();if(_state.value.url.isBlank())_state.value=_state.value.copy(height=p.defaultMaxQuality)};if(_state.value.url.isNotBlank())input(_state.value.url)}
    fun input(url:String,immediate:Boolean=false){saved["url"]=url;fetch?.cancel();_state.value=CaptureState(url=url,height=_state.value.height,audioOnly=_state.value.audioOnly);if(url.isBlank())return
        fetch=work {
            if(!immediate)delay(400)
            if(UrlParser.parse(url)==null){_state.value=_state.value.copy(error="URL");return@work}
            _state.value=_state.value.copy(loading=true)
            try {when(val resolved=backend.resolve(url)) {
                is ResolvedLink.Video->{val p=prefs.flow.first();val remembered=dao.channelLanguage(resolved.details.channelId);val selected=AudioTrackSelector.select(resolved.details.tracks,p.preferredAudioLanguage,remembered,p.autoPickDubbed,p.askWhenAlternatives);_state.value=_state.value.copy(loading=false,result=resolved.details,trackId=selected.track?.id,prompt=selected.prompt)}
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
    fun loadMore()=work {
        val current=_state.value
        val playlist=current.playlist ?: return@work
        if(current.loadingMore || !playlist.hasMore || current.busy)return@work
        _state.value=current.copy(loadingMore=true)
        try {
            val next=backend.morePlaylist(playlist.url)
            if(_state.value.playlist?.url!=playlist.url)return@work
            val selected=if(playlist.entries.all{it.ytId in current.selected})current.selected+next.entries.map{it.ytId}else current.selected
            _state.value=_state.value.copy(playlist=playlist.copy(entries=(playlist.entries+next.entries).distinctBy{it.ytId}.take(200),hasMore=next.hasMore),selected=selected)
        }finally{_state.value=_state.value.copy(loadingMore=false)}
    }
    fun enqueue()=work {
        val state=_state.value;if(state.busy)return@work;_state.value=state.copy(busy=true)
        try {
            if(state.playlist!=null){
                val failed=mutableSetOf<String>()
                for(entry in state.playlist.entries.filter{it.ytId in state.selected}){
                    try{downloads.enqueue(entry.ytId,state.height,state.audioOnly,title=entry.title,channel=entry.channel,thumbnail=entry.thumbnail)}
                    catch(e:CancellationException){throw e}catch(_:Exception){failed.add(entry.ytId)}
                }
                if(failed.isNotEmpty()){_state.value=_state.value.copy(selected=failed);effects.send(R.string.batch_partial);return@work}
            }
            else {
                val link=UrlParser.parse(state.url) as? YoutubeLink.Video ?: return@work
                val d=state.result;val t=d?.tracks?.firstOrNull{it.id==state.trackId}
                if(state.remember && t!=null)dao.remember(ChannelAudioPref(d.channelId,t.locale))
                downloads.enqueue(link.id,state.height,state.audioOnly,d,state.trackId)
            }
            fetch?.cancel();saved["url"]="";_state.value=CaptureState(height=state.height,audioOnly=state.audioOnly);effects.send(R.string.added_queue)
        }finally{_state.value=_state.value.copy(busy=false)}
    }
}
@HiltViewModel class QueueModel @Inject constructor(dao:AstraDao,val manager:DownloadManager):ScreenModel() {
    val state=dao.downloads().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val progress=manager.progress
    private val _pending=MutableStateFlow(setOf<Long>())
    val pending=_pending.asStateFlow()
    private val undoJobs=mutableMapOf<Long,Job>()
    fun cancelWithUndo(id:Long){undoJobs.remove(id)?.cancel();_pending.value=_pending.value+id;undoJobs[id]=work{delay(4000);manager.cancel(id);_pending.value=_pending.value-id;undoJobs.remove(id)}}
    fun undoCancel(id:Long){undoJobs.remove(id)?.cancel();_pending.value=_pending.value-id}
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
    private data class Filter(val kind:String?=null,val unwatched:Boolean=false,val channel:String?=null,val sort:Int=0)
    private val filter=MutableStateFlow(Filter())
    val recent=prefs.flow.map{it.recentSearches}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val paged=combine(query,filter){query,filter->query to filter}.debounce(150).flatMapLatest{(text,filter)->
        val tokens=text.split(Regex("""[^\p{L}\p{N}]+""")).filter{it.isNotBlank()}
        val where=mutableListOf<String>();val args=mutableListOf<Any>()
        val join=if(tokens.isEmpty())""else" JOIN video_fts ON videos.id=video_fts.rowid"
        if(tokens.isNotEmpty()){where.add("video_fts MATCH ?");args.add(tokens.joinToString(" AND "){"\"$it\"*"})}
        filter.kind?.let{where.add("videos.kind = ?");args.add(it)}
        if(filter.unwatched)where.add("positionMs < durationMs * 0.95")
        filter.channel?.let{where.add("channelId = ?");args.add(it)}
        val order=when(filter.sort){1->"title COLLATE NOCASE ASC";2->"durationMs DESC";3->"fileSizeBytes DESC";4->"channelName COLLATE NOCASE ASC";else->"addedAtMs DESC"}
        val sql="SELECT videos.* FROM videos"+join+(if(where.isEmpty())""else" WHERE "+where.joinToString(" AND "))+" ORDER BY "+order
        androidx.paging.Pager(androidx.paging.PagingConfig(pageSize=20,enablePlaceholders=false)){dao.pagingQuery(androidx.sqlite.db.SimpleSQLiteQuery(sql,args.toTypedArray()))}.flow
    }.cachedIn(viewModelScope)
    fun configure(kind:String?,unwatched:Boolean,channel:String?,sort:Int){filter.value=Filter(kind,unwatched,channel,sort)}
    fun clearSearches()=work{prefs.update{it.copy(recentSearches=emptyList())}}
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
        val check=withContext(Dispatchers.IO) {
            client.newCall(Request.Builder().url("https://api.github.com/repos/${BuildConfig.GITHUB_REPOSITORY}/releases/latest").header("Accept","application/vnd.github+json").build()).execute().use { response->
                if(response.code==404)return@use false to null
                if(!response.isSuccessful)throw java.io.IOException("HTTP ${response.code}")
                val root=Json.parseToJsonElement(response.body.string()).jsonObject
                val remote=root["tag_name"]?.jsonPrimitive?.content.orEmpty().removePrefix("v").split('.').mapNotNull{it.toIntOrNull()}
                val local=BuildConfig.VERSION_NAME.split('.').mapNotNull{it.toIntOrNull()}
                val newer=(0 until maxOf(remote.size,local.size)).firstOrNull{remote.getOrElse(it){0}!=local.getOrElse(it){0}}?.let{remote.getOrElse(it){0}>local.getOrElse(it){0}} ?: false
                val assets=root["assets"]?.jsonArray.orEmpty().mapNotNull { entry->
                    val name=entry.jsonObject["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val url=entry.jsonObject["browser_download_url"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    ReleaseAsset(name,url)
                }
                newer to if(newer)ReleaseAssetSelector.select(assets,android.os.Build.SUPPORTED_ABIS.toList())?.url else null
            }
        }
        _update.value=check.second
        prefs.update{it.copy(lastUpdateCheck=System.currentTimeMillis())}
        effects.send(when { !check.first->R.string.up_to_date;check.second==null->R.string.no_compatible_update;else->R.string.update_available })
    }

}
@HiltViewModel class MainModel @Inject constructor(val playback:Playback,private val downloads:DownloadManager,private val dao:AstraDao):ScreenModel() {
    init{work{downloads.initialize()}}
    fun play(video:VideoEntity,playlist:List<VideoEntity>,audioOnly:Boolean=false)=work{playback.play(video,playlist.ifEmpty{listOf(video)},audioOnly)}
    fun playDownloaded(id:Long)=work{val row=dao.download(id) ?: return@work;val all=dao.allVideos();all.firstOrNull{it.ytId==row.ytId}?.let{playback.play(it,all)}}
    fun toggle()=work{playback.toggle()}
    fun close()=work{playback.close()}
    fun save()=work{playback.save()}
}
