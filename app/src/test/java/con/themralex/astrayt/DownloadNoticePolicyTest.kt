package con.themralex.astrayt
import con.themralex.astrayt.download.DownloadNoticePolicy
import con.themralex.astrayt.download.DownloadNoticePolicy.Action
import org.junit.Assert.*
import org.junit.Test
class DownloadNoticePolicyTest {
    @Test fun failedDownloadDoesNotAlertEveryProgressTick() {
        assertEquals(Action.TERMINAL,DownloadNoticePolicy.action("FAILED","DOWNLOADING",110,100))
        repeat(10){assertEquals(Action.NONE,DownloadNoticePolicy.action("FAILED","FAILED",110,100))}
    }
    @Test fun cancellationOnlyRemovesNotification() {
        listOf("QUEUED","DOWNLOADING","FAILED","PAUSED").forEach{assertEquals(Action.REMOVE,DownloadNoticePolicy.action("CANCELED",it,120,100))}
        assertEquals(Action.NONE,DownloadNoticePolicy.action("CANCELED","CANCELED",120,100))
    }
    @Test fun restartingServiceDoesNotReplayOldTerminals() {
        listOf("DONE","FAILED","PAUSED").forEach{assertEquals(Action.NONE,DownloadNoticePolicy.action(it,null,90,100))}
        assertEquals(Action.TERMINAL,DownloadNoticePolicy.action("DONE",null,110,100))
    }
    @Test fun progressStaysLiveAndRetryCanFinishOnce() {
        assertEquals(Action.UPDATE,DownloadNoticePolicy.action("DOWNLOADING","DOWNLOADING",110,100))
        assertEquals(Action.UPDATE,DownloadNoticePolicy.action("QUEUED","FAILED",110,100))
        assertEquals(Action.TERMINAL,DownloadNoticePolicy.action("DONE","DOWNLOADING",120,100))
        assertEquals(Action.NONE,DownloadNoticePolicy.action("DONE","DONE",120,100))
    }
}
