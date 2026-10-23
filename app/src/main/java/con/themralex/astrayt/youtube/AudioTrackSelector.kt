package con.themralex.astrayt.youtube
import java.util.Locale
object AudioTrackSelector {
    data class Selection(val track: AudioTrackOption?,val prompt: Boolean)
    private fun language(tag: String)=Locale.forLanguageTag(tag).language
    fun select(tracks: List<AudioTrackOption>,preferred: String,remembered: String?=null,autoDub: Boolean=false,ask: Boolean=true,batch: Boolean=false,deviceLocale: String=Locale.getDefault().toLanguageTag()):Selection {
        val safe=tracks.filter{it.type!="DESCRIPTIVE"}
        if(safe.isEmpty())return Selection(null,false)
        if(safe.size==1)return Selection(safe.first(),false)
        val original=safe.firstOrNull{it.type=="ORIGINAL"} ?: safe.firstOrNull{it.default} ?: safe.first()
        fun match(tag: String?)=tag?.let { t->val same=safe.filter{language(it.locale)==language(t)};same.firstOrNull{it.locale.equals(t,true)} ?: same.firstOrNull{Locale.forLanguageTag(it.locale).country==Locale.forLanguageTag(deviceLocale).country} ?: same.firstOrNull() }
        match(remembered)?.let{return Selection(it,false)}
        val pref=match(preferred)
        if(autoDub && pref!=null)return Selection(pref,false)
        if(batch)return Selection(original,false)
        return Selection(pref ?: original,ask)
    }
    fun afterRefresh(tracks:List<AudioTrackOption>,selection:Selection,previous:AudioTrackOption?,explicit:Boolean,wasPrompt:Boolean=false):Selection {
        if(!explicit || previous==null)return selection.copy(prompt=wasPrompt || selection.prompt)
        val restored=restore(tracks,previous.id,previous.locale,previous.type)
        return Selection(restored,wasPrompt || restored==null)
    }
    /** Exact semantic identity only: never resume bytes from a different language or audio type. */
    fun restore(tracks:List<AudioTrackOption>,id:String,locale:String?,type:String?):AudioTrackOption? {
        fun matches(t:AudioTrackOption)=(locale.isNullOrBlank() || t.locale.equals(locale,true)) && (type==null || type=="UNKNOWN" || t.type==type)
        tracks.firstOrNull{it.id==id && matches(it)}?.let{return it}
        if(locale.isNullOrBlank() || type==null)return null
        return tracks.filter{matches(it)}.singleOrNull()
    }
    fun sorted(tracks: List<AudioTrackOption>,preferred: String)=tracks.sortedWith(compareBy<AudioTrackOption>{if(language(it.locale)==language(preferred))0 else if(it.type=="ORIGINAL")1 else 2}.thenBy{Locale.forLanguageTag(it.locale).getDisplayLanguage(Locale.forLanguageTag(it.locale))})
}
