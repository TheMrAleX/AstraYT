package con.themralex.astrayt.update

import con.themralex.astrayt.core.util.ReleaseAsset
import con.themralex.astrayt.core.util.ReleaseAssetSelector
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.net.URI

@Serializable data class AppRelease(val tag:String,val version:String,val notes:String,val assetName:String,val url:String,val checksumUrl:String,val size:Long)
@Serializable data class AppUpdateState(val available:AppRelease?=null,val target:AppRelease?=null,val checkedAt:Long=0,val attemptedAt:Long=0,val stage:String="IDLE",val done:Long=0,val total:Long=0,val fileName:String?=null,val token:String?=null,val error:String?=null,val dismissedTag:String?=null) {
    val release get()=target ?: available
    val busy get()=stage in setOf("QUEUED","DOWNLOADING","VERIFYING")
    val showBanner get()=release!=null && (busy || stage=="READY" || dismissedTag!=release?.tag)
}
internal class UpdateFailure(val code:String):Exception(code)
internal object UpdatePolicy {
    const val MAX_APK_SIZE=256L*1024*1024
    fun version(value:String):List<Int>? {
        val clean=value.removePrefix("v").takeIf{it.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))} ?: return null
        return clean.split('.').map{it.toIntOrNull() ?: return null}
    }
    fun newer(remote:String,local:String):Boolean {val r=version(remote) ?: return false;val l=version(local) ?: return false;return r.zip(l).firstOrNull{it.first!=it.second}?.let{it.first>it.second} ?: false}
    fun trustedUrl(url:String,repo:String,tag:String,name:String):Boolean=runCatching {
        val uri=URI(url)
        uri.scheme=="https" && uri.host=="github.com" && uri.port in setOf(-1,443) && uri.userInfo==null && uri.query==null && uri.fragment==null && uri.path=="/$repo/releases/download/$tag/$name" && !name.contains('/') && name.matches(Regex("[A-Za-z0-9._-]+"))
    }.getOrDefault(false)
    fun release(root:JsonObject,local:String,abis:List<String>,repo:String):AppRelease? {
        if(root["draft"]?.jsonPrimitive?.booleanOrNull==true || root["prerelease"]?.jsonPrimitive?.booleanOrNull==true)return null
        val tag=root["tag_name"]?.jsonPrimitive?.contentOrNull ?: throw UpdateFailure("CHECK")
        if(version(tag)==null)throw UpdateFailure("CHECK")
        if(!newer(tag,local))return null
        val assets=root["assets"]?.jsonArray.orEmpty().mapNotNull{it as? JsonObject}
        val all=assets.mapNotNull{a->val n=a["name"]?.jsonPrimitive?.contentOrNull;val u=a["browser_download_url"]?.jsonPrimitive?.contentOrNull;if(n!=null && u!=null)ReleaseAsset(n,u)else null}
        val apk=ReleaseAssetSelector.select(all,abis) ?: throw UpdateFailure("COMPATIBLE")
        val checksum=all.firstOrNull{it.name==apk.name+".sha256"} ?: throw UpdateFailure("VERIFY")
        if(!trustedUrl(apk.url,repo,tag,apk.name) || !trustedUrl(checksum.url,repo,tag,checksum.name))throw UpdateFailure("VERIFY")
        val size=assets.first{it["name"]?.jsonPrimitive?.content==apk.name}["size"]?.jsonPrimitive?.longOrNull ?: 0
        if(size !in 1..MAX_APK_SIZE)throw UpdateFailure("VERIFY")
        return AppRelease(tag,tag.removePrefix("v"),root["body"]?.jsonPrimitive?.contentOrNull.orEmpty().take(24000),apk.name,apk.url,checksum.url,size)
    }
    fun checksum(text:String,name:String):String?=text.lineSequence().mapNotNull{Regex("^([a-fA-F0-9]{64})\\s+\\*?(.+)$").matchEntire(it.trim())}.firstOrNull{it.groupValues[2]==name}?.groupValues?.get(1)?.lowercase()
    fun validRange(header:String?,offset:Long,total:Long)=header=="bytes $offset-${total-1}/$total"
    fun validIdentity(packageName:String,expectedPackage:String,versionName:String?,expectedVersion:String,code:Long,installedCode:Long,signers:Set<String>,installedSigners:Set<String>)=
        packageName==expectedPackage && versionName==expectedVersion && code>installedCode && signers.isNotEmpty() && signers==installedSigners
    fun notes(markdown:String)=markdown.replace(Regex("(?m)^#{1,6}\\s+"),"").replace(Regex("(?m)^\\s*[-*]\\s+"),"• ").replace(Regex("\\[([^]]+)]\\([^)]+\\)"),"$1").replace("**","").replace("__","").replace("`","").trim()
}
