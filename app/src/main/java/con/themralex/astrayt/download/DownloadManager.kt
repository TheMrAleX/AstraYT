package con.themralex.astrayt.download
import android.content.*
import android.net.*
import android.os.PowerManager
import androidx.core.content.ContextCompat
import con.themralex.astrayt.data.db.*
import con.themralex.astrayt.data.prefs.*
import con.themralex.astrayt.youtube.*
import con.themralex.astrayt.core.util.Errors
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.*
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import okhttp3.*
import java.io.*
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.*
import kotlin.random.Random

@kotlinx.serialization.Serializable data class DownloadOptions(val details:VideoDetails,val trackId:String?,val height:Int,val container:String,val audioOnly:Boolean)
data class Progress(val done:Long,val total:Long,val speed:Long,val eta:Long) {val fraction get()=if(total>0)done.toFloat()/total else 0f}
private class StreamExpired:IOException()
@Singleton class DownloadManager @Inject constructor(@ApplicationContext private val context:Context,private val dao:AstraDao,private val preferences:Preferences,private val backend:YoutubeBackend,private val client:OkHttpClient,private val storage:MediaStorage) {
    private val handler=CoroutineExceptionHandler{_,_->}
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO+handler)
    private val active=ConcurrentHashMap<Long,Job>()
    private val calls=ConcurrentHashMap<Long,MutableSet<Call>>()
    private val counters=ConcurrentHashMap<Long,Pair<AtomicLong,AtomicLong>>()
    private val _progress=MutableStateFlow<Map<Long,Progress>>(emptyMap())
    val progress:StateFlow<Map<Long,Progress>> = _progress.asStateFlow()
    private val network=MutableStateFlow(false)
    private val initialization=Mutex()
    private val scheduler=Mutex()
    private var ready=false
    private val cooldown=AtomicLong(0)
    private val connectivity=context.getSystemService(ConnectivityManager::class.java)
    private fun online(wifiOnly:Boolean):Boolean {val caps=connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false;return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) && (!wifiOnly || !connectivity.isActiveNetworkMetered)}
    suspend fun initialize()=initialization.withLock {
        if(ready)return@withLock
        dao.pauseInterrupted()
        val known=dao.allDownloads().map{it.id.toString()}.toSet()
        File(context.noBackupFilesDir,"dl").listFiles()?.filter{it.name !in known}?.forEach{it.deleteRecursively()}
        connectivity.registerDefaultNetworkCallback(object:ConnectivityManager.NetworkCallback(){override fun onAvailable(network:Network){this@DownloadManager.network.value=true};override fun onLost(network:Network){this@DownloadManager.network.value=false};override fun onCapabilitiesChanged(network:Network,capabilities:NetworkCapabilities){this@DownloadManager.network.value=capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}})
        network.value=online(false);ready=true
        scope.launch {combine(dao.downloads(),preferences.flow){rows,prefs->rows to prefs}.collect{(rows,prefs)->schedule(rows,prefs)}}
        scope.launch {val meters=mutableMapOf<Long,TransferMeter>();var tick=0;while(isActive){delay(250);val now=System.currentTimeMillis();_progress.value=counters.mapValues{(id,c)->val done=c.first.get();val total=c.second.get();val(speed,eta)=meters.getOrPut(id){TransferMeter()}.sample(now,done,total);Progress(done,total,speed,eta)};if(++tick%8==0)counters.forEach{(id,c)->dao.progress(id,c.first.get(),c.second.get())};meters.keys.retainAll(counters.keys)}}
    }
    private suspend fun schedule(rows:List<DownloadEntity>,prefs:Settings):Unit=scheduler.withLock {
        rows.filter{it.state=="QUEUED" && !active.containsKey(it.id)}.take((prefs.maxConcurrentDownloads-active.size).coerceAtLeast(0)).forEach { row->
            val job=scope.launch(start=CoroutineStart.LAZY){try{pipeline(row,prefs)}finally{active.remove(row.id);counters.remove(row.id);calls.remove(row.id);schedule(dao.allDownloads(),preferences.flow.first())}}
            active[row.id]=job;job.start()
        }
    }
    suspend fun enqueue(details:VideoDetails,track:AudioTrackOption?,quality:QualityOption,audioOnly:Boolean):Long {
        initialize();val options=DownloadOptions(details,track?.id,quality.height,quality.container,audioOnly)
        val id=dao.enqueue(DownloadEntity(ytId=details.ytId,url=UrlParser.canonical(details.ytId),title=details.title,channelName=details.channelName,thumbnailPath=details.thumbnail,kind=if(audioOnly)"AUDIO"else"VIDEO",optionsJson=Json.encodeToString(options),bytesTotal=quality.estimatedBytes))
        startService();return id
    }
    fun startService(){ContextCompat.startForegroundService(context,Intent(context,DownloadService::class.java))}
    suspend fun pause(id:Long) {dao.state(id,"PAUSED");calls[id]?.toList()?.forEach{it.cancel()};active[id]?.cancelAndJoin()}
    suspend fun resume(id:Long){initialize();dao.state(id,"QUEUED");startService()}
    suspend fun cancel(id:Long){dao.state(id,"CANCELED");calls[id]?.toList()?.forEach{it.cancel()};active[id]?.cancelAndJoin();File(context.noBackupFilesDir,"dl/$id").deleteRecursively()}
    suspend fun pauseAll(){dao.allDownloads().filter{it.state in setOf("QUEUED","FETCHING","DOWNLOADING","WAITING_NETWORK","MUXING")}.forEach{pause(it.id)}}
    suspend fun resumeAll(){initialize();dao.allDownloads().filter{it.state in setOf("PAUSED","FAILED","WAITING_NETWORK")}.forEach{dao.state(it.id,"QUEUED")};startService()}
    suspend fun prioritize(id:Long)=dao.prioritize(id)
    suspend fun clearFinished()=dao.clearFinished()
    private suspend fun awaitNetwork(id:Long,prefs:Settings){while(!online(prefs.wifiOnly)){dao.state(id,"WAITING_NETWORK");withTimeoutOrNull(10000){network.first{online(prefs.wifiOnly)}};currentCoroutineContext().ensureActive()};dao.state(id,"DOWNLOADING")}
    private suspend fun pipeline(row:DownloadEntity,prefs:Settings) {
        val dir=File(context.noBackupFilesDir,"dl/${row.id}").apply{mkdirs()};val state=ChunkState(dir)
        var wake:PowerManager.WakeLock?=null
        try {
            startService();dao.state(row.id,"FETCHING")
            val options=Json.decodeFromString<DownloadOptions>(row.optionsJson)
            var details=if(System.currentTimeMillis()-options.details.fetchedAt>1200000)backend.refreshStreams(row.ytId)else options.details
            val refreshMutex=Mutex();var refreshed=false
            suspend fun refresh(){refreshMutex.withLock{if(!refreshed){details=backend.refreshStreams(row.ytId);refreshed=true}}}
            fun quality():QualityOption {val track=details.tracks.firstOrNull{it.id==options.trackId} ?: AudioTrackSelector.select(details.tracks,prefs.preferredAudioLanguage,batch=true,autoDub=prefs.autoPickDubbed).track;val all=StreamPlanner.options(details,track,options.audioOnly);return all.firstOrNull{it.height==options.height && it.container==options.container} ?: throw IOException("Requested stream no longer available")}
            var plan=quality();awaitNetwork(row.id,prefs)
            val lengths=mutableMapOf<String,Long>()
            suspend fun length(stream:MediaStream):Long {return try {contentLength(row.id,stream.url)}catch(e:StreamExpired){refresh();plan=quality();contentLength(row.id,(if(stream.videoOnly)plan.video else plan.audio ?: plan.video)?.url ?: throw e)}}
            plan.video?.let{lengths["video"]=length(it)};plan.audio?.let{lengths["audio"]=length(it)}
            val total=lengths.values.sum();if(dir.usableSpace<total*1.15)throw IOException("DISK_FULL")
            val done=AtomicLong();val totalCounter=AtomicLong(total);counters[row.id]=done to totalCounter
            wake=context.getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"AstraYT:transfer").also{it.acquire(10*60*1000L)}
            val io=Dispatchers.IO.limitedParallelism(12)
            coroutineScope {
                lengths.map { (key, length) ->
                    async(io) {
                        val file = File(dir, "$key.part")
                        val chunks = ChunkPlanner.plan(length)
                        val completed = state.prepare(key, length, file)
                        done.addAndGet(chunks.filter { completed[it.index] }.sumOf { it.length })
                        RandomAccessFile(file, "rw").use { random ->
                            random.setLength(length)
                            val work = Channel<Chunk>(Channel.UNLIMITED)
                            chunks.filterNot { state.has(key, it.index) }.forEach { work.trySend(it) }
                            work.close()
                            val lastSave = AtomicLong(0)
                            coroutineScope {
                                repeat(prefs.connectionsPerStream) {
                                    launch {
                                        val buffer = ByteArray(64 * 1024)
                                        for (chunk in work) {
                                            var attempt = 0
                                            while (true) {
                                                currentCoroutineContext().ensureActive()
                                                awaitNetwork(row.id, prefs)
                                                val wait = cooldown.get() - System.currentTimeMillis()
                                                if (wait > 0) delay(wait)
                                                var counted = 0L
                                                try {
                                                    val stream = if (key == "video") quality().video else quality().audio
                                                    val url = stream?.url ?: throw IOException("Missing stream")
                                                    val call = client.newCall(Request.Builder().url(url).header("Range", "bytes=${chunk.start}-${chunk.end}").header("Accept-Encoding", "identity").build())
                                                    register(row.id, call)
                                                    try {
                                                        call.execute().use { response ->
                                                            when (response.code) {
                                                                403, 410 -> throw StreamExpired()
                                                                429 -> { cooldown.set(System.currentTimeMillis() + 30000); throw IOException("429") }
                                                            }
                                                            if (response.code != 206 && !(response.code == 200 && chunk.start == 0L && chunk.length == length)) throw IOException("Range request refused: ${response.code}")
                                                            val range = response.header("Content-Range")
                                                            if (response.code == 206 && range?.startsWith("bytes ${chunk.start}-${chunk.end}/") != true) throw IOException("Invalid content range")
                                                            response.body.byteStream().use { input ->
                                                                var offset = chunk.start
                                                                var remain = chunk.length
                                                                while (remain > 0) {
                                                                    currentCoroutineContext().ensureActive()
                                                                    val n = input.read(buffer, 0, minOf(buffer.size.toLong(), remain).toInt())
                                                                    if (n < 0) throw EOFException("Truncated stream")
                                                                    val bytes = ByteBuffer.wrap(buffer, 0, n)
                                                                    while (bytes.hasRemaining()) offset += random.channel.write(bytes, offset)
                                                                    remain -= n
                                                                    counted += n
                                                                    done.addAndGet(n.toLong())
                                                                }
                                                            }
                                                        }
                                                    } finally { unregister(row.id, call) }
                                                    state.done(key, chunk.index)
                                                    val now = System.currentTimeMillis()
                                                    if (now - lastSave.get() > 2000) { state.save(); lastSave.set(now) }
                                                    break
                                                } catch (e: CancellationException) { throw e }
                                                catch (e: Exception) {
                                                    done.addAndGet(-counted)
                                                    if (e is StreamExpired) { if (refreshed) throw e; refresh() }
                                                    if (++attempt >= 6) throw e
                                                    delay((500L shl (attempt - 1)).coerceAtMost(20000) + Random.nextLong(250))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        file
                    }
                }.awaitAll()
            }
            state.save();wake?.let{if(it.isHeld)it.release()};wake=null;dao.state(row.id,"MUXING")
            storage.finish(row,options,details,plan,File(dir,"video.part").takeIf{it.exists()},File(dir,"audio.part").takeIf{it.exists()})
            dao.progress(row.id,total,total);dao.state(row.id,"DONE");dir.deleteRecursively()
        }catch(e:CancellationException){throw e}catch(e:Exception){if(dao.download(row.id)?.state !in setOf("CANCELED","PAUSED"))dao.state(row.id,"FAILED",Errors.code(e))}
        finally {if(dir.exists())runCatching{state.save()};wake?.let{if(it.isHeld)it.release()}}
    }
    private fun register(id:Long,call:Call){calls.computeIfAbsent(id){ConcurrentHashMap.newKeySet()}.add(call)}
    private fun unregister(id:Long,call:Call){calls[id]?.remove(call)}
    private suspend fun contentLength(id:Long,url:String):Long=withContext(Dispatchers.IO){val call=client.newCall(Request.Builder().url(url).header("Range","bytes=0-0").header("Accept-Encoding","identity").build());register(id,call);try{call.execute().use{r->if(r.code==403||r.code==410)throw StreamExpired();if(r.code==429){cooldown.set(System.currentTimeMillis()+30000);throw IOException("429")};if(!r.isSuccessful)throw IOException("HTTP ${r.code}");val length=r.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull() ?: r.body.contentLength();if(length<=0)throw IOException("Unknown stream size");length}}finally{unregister(id,call)}}
}
