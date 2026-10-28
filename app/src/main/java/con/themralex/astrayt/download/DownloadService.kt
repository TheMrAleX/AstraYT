package con.themralex.astrayt.download
import android.app.*
import android.content.*
import android.os.*
import androidx.core.app.NotificationCompat
import con.themralex.astrayt.MainActivity
import con.themralex.astrayt.R
import con.themralex.astrayt.data.db.*
import con.themralex.astrayt.core.util.*
import con.themralex.astrayt.feature.CollectionPolicy
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
@AndroidEntryPoint class DownloadService:Service() {
    @Inject lateinit var manager:DownloadManager
    @Inject lateinit var dao:AstraDao
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val notices by lazy{getSystemService(NotificationManager::class.java)}
    private val startedAt=System.currentTimeMillis()
    private val seen=mutableMapOf<Long,String>()
    private val seenGroups=mutableMapOf<Long,String>()
    override fun onCreate(){super.onCreate();channels();startForeground(100,summary(0));scope.launch {manager.initialize();combine(dao.downloads(),manager.progress){rows,p->rows to p}.sample(1000).collect{(rows,progress)->
        val running=rows.filter{it.state in setOf("FETCHING","DOWNLOADING","MUXING","SUBTITLES","WAITING_NETWORK","QUEUED")}
        notices.notify(100,summary(CollectionPolicy.taskCount(running)))
        rows.filter{it.playlistId==null}.forEach { row->
            val previous=seen.put(row.id,row.state)
            when(DownloadNoticePolicy.action(row.state,previous,row.updatedAtMs,startedAt)) {
                DownloadNoticePolicy.Action.UPDATE,DownloadNoticePolicy.Action.TERMINAL->notices.notify(row.id.toInt()+1000,notification(row,progress[row.id]))
                DownloadNoticePolicy.Action.REMOVE->notices.cancel(row.id.toInt()+1000)
                DownloadNoticePolicy.Action.NONE->Unit
            }
        }
        val playlists=dao.playlists().first().associateBy{it.id}
        rows.filter{it.playlistId!=null}.groupBy{it.playlistId!!}.forEach{(id,children)->
            val state=CollectionPolicy.state(children)
            val previous=seenGroups.put(id,state)
            val row=children.first().copy(title=playlists[id]?.title ?: children.first().title,state=state,updatedAtMs=children.maxOf{it.updatedAtMs},errorCode=children.firstOrNull{it.state=="FAILED"}?.errorCode)
            val p=Progress((CollectionPolicy.fraction(children,progress)*10000).toLong(),10000,children.sumOf{progress[it.id]?.speed ?: 0L},0)
            when(DownloadNoticePolicy.action(state,previous,row.updatedAtMs,startedAt)) {
                DownloadNoticePolicy.Action.UPDATE,DownloadNoticePolicy.Action.TERMINAL->notices.notify(1000000+id.toInt(),notification(row,p,id,getString(R.string.collection_completed,children.count{it.state=="DONE"},children.size)))
                DownloadNoticePolicy.Action.REMOVE->notices.cancel(1000000+id.toInt())
                DownloadNoticePolicy.Action.NONE->Unit
            }
        }
        val presentGroups=rows.mapNotNull{it.playlistId}.toSet()
        seenGroups.keys.filter{it !in presentGroups}.toList().forEach{notices.cancel(1000000+it.toInt());seenGroups.remove(it)}
        if(running.isEmpty()){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
    }}}
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        val id=intent?.getLongExtra("id",-1) ?: -1
        val group=intent?.getLongExtra("playlistId",-1) ?: -1
        scope.launch {try{when(intent?.action){"pause"->if(group>=0)manager.pauseCollection(group)else manager.pause(id);"resume"->if(group>=0)manager.resumeCollection(group)else manager.resume(id);"cancel"->if(group>=0)manager.cancelCollection(group)else manager.cancel(id);"pause_all"->manager.pauseAll();"resume_all"->manager.resumeAll()}}catch(_:Exception){stopSelf()}}
        return START_NOT_STICKY
    }
    override fun onTimeout(startId:Int,fgsType:Int){scope.launch{manager.pauseAll();notices.notify(101,NotificationCompat.Builder(this@DownloadService,QUIET_ISSUES_CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(getString(R.string.system_paused)).setSilent(true).setOnlyAlertOnce(true).addAction(0,getString(R.string.resume),action("resume_all",-1)).build());stopForeground(STOP_FOREGROUND_REMOVE);stopSelf(startId)}}
    override fun onDestroy(){scope.cancel();super.onDestroy()}
    override fun onBind(intent:Intent?):IBinder?=null
    private fun channels(){notices.createNotificationChannels(listOf(NotificationChannel("downloads_active",getString(R.string.active_channel),NotificationManager.IMPORTANCE_LOW).apply{setSound(null,null)},NotificationChannel("downloads_done",getString(R.string.done_channel),NotificationManager.IMPORTANCE_DEFAULT),NotificationChannel(QUIET_ISSUES_CHANNEL,getString(R.string.issues_channel),NotificationManager.IMPORTANCE_LOW).apply{setSound(null,null);enableVibration(false);setShowBadge(false)}))}
    private fun open(id:Long?=null,group:Long?=null):PendingIntent=PendingIntent.getActivity(this,group?.toInt()?.let{1000000+it} ?: id?.toInt() ?: 0,Intent(this,MainActivity::class.java).putExtra("screen",if(id==null)"queue"else"player").putExtra("downloadId",id ?: -1).putExtra("playlistId",group ?: -1L).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun action(action:String,id:Long,group:Long?=null)=PendingIntent.getForegroundService(this,(id.toString()+action+group).hashCode(),Intent(this,DownloadService::class.java).setAction(action).putExtra("id",id).putExtra("playlistId",group ?: -1L),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun summary(count:Int)=NotificationCompat.Builder(this,"downloads_active").setSmallIcon(R.drawable.ic_notification).setContentTitle(resources.getQuantityString(R.plurals.download_summary,count,count)).setContentIntent(open()).setGroup("downloads").setGroupSummary(true).setOngoing(true).setOnlyAlertOnce(true).setSilent(true).addAction(0,getString(R.string.pause_all),action("pause_all",-1)).build()
    private fun notification(row:DownloadEntity,p:Progress?,group:Long?=null,groupText:String?=null):Notification {
        val done=row.state=="DONE";val failed=row.state=="FAILED";val paused=row.state=="PAUSED"
        val status=when(row.state){"QUEUED"->R.string.queued;"FETCHING"->R.string.fetching;"MUXING"->R.string.muxing;"SUBTITLES"->R.string.subtitle_downloading;"WAITING_NETWORK"->R.string.waiting_network;"PAUSED"->R.string.paused;"FAILED"->Errors.string(row.errorCode);"DONE"->R.string.download_complete;else->R.string.downloading}
        val text=if(p!=null && row.state=="DOWNLOADING")"${getString(status)} · ${(p.fraction*100).toInt()} % · ${Formatters.bytes(p.speed)}/s"+(if(group==null)" · ${Formatters.duration(p.eta.coerceAtLeast(0)*1000)}"else "")else getString(status)
        val b=NotificationCompat.Builder(this,if(done)"downloads_done"else if(failed)QUIET_ISSUES_CHANNEL else "downloads_active").setSmallIcon(R.drawable.ic_notification).setContentTitle(row.title).setContentText(groupText?.let{"$it · $text"} ?: text).setContentIntent(open(if(done && group==null)row.id else null,group)).setGroup("downloads").setOnlyAlertOnce(true).setSilent(true).setAutoCancel(done || failed || paused).setOngoing(!done && !failed && !paused)
        if(done && group==null){
            val artwork=java.io.File(filesDir,"art/${row.ytId}.webp")
            if(artwork.isFile){val bitmap=android.graphics.BitmapFactory.decodeFile(artwork.absolutePath);if(bitmap!=null)b.setLargeIcon(bitmap).setStyle(NotificationCompat.BigPictureStyle().bigPicture(bitmap))}
        }
        if(!done && !failed)b.setCategory(NotificationCompat.CATEGORY_PROGRESS).setProgress(100,((p?.fraction ?: 0f)*100).toInt(),row.state in setOf("QUEUED","FETCHING","MUXING","SUBTITLES","WAITING_NETWORK"))
        if(!done){b.addAction(0,getString(if(paused || failed)R.string.resume else R.string.pause),action(if(paused || failed)"resume"else"pause",row.id,group));b.addAction(0,getString(R.string.cancel),action("cancel",row.id,group))}
        else if(group==null)b.addAction(0,getString(R.string.play),open(row.id))
        return b.build()
    }
}
