package con.themralex.astrayt.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.*
import con.themralex.astrayt.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.*

private val Context.updateStore by preferencesDataStore("app_updates")
data class UpdateCheck(val checking:Boolean=false,val error:String?=null)
@Singleton class AppUpdates @Inject constructor(@ApplicationContext private val context:Context,private val client:OkHttpClient) {
    private val key=stringPreferencesKey("snapshot")
    private val json=Json{ignoreUnknownKeys=true;encodeDefaults=true}
    private val mutex=Mutex()
    private val actions=Mutex()
    private val transfers=Mutex()
    private val calls=ConcurrentHashMap<String,Call>()
    private val _check=MutableStateFlow(UpdateCheck())
    val check=_check.asStateFlow()
    val state=context.updateStore.data.catch{if(it is IOException)emit(emptyPreferences())else throw it}.map{decode(it[key])}
    private fun decode(value:String?)=runCatching{json.decodeFromString<AppUpdateState>(value ?: "{}")}.getOrDefault(AppUpdateState())
    private suspend fun edit(change:(AppUpdateState)->AppUpdateState){context.updateStore.edit{it[key]=json.encodeToString(change(decode(it[key])))}}
    private val folder get()=File(context.filesDir,"updates").apply{mkdirs()}
    private val work get()=WorkManager.getInstance(context)
    suspend fun initialize()=withContext(Dispatchers.IO) {
        work.enqueueUniquePeriodicWork(CHECK_WORK,ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<UpdateCheckWorker>(12,TimeUnit.HOURS).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        val current=state.first()
        if(current.release?.let{!UpdatePolicy.newer(it.version,BuildConfig.VERSION_NAME)}==true){
            work.cancelUniqueWork(DOWNLOAD_WORK);calls.values.forEach{it.cancel()};edit{AppUpdateState(available=it.available?.takeIf{release->UpdatePolicy.newer(release.version,BuildConfig.VERSION_NAME)},checkedAt=it.checkedAt,attemptedAt=it.attemptedAt)};folder.listFiles()?.forEach{it.delete()}
        }else if(current.stage=="READY" && readyFile(current)==null)edit{it.copy(stage="FAILED",error="DOWNLOAD",fileName=null)}
        else if(current.busy && work.getWorkInfosForUniqueWork(DOWNLOAD_WORK).get().none{!it.state.isFinished})edit{it.copy(stage="FAILED",error="DOWNLOAD")}
    }
    suspend fun check(manual:Boolean=false):Unit=withContext(Dispatchers.IO) {
        mutex.withLock {
            val now=System.currentTimeMillis();val old=state.first()
            if(!manual && now-old.attemptedAt<6*60*60*1000L)return@withLock
            _check.value=UpdateCheck(checking=true)
            edit{it.copy(attemptedAt=now)}
            try {
                val request=Request.Builder().url("https://api.github.com/repos/${BuildConfig.GITHUB_REPOSITORY}/releases/latest").header("Accept","application/vnd.github+json").header("User-Agent","AstraYT/${BuildConfig.VERSION_NAME}").build()
                val release=client.newCall(request).apply{timeout().timeout(15,TimeUnit.SECONDS)}.execute().use { response->
                    if(response.code==404)null else {
                        if(!response.isSuccessful)throw IOException("Update check HTTP ${response.code}")
                        UpdatePolicy.release(Json.parseToJsonElement(response.body.string()).jsonObject,BuildConfig.VERSION_NAME,Build.SUPPORTED_ABIS.toList(),BuildConfig.GITHUB_REPOSITORY)
                    }
                }
                edit{it.copy(available=release,checkedAt=now)}
                _check.value=UpdateCheck()
            }catch(e:CancellationException){_check.value=UpdateCheck();throw e}
            catch(e:Exception){_check.value=UpdateCheck(error=if(manual)(e as? UpdateFailure)?.code ?: "CHECK"else null)}
        }
    }
    suspend fun dismiss(){edit{it.copy(dismissedTag=it.release?.tag)}}
    suspend fun startDownload()=withContext(Dispatchers.IO) {actions.withLock {
        val current=state.first();if(current.busy || current.stage=="READY")return@withLock
        val release=current.release ?: return@withLock
        if(!UpdatePolicy.newer(release.version,BuildConfig.VERSION_NAME))return@withLock
        val request=OneTimeWorkRequestBuilder<UpdateDownloadWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        edit{it.copy(target=release,stage="QUEUED",done=0,total=release.size,fileName=null,token=request.id.toString(),error=null,dismissedTag=null)}
        try{work.enqueueUniqueWork(DOWNLOAD_WORK,ExistingWorkPolicy.REPLACE,request).result.get()}
        catch(e:Exception){failed(request.id.toString(),"DOWNLOAD")}
    }}
    suspend fun cancelDownload()=withContext(Dispatchers.IO) {actions.withLock {
        val token=state.first().token
        edit{it.copy(target=null,stage="IDLE",done=0,total=0,fileName=null,token=null,error=null)}
        token?.let{calls.remove(it)?.cancel()};work.cancelUniqueWork(DOWNLOAD_WORK).result.get()
    }}
    fun readyFile(snapshot:AppUpdateState):File? {
        val name=snapshot.fileName ?: return null
        if(snapshot.stage!="READY" || !name.matches(Regex("[A-Za-z0-9._-]+\\.apk")))return null
        return File(folder,name).takeIf{it.isFile && it.length()==snapshot.release?.size}
    }
    private suspend fun changeDownload(token:String,change:(AppUpdateState)->AppUpdateState){edit{if(it.token==token)change(it)else it}}
    private suspend fun failed(token:String,error:String){changeDownload(token){it.copy(stage="FAILED",error=error,fileName=null)}}
    private fun request(url:String)=Request.Builder().url(url).header("User-Agent","AstraYT/${BuildConfig.VERSION_NAME}").build()
    private fun execute(token:String,request:Request):Response {
        val call=client.newCall(request).apply{timeout().timeout(120,TimeUnit.SECONDS)};calls[token]=call
        return call.execute()
    }
    suspend fun transfer(token:String)=withContext(Dispatchers.IO) {transfers.withLock {
        val snapshot=state.first();val release=snapshot.target ?: return@withLock
        if(snapshot.token!=token)return@withLock
        val partial=File(folder,release.assetName+".part")
        try {
            if(!UpdatePolicy.trustedUrl(release.url,BuildConfig.GITHUB_REPOSITORY,release.tag,release.assetName) || !UpdatePolicy.trustedUrl(release.checksumUrl,BuildConfig.GITHUB_REPOSITORY,release.tag,release.assetName+".sha256"))throw UpdateFailure("VERIFY")
            changeDownload(token){it.copy(stage="DOWNLOADING",error=null)}
            val digest=execute(token,request(release.checksumUrl)).use{response->
                if(!response.isSuccessful)throw IOException("Checksum HTTP ${response.code}")
                if(response.body.contentLength()>4096)throw UpdateFailure("VERIFY")
                val buffer=ByteArray(4097);var count=0
                response.body.byteStream().use{input->while(count<buffer.size){val n=input.read(buffer,count,buffer.size-count);if(n<0)break;count+=n}}
                if(count>4096)throw UpdateFailure("VERIFY")
                UpdatePolicy.checksum(String(buffer,0,count,Charsets.UTF_8),release.assetName) ?: throw UpdateFailure("VERIFY")
            }
            var offset=partial.length().takeIf{it in 0..release.size} ?: 0L
            if(folder.usableSpace<release.size-offset+8*1024*1024)throw UpdateFailure("DISK")
            if(offset<release.size) {
                val req=request(release.url).newBuilder().header("Accept-Encoding","identity").apply{if(offset>0)header("Range","bytes=$offset-")}.build()
                execute(token,req).use { response->
                    if(response.code==206){if(!UpdatePolicy.validRange(response.header("Content-Range"),offset,release.size))throw UpdateFailure("VERIFY")}
                    else if(response.code==200)offset=0 else throw IOException("APK HTTP ${response.code}")
                    RandomAccessFile(partial,"rw").use { file->
                        file.setLength(offset);file.seek(offset)
                        response.body.byteStream().use { input->
                            val buffer=ByteArray(64*1024);var last=0L
                            while(true){currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0)break
                                if(offset+n>release.size || offset+n>UpdatePolicy.MAX_APK_SIZE)throw UpdateFailure("VERIFY")
                                file.write(buffer,0,n);offset+=n
                                val now=System.nanoTime();if(now-last>250_000_000){changeDownload(token){it.copy(done=offset,total=release.size)};last=now}
                            }
                        };file.fd.sync()
                    }
                }
            }
            if(partial.length()!=release.size)throw IOException("Incomplete APK")
            changeDownload(token){it.copy(stage="VERIFYING",done=release.size,total=release.size)}
            val actual=partial.inputStream().use{input->val md=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(64*1024);while(true){currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0)break;md.update(buffer,0,n)};md.digest().joinToString(""){"%02x".format(it)}}
            if(actual!=digest)throw UpdateFailure("VERIFY")
            validateApk(partial,release)
            currentCoroutineContext().ensureActive()
            if(state.first().token!=token)return@withLock
            val output=File(folder,release.assetName);if(output.exists() && !output.delete())throw IOException("Cannot replace APK")
            if(!partial.renameTo(output))throw IOException("Cannot finish APK")
            changeDownload(token){it.copy(stage="READY",fileName=output.name,error=null)}
            folder.listFiles()?.filter{it.name!=output.name}?.forEach{it.delete()}
        }catch(e:CancellationException){throw e}
        catch(e:Exception){val code=(e as? UpdateFailure)?.code ?: "DOWNLOAD";if(code=="VERIFY")partial.delete();failed(token,code)}
        finally{calls.remove(token)?.cancel()}
    }}
    @Suppress("DEPRECATION") private fun validateApk(file:File,release:AppRelease) {
        val flags=if(Build.VERSION.SDK_INT>=28)PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val pm=context.packageManager
        val archive=pm.getPackageArchiveInfo(file.absolutePath,flags) ?: throw UpdateFailure("VERIFY")
        val installed=pm.getPackageInfo(context.packageName,flags)
        fun signatures(info:PackageInfo):Set<String> {
            val signers=if(Build.VERSION.SDK_INT>=28)info.signingInfo?.apkContentsSigners else info.signatures
            return signers.orEmpty().map{signature->MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString(""){"%02x".format(it)}}.toSet()
        }
        val code=if(Build.VERSION.SDK_INT>=28)archive.longVersionCode else archive.versionCode.toLong()
        if(!UpdatePolicy.validIdentity(archive.packageName,context.packageName,archive.versionName,release.version,code,BuildConfig.VERSION_CODE.toLong(),signatures(archive),signatures(installed)) || (archive.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE)>Build.VERSION.SDK_INT)throw UpdateFailure("VERIFY")
    }
    companion object {const val CHECK_WORK="app-update-check";const val DOWNLOAD_WORK="app-update-download"}
}
