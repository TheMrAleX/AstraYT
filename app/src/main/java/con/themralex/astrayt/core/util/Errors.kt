package con.themralex.astrayt.core.util
import con.themralex.astrayt.R
import kotlinx.coroutines.TimeoutCancellationException
import con.themralex.astrayt.youtube.ExtractionTimeout
import con.themralex.astrayt.youtube.NoStreams
object Errors {
    fun code(error:Throwable):String {
        val causes=generateSequence(error){it.cause}.take(12).toList()
        if(causes.any{it is ExtractionTimeout || it is TimeoutCancellationException})return "TIMEOUT"
        if(causes.any{it is java.net.SocketTimeoutException || it is java.net.UnknownHostException || it is java.net.ConnectException})return "NETWORK"
        if(error is con.themralex.astrayt.download.SponsorBlockFailure)return "SPONSORBLOCK"
        if(error is NoStreams)return "NO_STREAMS"
        val name=error.javaClass.simpleName;val msg=error.message.orEmpty().lowercase();return when {
        "AgeRestricted" in name || "LoginRequired" in name->"AGE"
        "Private" in name || "private" in msg->"PRIVATE"
        "Geographic" in name || "country" in msg->"GEO"
        "Paid" in name || "members" in msg || "premium" in msg->"MEMBERS"
        "ReCaptcha" in name || "429" in msg->"RATE"
        "live_stream" in msg->"LIVE"
        "audio_unavailable" in msg->"AUDIO_UNAVAILABLE"
        "subtitle_format" in msg->"SUBTITLE_FORMAT"
        "subtitle_size" in msg->"SUBTITLE_SIZE"
        "subtitle_download" in msg->"SUBTITLE_DOWNLOAD"
        "sponsorblock_empty" in msg->"EMPTY_CUT"
        "disk_full" in msg->"DISK"
        "Unsupported" in name || "unsupported_url" in msg->"URL"
        "ContentNotAvailable" in name || "removed" in msg->"REMOVED"
        "no_streams" in msg->"NO_STREAMS"
        "Parsing" in name || "Extraction" in name->"EXTRACTOR"
        error is SecurityException || "storage" in msg->"STORAGE"
        error is java.io.IOException->"NETWORK"
        else->"GENERIC"
    }}
    fun string(code:String?)=when(code){"SUBTITLE_DOWNLOAD"->R.string.subtitle_failed;"AUDIO_UNAVAILABLE"->R.string.error_audio_unavailable;"SUBTITLE_FORMAT"->R.string.error_subtitle_format;"SUBTITLE_SIZE"->R.string.error_subtitle_size;"SPONSORBLOCK"->R.string.error_sponsorblock;"EMPTY_CUT"->R.string.error_empty_cut;"TIMEOUT"->R.string.error_timeout;"NO_STREAMS"->R.string.error_no_streams;"NETWORK"->R.string.error_network;"AGE"->R.string.error_age;"PRIVATE"->R.string.error_private;"GEO"->R.string.error_geo;"MEMBERS"->R.string.error_members;"RATE"->R.string.error_rate;"LIVE"->R.string.error_live;"DISK"->R.string.error_disk;"URL"->R.string.error_url;"REMOVED"->R.string.error_removed;"EXTRACTOR"->R.string.error_extractor;"STORAGE"->R.string.error_storage;else->R.string.error_generic}
}
