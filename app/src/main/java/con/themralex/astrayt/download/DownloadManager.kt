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

@kotlinx.serialization.Serializable data class DownloadOptions(val details:VideoDetails?=null,val trackId:String?=null,val height:Int=1080,val container:String="",val audioOnly:Boolean=false,val sponsorBlockCategories:Set<String> = emptySet(),val pinnedVideoId:String?=null,val pinnedAudioId:String?=null,val planPinned:Boolean=false,val requestedHeight:Int?=null,val audioLanguage:String?=null,val audioType:String?=null,val audioLabel:String?=null,val requestedAudioLanguage:String?=null,val subtitleId:String?=null,val subtitleLanguage:String?=null)
data class Progress(val done:Long,val total:Long,val speed:Long,val eta:Long) {val fraction get()=if(total>0)done.toFloat()/total else 0f}
private class StreamExpired:IOException()
@Singleton class DownloadManager @Inject constructor(@ApplicationContext private val context:Context,private val dao:AstraDao,private val preferences:Preferences,private val backend:YoutubeBackend,private val client:OkHttpClient,private val storage:MediaStorage,private val subtitles:SubtitleDownloads,private val timelines:con.themralex.astrayt.timeline.TimelineStore) {
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
    private val actions=Mutex()
    private var ready=false
    private val cooldown=AtomicLong(0)
    private val connectivity=context.getSystemService(ConnectivityManager::class.java)
    private fun online(wifiOnly:Boolean):Boolean {val caps=connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false;return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) && (!wifiOnly || !connectivity.isActiveNetworkMetered)}
    suspend fun initialize()=initialization.withLock {
        if(ready)return@withLock
        dao.pauseInterrupted()
        dao.allDownloads().forEach{row->
            val dir=File(context.noBackupFilesDir,"dl/${row.id}")
            if(storage.recover(row,dir)){dao.progress(row.id,row.bytesTotal,row.bytesTotal);dao.state(row.id,"DONE",recoveredSubtitleWarning(row));dir.deleteRecursively()}
            else if(row.state !in setOf("DONE","CANCELED") && File(dir,"state.bin").exists())dao.progress(row.id,ChunkState(dir).validatedBytes(),row.bytesTotal)
        }
        val stored=dao.allDownloads()
        val paused=stored.count{it.state=="PAUSED"}
        if(paused>0){
            val notices=context.getSystemService(android.app.NotificationManager::class.java)
            notices.createNotificationChannel(android.app.NotificationChannel(QUIET_ISSUES_CHANNEL,context.getString(con.themralex.astrayt.R.string.issues_channel),android.app.NotificationManager.IMPORTANCE_LOW).apply{setSound(null,null);enableVibration(false);setShowBadge(false)})
            val resume=android.app.PendingIntent.getForegroundService(context,101,Intent(context,DownloadService::class.java).setAction("resume_all"),android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
            notices.notify(101,androidx.core.app.NotificationCompat.Builder(context,QUIET_ISSUES_CHANNEL).setSmallIcon(con.themralex.astrayt.R.drawable.ic_notification).setContentTitle(context.resources.getQuantityString(con.themralex.astrayt.R.plurals.paused_count,paused,paused)).setAutoCancel(true).setSilent(true).setOnlyAlertOnce(true).addAction(0,context.getString(con.themralex.astrayt.R.string.resume),resume).build())
        }
        val known=stored.filter{it.state !in setOf("DONE","CANCELED")}.map{it.id.toString()}.toSet()
        File(context.noBackupFilesDir,"dl").listFiles()?.filter{it.name !in known}?.forEach{it.deleteRecursively()}
        connectivity.registerDefaultNetworkCallback(object:ConnectivityManager.NetworkCallback(){override fun onAvailable(network:Network){this@DownloadManager.network.value=true};override fun onLost(network:Network){this@DownloadManager.network.value=false};override fun onCapabilitiesChanged(network:Network,capabilities:NetworkCapabilities){this@DownloadManager.network.value=capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}})
        network.value=online(false);ready=true
        scope.launch {combine(dao.downloads(),preferences.flow){rows,prefs->rows to prefs}.collect{(rows,prefs)->schedule(rows,prefs)}}
        scope.launch {
            val meters=mutableMapOf<Long,TransferMeter>()
            while(isActive){
                delay(250)
                val now=android.os.SystemClock.elapsedRealtime()
                _progress.value=counters.mapValues{(id,c)->
                    val done=c.first.get();val total=c.second.get()
                    val(speed,eta)=meters.getOrPut(id){TransferMeter()}.sample(now,done,total)
                    Progress(done,total,speed,eta)
                }
                meters.keys.retainAll(counters.keys)
            }
        }
        // Database latency/failure must not stop the independent visual sampler.
        scope.launch {
            while(isActive){
                delay(2000)
                counters.forEach{(id,c)->
                    try{dao.progress(id,c.first.get(),c.second.get())}
                    catch(e:CancellationException){throw e}
                    catch(_:Exception){ /* Retry the next snapshot; chunk checkpoints remain durable. */ }
                }
            }
        }
    }
    private fun recoveredSubtitleWarning(row:DownloadEntity):String? {
        val options=runCatching{Json.decodeFromString<DownloadOptions>(row.optionsJson)}.getOrNull() ?: return null
        return "SUBTITLE_DOWNLOAD".takeIf{(options.subtitleId!=null || options.subtitleLanguage!=null) && !subtitles.hasDownload(row.ytId,options.subtitleLanguage)}
    }
    private suspend fun schedule(rows:List<DownloadEntity>,prefs:Settings):Unit=scheduler.withLock {
        rows.filter{it.state=="QUEUED" && !active.containsKey(it.id)}.take((prefs.maxConcurrentDownloads-active.size).coerceAtLeast(0)).forEach { row->
            if(dao.claimQueued(row.id)==0)return@forEach
            val job=scope.launch(start=CoroutineStart.LAZY){pipeline(row,prefs)}
            active[row.id]=job
            // Completion also runs when cancellation happens before the lazy body starts.
            job.invokeOnCompletion{scope.launch{
                scheduler.withLock{if(active.remove(row.id,job)){counters.remove(row.id);calls.remove(row.id)}}
                schedule(dao.allDownloads(),preferences.flow.first())
            }}
            job.start()
        }
    }
    suspend fun enqueue(videoId:String,height:Int,audioOnly:Boolean,details:VideoDetails?=null,trackId:String?=null,title:String?=null,channel:String="",thumbnail:String="",subtitle:SubtitleOption?=null,subtitleExplicit:Boolean=false):Long {
        initialize()
        val prefs=preferences.flow.first()
        val track=details?.tracks?.firstOrNull{it.id==trackId} ?: details?.let{AudioTrackSelector.select(it.tracks,prefs.preferredAudioLanguage,prefs.rememberedAudioLanguage ?: dao.channelLanguage(it.channelId),prefs.autoPickDubbed,batch=true).track}
        val chosen=details?.let{StreamPlanner.default(StreamPlanner.options(it,track,audioOnly),height) ?: throw NoStreams()}
        val subtitleChoice=if(subtitleExplicit)subtitle else subtitle ?: prefs.subtitleLanguage?.let{SubtitlePolicy.select(details?.subtitles.orEmpty(),it)}
        val options=DownloadOptions(details,track?.id ?: trackId,chosen?.height ?: height,container=chosen?.container.orEmpty(),audioOnly=audioOnly,sponsorBlockCategories=if(prefs.sponsorBlockEnabled)prefs.sponsorBlockCategories else emptySet(),pinnedVideoId=chosen?.video?.id,pinnedAudioId=chosen?.audio?.id,planPinned=chosen!=null,requestedHeight=height,audioLanguage=track?.locale,audioType=track?.type,audioLabel=track?.displayName,subtitleId=subtitleChoice?.id,subtitleLanguage=subtitleChoice?.language ?: prefs.subtitleLanguage.takeUnless{subtitleExplicit},requestedAudioLanguage=prefs.rememberedAudioLanguage ?: prefs.preferredAudioLanguage.takeIf{prefs.autoPickDubbed || !prefs.askWhenAlternatives})
        val id=dao.enqueue(DownloadEntity(ytId=videoId,url=UrlParser.canonical(videoId),title=details?.title ?: title ?: context.getString(con.themralex.astrayt.R.string.pending_video),channelName=details?.channelName ?: channel,thumbnailPath=details?.thumbnail ?: thumbnail,kind=if(audioOnly)"AUDIO"else"VIDEO",optionsJson=Json.encodeToString(options)))
        startService();return id
    }
    fun startService(){ContextCompat.startForegroundService(context,Intent(context,DownloadService::class.java))}
    private suspend fun stop(id:Long,state:String){
        val job=scheduler.withLock{
            dao.stop(id,state)
            calls[id]?.toList()?.forEach{it.cancel()}
            active[id]?.also{it.cancel()}
        }
        job?.join()
    }
    suspend fun pause(id:Long)=actions.withLock{stop(id,"PAUSED")}
    suspend fun resume(id:Long)=actions.withLock{initialize();dao.queueIfResumable(id);startService()}
    suspend fun cancel(id:Long)=actions.withLock{stop(id,"CANCELED");File(context.noBackupFilesDir,"dl/$id").deleteRecursively()}
    suspend fun pauseAll(){dao.allDownloads().filter{it.state in setOf("QUEUED","FETCHING","DOWNLOADING","WAITING_NETWORK","MUXING","SUBTITLES")}.forEach{pause(it.id)}}
    suspend fun resumeAll()=actions.withLock{initialize();dao.allDownloads().filter{it.state in setOf("PAUSED","FAILED","WAITING_NETWORK")}.forEach{dao.queueIfResumable(it.id)};startService()}
    suspend fun prioritize(id:Long)=dao.prioritize(id)
    suspend fun clearFinished()=dao.clearFinished()
    private suspend fun awaitNetwork(id:Long,prefs:Settings,nextState:String="DOWNLOADING"){while(!online(prefs.wifiOnly)){dao.runningState(id,"WAITING_NETWORK");withTimeoutOrNull(10000){network.first{online(prefs.wifiOnly)}};currentCoroutineContext().ensureActive()};currentCoroutineContext().ensureActive();dao.runningState(id,nextState)}
    private suspend fun pipeline(row:DownloadEntity,prefs:Settings) {
        val dir=File(context.noBackupFilesDir,"dl/${row.id}").apply{mkdirs()};val state=ChunkState(dir)
        var wake:PowerManager.WakeLock?=null
        try {
            if(storage.recover(row,dir)){
                dao.progress(row.id,row.bytesTotal,row.bytesTotal);dao.state(row.id,"DONE",recoveredSubtitleWarning(row));dir.deleteRecursively();return
            }
            startService();currentCoroutineContext().ensureActive();dao.runningState(row.id,"FETCHING")
            var options=Json.decodeFromString<DownloadOptions>(row.optionsJson)
            awaitNetwork(row.id,prefs,"FETCHING")
            val cuts=if(options.sponsorBlockCategories.isEmpty())emptyList()else SponsorBlock(client).segments(row.ytId,options.sponsorBlockCategories)
            var details=options.details?.takeIf{System.currentTimeMillis()-it.fetchedAt<1200000 && it.metadataLanguage==con.themralex.astrayt.timeline.TimelinePolicy.language(context.resources.configuration.locales[0].language) && (it.videos.isNotEmpty() || it.tracks.isNotEmpty())} ?: backend.details(row.ytId)
            val remembered=options.requestedAudioLanguage ?: prefs.rememberedAudioLanguage ?: dao.channelLanguage(details.channelId)
            fun selectedTrack():AudioTrackOption? {
                val id=options.trackId
                if(id!=null)return AudioTrackSelector.restore(details.tracks,id,options.audioLanguage,options.audioType) ?: throw IOException("AUDIO_UNAVAILABLE")
                return AudioTrackSelector.select(details.tracks,prefs.preferredAudioLanguage,remembered,prefs.autoPickDubbed,batch=true).track
            }
            var pinned:QualityOption?=null
            fun quality():QualityOption {
                val progressiveAudio=options.planPinned && details.videos.any{it.id==options.pinnedAudioId && !it.videoOnly}
                val track=if(progressiveAudio)null else selectedTrack()
                val pinnedProgressive=options.planPinned && !options.audioOnly && details.videos.any{it.id==options.pinnedVideoId && !it.videoOnly}
                // Preserve a previously downloaded original; repair old dubbed plans by adding their chosen audio.
                val embeddedOriginal=pinnedProgressive && options.pinnedAudioId==null && track?.type=="ORIGINAL"
                val repairDub=pinnedProgressive && options.pinnedAudioId==null && track!=null && track.type!="ORIGINAL"
                val all=StreamPlanner.options(details,if(embeddedOriginal)null else track,options.audioOnly,options.pinnedAudioId.takeIf{options.planPinned},options.pinnedVideoId.takeIf{options.planPinned})
                val previous=pinned
                return (if(previous==null && !options.planPinned)StreamPlanner.default(all,options.height)
                    else if(previous==null)all.firstOrNull{it.video?.id==options.pinnedVideoId && (it.audio?.id==options.pinnedAudioId || repairDub) && it.container==options.container && it.height==options.height}
                    else all.firstOrNull{it.video?.id==previous.video?.id && it.audio?.id==previous.audio?.id && it.container==previous.container && it.height==previous.height})
                    ?: throw NoStreams()
            }
            // The backend owns bounded extraction retries. Do not multiply them per queue item.
            dao.metadata(row.id,details.title,details.channelName,details.thumbnail)
            val refreshMutex=Mutex();var refreshed=false
            suspend fun refresh(){refreshMutex.withLock{if(!refreshed){details=backend.refreshStreams(row.ytId);refreshed=true}}}
            var plan=quality();pinned=plan
            val selected=if(plan.extractAudio || (options.planPinned && options.trackId==null))null else selectedTrack()
            options=options.copy(trackId=options.trackId ?: selected?.id,audioLanguage=options.audioLanguage ?: selected?.locale,audioType=options.audioType ?: selected?.type,audioLabel=options.audioLabel ?: selected?.displayName,pinnedVideoId=plan.video?.id,pinnedAudioId=plan.audio?.id,container=plan.container,height=plan.height,planPinned=true)
            dao.options(row.id,Json.encodeToString(options))
            dao.metadata(row.id,details.title,details.channelName,details.thumbnail)
            val annotationTask=CoroutineScope(currentCoroutineContext()).async{timelines.recognize(details)}
            CoroutineScope(currentCoroutineContext()).launch{storage.prefetchArt(details)}
            awaitNetwork(row.id,prefs)
            val lengths=mutableMapOf<String,Long>()
            suspend fun length(key:String,stream:MediaStream):Long {return try {contentLength(row.id,stream.url,stream.headers)}catch(e:StreamExpired){refresh();plan=quality();(if(key=="video")plan.video else plan.audio)?.let{contentLength(row.id,it.url,it.headers)} ?: throw e}}
            plan.video?.let{lengths["video"]=length("video",it)};plan.audio?.let{lengths["audio"]=length("audio",it)}
            val total=lengths.values.sum()
            // Existing preallocated files already occupy storage; require only new allocation plus headroom.
            val allocation=lengths.entries.sumOf{(key,length)->(length-File(dir,"$key.part").length()).coerceAtLeast(0)}
            if(dir.usableSpace<allocation+16L*1024*1024)throw IOException("DISK_FULL")
            lengths.forEach{(key,length)->
                val stream=if(key=="video")plan.video else plan.audio
                val identity=listOf(row.ytId,options.trackId,key,stream?.id,stream?.codec,plan.container).joinToString("|")
                state.prepare(key,length,File(dir,"$key.part"),identity)
            }
            state.save()
            val done=AtomicLong(lengths.entries.sumOf{(key,length)->ChunkPlanner.plan(length).sumOf{state.offset(key,it.index)}})
            val totalCounter=AtomicLong(total)
            dao.progress(row.id,done.get(),total)
            counters[row.id]=done to totalCounter

            val io=Dispatchers.IO.limitedParallelism(12)
            coroutineScope {
                lengths.map { (key, length) ->
                    async(io) {
                        val file = File(dir, "$key.part")
                        val chunks = ChunkPlanner.plan(length)
                        RandomAccessFile(file, "rw").use { random ->
                            random.setLength(length)
                            random.fd.sync()
                            val work = Channel<Chunk>(Channel.UNLIMITED)
                            chunks.filterNot { state.has(key, it.index) }.forEach { work.trySend(it) }
                            work.close()
                            state.save()
                            coroutineScope {
                                repeat(prefs.connectionsPerStream) {
                                    launch {
                                        val buffer = ByteArray(64 * 1024)
                                        val transferLock=context.getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"AstraYT:transfer")
                                        for (chunk in work) {
                                            var attempt = 0
                                            while (true) {
                                                currentCoroutineContext().ensureActive()
                                                awaitNetwork(row.id, prefs)
                                                val wait = cooldown.get() - System.currentTimeMillis()
                                                if (wait > 0) delay(wait)
                                                var offset = chunk.start + state.offset(key,chunk.index)
                                                if(offset>chunk.end)break
                                                var checkpoint = offset
                                                var savedAt = System.nanoTime()
                                                try {
                                                    val stream = if (key == "video") quality().video else quality().audio
                                                    val url = stream?.url ?: throw IOException("Missing stream")
                                                    val call = client.newCall(Request.Builder().url(url).apply{stream.headers.forEach{(name,value)->header(name,value)}}.header("Range", "bytes=$offset-${chunk.end}").header("Accept-Encoding", "identity").build())
                                                    register(row.id, call)
                                                    transferLock.acquire(5*60*1000L)
                                                    try {
                                                        call.execute().use { response ->
                                                            when (response.code) {
                                                                403, 410 -> throw StreamExpired()
                                                                429 -> { cooldown.set(System.currentTimeMillis() + 30000); throw IOException("429") }
                                                            }
                                                            if (response.code != 206 && !(response.code == 200 && offset == 0L && chunk.length == length)) throw IOException("Range request refused: ${response.code}")
                                                            val range = response.header("Content-Range")
                                                            if (response.code == 206 && range != "bytes $offset-${chunk.end}/$length") throw IOException("Invalid content range")
                                                            response.body.byteStream().use { input ->
                                                                var remain = chunk.end-offset+1
                                                                while (remain > 0) {
                                                                    currentCoroutineContext().ensureActive()
                                                                    val n = input.read(buffer, 0, minOf(buffer.size.toLong(), remain).toInt())
                                                                    if (n < 0) throw EOFException("Truncated stream")
                                                                    val bytes = ByteBuffer.wrap(buffer, 0, n)
                                                                    while (bytes.hasRemaining()) offset += random.channel.write(bytes, offset)
                                                                    remain -= n
                                                                    done.addAndGet(n.toLong())
                                                                    if(offset-checkpoint>=1024*1024 || System.nanoTime()-savedAt>=1_000_000_000L){
                                                                        random.channel.force(false)
                                                                        state.advance(key,chunk.index,offset-chunk.start);state.save()
                                                                        checkpoint=offset;savedAt=System.nanoTime()
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    } finally {
                                                        unregister(row.id, call);if(transferLock.isHeld)transferLock.release()
                                                        // Includes a short final read on pause, network failure or normal completion.
                                                        random.channel.force(false)
                                                        state.advance(key,chunk.index,offset-chunk.start);state.save()
                                                    }

                                                    break
                                                } catch (e: CancellationException) { throw e }
                                                catch (e: Exception) {

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
            state.save();wake?.let{if(it.isHeld)it.release()};wake=null;currentCoroutineContext().ensureActive()
            if(dir.usableSpace<total+16L*1024*1024)throw IOException("DISK_FULL")
            if(!annotationTask.isCompleted)dao.runningState(row.id,"FETCHING")
            val annotations=annotationTask.await()
            dao.runningState(row.id,"MUXING")
            val appliedCuts=storage.finish(row,options,details,plan,File(dir,"video.part").takeIf{it.exists()},File(dir,"audio.part").takeIf{it.exists()},cuts,annotations)
            var subtitleError:String?=if(annotations.sponsorFailed)"TIMELINE_DOWNLOAD"else null
            if(options.subtitleId!=null || options.subtitleLanguage!=null)try {
                dao.runningState(row.id,"SUBTITLES")
                val source=details.subtitles.firstOrNull{it.id==options.subtitleId} ?: options.subtitleLanguage?.let{SubtitlePolicy.select(details.subtitles,it)}
                if(source==null)throw IOException("SUBTITLE_DOWNLOAD")
                subtitles.download(row.ytId,source,appliedCuts)
            }catch(e:CancellationException){throw e}catch(_:Exception){subtitleError="SUBTITLE_DOWNLOAD"}
            dao.progress(row.id,total,total);dao.state(row.id,"DONE",subtitleError);dir.deleteRecursively()
        }catch(e:TimeoutCancellationException){dao.failUnlessStopped(row.id,"TIMEOUT")}catch(e:CancellationException){throw e}catch(e:Exception){dao.failUnlessStopped(row.id,Errors.code(e))}
        finally {withContext(NonCancellable){
            if(dir.exists()){
                runCatching{state.save()}
                val total=counters[row.id]?.second?.get() ?: row.bytesTotal
                dao.progress(row.id,state.bytes(),total)
            }
            wake?.let{if(it.isHeld)it.release()}
        }}
    }
    private fun register(id:Long,call:Call){calls.computeIfAbsent(id){ConcurrentHashMap.newKeySet()}.add(call)}
    private fun unregister(id:Long,call:Call){calls[id]?.remove(call)}
    private suspend fun contentLength(id:Long,url:String,headers:Map<String,String>):Long=withContext(Dispatchers.IO){val call=client.newCall(Request.Builder().url(url).apply{headers.forEach{(name,value)->header(name,value)}}.header("Range","bytes=0-0").header("Accept-Encoding","identity").build());register(id,call);try{call.execute().use{r->if(r.code==403||r.code==410)throw StreamExpired();if(r.code==429){cooldown.set(System.currentTimeMillis()+30000);throw IOException("429")};if(!r.isSuccessful)throw IOException("HTTP ${r.code}");val length=r.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull() ?: r.body.contentLength();if(length<=0)throw IOException("Unknown stream size");length}}finally{unregister(id,call)}}
}
