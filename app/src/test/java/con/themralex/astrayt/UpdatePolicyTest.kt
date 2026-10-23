package con.themralex.astrayt

import con.themralex.astrayt.update.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    private val repo="TheMrAleX/AstraYT"
    private val tag="v0.1.4"
    private val name="AstraYT-v0.1.4-arm64-v8a.apk"
    private fun url(file:String)="https://github.com/$repo/releases/download/$tag/$file"
    private fun metadata(checksum:Boolean=true,overrideUrl:String?=null)=buildJsonObject{
        put("tag_name",tag);put("draft",false);put("prerelease",false);put("body","## Novedades\n- Cambios de la app")
        put("assets",buildJsonArray{
            listOf("AstraYT-v0.1.4-armeabi-v7a.apk",name).forEach{file->add(buildJsonObject{put("name",file);put("browser_download_url",overrideUrl ?: url(file));put("size",5000000)})}
            if(checksum)add(buildJsonObject{put("name",name+".sha256");put("browser_download_url",url(name+".sha256"));put("size",95)})
        })
    }
    @Test fun versionComparisonHandlesNumericOrderingAndRejectsUnstableOrMalformedTags(){
        assertTrue(UpdatePolicy.newer("v0.1.10","0.1.9"));assertTrue(UpdatePolicy.newer("1.0.0","0.9.99"))
        assertFalse(UpdatePolicy.newer("v0.1.3","0.1.3"));assertFalse(UpdatePolicy.newer("v0.1.2","0.1.3"))
        listOf("v0.1.4-beta","bad","1.2","2147483648.1.0").forEach{assertFalse(UpdatePolicy.newer(it,"0.1.3"))}
    }
    @Test fun updateUsesTheMatchingApkAndItsOwnChecksum(){
        val release=UpdatePolicy.release(metadata(),"0.1.3",listOf("arm64-v8a","armeabi-v7a"),repo)!!
        assertEquals(name,release.assetName);assertEquals(url(name+".sha256"),release.checksumUrl);assertEquals(5000000L,release.size)
    }
    @Test(expected=UpdateFailure::class) fun missingChecksumCannotBecomeAnInstallableUpdate(){UpdatePolicy.release(metadata(false),"0.1.3",listOf("arm64-v8a"),repo)}
    @Test(expected=UpdateFailure::class) fun incompatibleArchitectureDoesNotDownloadAnotherArchitecture(){UpdatePolicy.release(metadata(),"0.1.3",listOf("x86"),repo)}
    @Test fun currentOrPrereleaseDoesNotOfferAnUpdate(){
        assertNull(UpdatePolicy.release(metadata(),"0.1.4",listOf("arm64-v8a"),repo))
        assertNull(UpdatePolicy.release(JsonObject(metadata()+mapOf("prerelease" to JsonPrimitive(true))),"0.1.3",listOf("arm64-v8a"),repo))
    }
    @Test(expected=UpdateFailure::class) fun anExternalApkUrlIsRejected(){UpdatePolicy.release(metadata(overrideUrl="https://example.test/$name"),"0.1.3",listOf("arm64-v8a"),repo)}
    @Test fun downloadUrlsMustMatchRepositoryTagAndFile(){
        assertTrue(UpdatePolicy.trustedUrl(url(name),repo,tag,name))
        listOf(url(name).replace("https:","http:"),url(name).replace("github.com","github.com.evil.test"),url(name).replace("github.com","github.com:8443"),url(name).replace(tag,"v9.0.0"),url(name)+"?redirect=1",url(name).replace("AstraYT/releases","Other/releases")).forEach{assertFalse(it,UpdatePolicy.trustedUrl(it,repo,tag,name))}
    }
    @Test fun checksumIsBoundToTheExpectedFilename(){
        val hash="a".repeat(64)
        assertEquals(hash,UpdatePolicy.checksum(hash.uppercase()+"  "+name+"\n",name))
        assertNull(UpdatePolicy.checksum(hash+"  other.apk",name));assertNull(UpdatePolicy.checksum("<html>no checksum</html>",name))
    }
    @Test fun resumingOnlyAcceptsTheExpectedByteRange(){
        assertTrue(UpdatePolicy.validRange("bytes 1024-4999/5000",1024,5000))
        assertFalse(UpdatePolicy.validRange("bytes 0-4999/5000",1024,5000));assertFalse(UpdatePolicy.validRange("bytes 1024-5999/6000",1024,5000))
    }
    @Test fun apkIdentityRequiresSameAppSameSignerAndANewerBuild(){
        fun valid(pkg:String="app.id",version:String="0.1.4",code:Long=14,signers:Set<String> = setOf("original"))=UpdatePolicy.validIdentity(pkg,"app.id",version,"0.1.4",code,13,signers,setOf("original"))
        assertTrue(valid());assertFalse(valid(pkg="other.app"));assertFalse(valid(version="9.0.0"));assertFalse(valid(code=13));assertFalse(valid(signers=setOf("other")));assertFalse(valid(signers=emptySet()))
    }
    @Test fun aDownloadKeepsItsPinnedReleaseWhenAnotherReleaseIsDetected(){
        val pinned=UpdatePolicy.release(metadata(),"0.1.3",listOf("arm64-v8a"),repo)!!
        val snapshot=AppUpdateState(available=pinned.copy(version="0.1.5",tag="v0.1.5"),target=pinned,stage="DOWNLOADING",done=1024,total=pinned.size,token="worker-id")
        val restored=Json.decodeFromString<AppUpdateState>(Json.encodeToString(snapshot))
        assertEquals(pinned,restored.release);assertEquals(1024L,restored.done);assertEquals("worker-id",restored.token)
    }
    @Test fun periodicDiscoveryShowsABannerAndDismissalDoesNotHideAnActiveDownload(){
        val release=UpdatePolicy.release(metadata(),"0.1.3",listOf("arm64-v8a"),repo)!!
        val detected=AppUpdateState(available=release)
        assertTrue(detected.showBanner);assertFalse(detected.copy(dismissedTag=tag).showBanner)
        assertTrue(detected.copy(dismissedTag=tag,stage="DOWNLOADING").showBanner)
        assertTrue(detected.copy(dismissedTag=tag,stage="READY").showBanner)
    }
    @Test fun releaseNotesDoNotExposeMarkdownFormatting(){assertEquals("Cambios\n• Reproductor mejorado\nAbrir detalles",UpdatePolicy.notes("## Cambios\n- **Reproductor** mejorado\n[Abrir detalles](https://example.test)"))}
}
