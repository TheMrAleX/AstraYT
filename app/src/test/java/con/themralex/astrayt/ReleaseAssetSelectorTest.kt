package con.themralex.astrayt

import con.themralex.astrayt.core.util.ReleaseAsset
import con.themralex.astrayt.core.util.ReleaseAssetSelector
import org.junit.Assert.*
import org.junit.Test

class ReleaseAssetSelectorTest {
    private fun asset(abi: String): ReleaseAsset {
        val name = "AstraYT-v0.0.1-$abi.apk"
        return ReleaseAsset(name, "https://github.com/TheMrAleX/AstraYT/releases/download/v0.0.1/$name")
    }

    @Test fun prefers64BitEvenWhen32BitAssetAppearsFirst() {
        val arm64 = asset("arm64-v8a")
        assertEquals(arm64, ReleaseAssetSelector.select(listOf(asset("armeabi-v7a"), arm64), listOf("arm64-v8a", "armeabi-v7a")))
    }

    @Test fun selects32BitFor32BitAndroid() {
        val arm32 = asset("armeabi-v7a")
        assertEquals(arm32, ReleaseAssetSelector.select(listOf(asset("arm64-v8a"), arm32), listOf("armeabi-v7a", "armeabi")))
    }

    @Test fun neverOffersArmBinaryToX86Device() {
        assertNull(ReleaseAssetSelector.select(listOf(asset("arm64-v8a"), asset("armeabi-v7a")), listOf("x86_64", "x86")))
    }

    @Test fun supportsLegacyUniversalRelease() {
        val universal = ReleaseAsset("AstraYT-v1.0.0.apk", "https://github.com/TheMrAleX/AstraYT/releases/download/v1.0.0/AstraYT-v1.0.0.apk")
        assertEquals(universal, ReleaseAssetSelector.select(listOf(asset("arm64-v8a"), universal), listOf("x86_64")))
    }

    @Test fun ignoresChecksumsAndUnknownFileNames() {
        val arm64 = asset("arm64-v8a")
        assertNull(ReleaseAssetSelector.select(listOf(arm64.copy(name=arm64.name+".sha256"), arm64.copy(name="unrelated.apk")), listOf("arm64-v8a")))
    }
}
