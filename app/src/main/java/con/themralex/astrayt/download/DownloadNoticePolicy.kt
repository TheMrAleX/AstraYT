package con.themralex.astrayt.download

/** Terminal states are events, not progress updates. Old failures are not replayed on restart. */
internal object DownloadNoticePolicy {
    enum class Action { UPDATE, TERMINAL, REMOVE, NONE }
    fun action(state:String,previous:String?,updatedAt:Long,startedAt:Long):Action = when {
        state=="CANCELED"->if(previous!="CANCELED")Action.REMOVE else Action.NONE
        state in setOf("QUEUED","FETCHING","DOWNLOADING","MUXING","SUBTITLES","WAITING_NETWORK")->Action.UPDATE
        state in setOf("DONE","FAILED","PAUSED") && state!=previous && (previous!=null || updatedAt>=startedAt)->Action.TERMINAL
        else->Action.NONE
    }
}
internal const val QUIET_ISSUES_CHANNEL="downloads_issues_quiet"
