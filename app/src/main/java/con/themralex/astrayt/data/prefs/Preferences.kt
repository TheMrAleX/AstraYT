package con.themralex.astrayt.data.prefs
import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import java.util.Locale
import javax.inject.*
private val Context.store by preferencesDataStore("astrayt")
@Serializable data class Settings(val onboardingDone: Boolean=false,val preferredAudioLanguage: String=Locale.getDefault().language,val askWhenAlternatives: Boolean=true,val autoPickDubbed: Boolean=false,val defaultMaxQuality: Int=1080,val maxConcurrentDownloads: Int=2,val connectionsPerStream: Int=3,val wifiOnly: Boolean=false,val animationsEnabled: Boolean=true,val amoledPureBlack: Boolean=false,val accentPreset: String="EMBER",val autoplayNext: Boolean=true,val recentSearches: List<String> = emptyList(),val lastUpdateCheck: Long=0,val sponsorBlockEnabled:Boolean=false,val sponsorBlockCategories:Set<String> = setOf("sponsor"),val rememberedAudioLanguage:String?=null,val subtitleLanguage:String?=null)
@Singleton class Preferences @Inject constructor(@ApplicationContext private val context: Context) {
    private val key=stringPreferencesKey("settings")
    private val json=Json{ignoreUnknownKeys=true;encodeDefaults=true}
    val flow:Flow<Settings> = context.store.data.catch{if(it is java.io.IOException)emit(emptyPreferences())else throw it}.map{runCatching{json.decodeFromString<Settings>(it[key] ?: "{}")}.getOrDefault(Settings())}
    suspend fun update(transform:(Settings)->Settings) { context.store.edit { prefs->val old=runCatching{json.decodeFromString<Settings>(prefs[key] ?: "{}")}.getOrDefault(Settings());prefs[key]=json.encodeToString(transform(old)) } }
}
