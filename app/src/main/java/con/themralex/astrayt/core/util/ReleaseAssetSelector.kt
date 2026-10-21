package con.themralex.astrayt.core.util

data class ReleaseAsset(val name: String, val url: String)

object ReleaseAssetSelector {
    private val legacyUniversal = Regex("AstraYT-v[0-9]+\\.[0-9]+\\.[0-9]+\\.apk")

    fun select(assets: List<ReleaseAsset>, supportedAbis: List<String>): ReleaseAsset? {
        for (abi in supportedAbis) {
            assets.firstOrNull { it.name.endsWith("-$abi.apk") }?.let { return it }
        }
        return assets.firstOrNull {
            it.name.endsWith("-universal.apk") || legacyUniversal.matches(it.name)
        }
    }
}
