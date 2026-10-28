package con.themralex.astrayt

import con.themralex.astrayt.data.db.*
import con.themralex.astrayt.download.*
import con.themralex.astrayt.feature.*
import con.themralex.astrayt.youtube.*
import org.junit.Assert.*
import org.junit.Test

class CollectionPolicyTest {
    private fun row(id:Long,state:String,group:Long?=1)=DownloadEntity(id=id,ytId="dQw4w9WgXcQ",url="",title="",channelName="",thumbnailPath="",kind="VIDEO",optionsJson="{}",state=state,playlistId=group)
    private fun video(id:Long,ytId:String)=VideoEntity(id=id,ytId=ytId,title=ytId,channelId="",channelName="",durationMs=0,contentUri="",fileSizeBytes=0,mimeType="video/mp4",kind="VIDEO")
    @Test fun videoIsEnqueueableDuringAnalysisAndAfterFailure() {
        val state=CaptureState(url="https://youtu.be/dQw4w9WgXcQ",loading=true)
        assertTrue(CapturePolicy.canEnqueue(state))
        assertTrue(CapturePolicy.canEnqueue(state.copy(loading=false,error="TIMEOUT")))
        assertFalse(CapturePolicy.canEnqueue(state.copy(busy=true)))
        assertFalse(CapturePolicy.canEnqueue(state.copy(url="https://example.com/dQw4w9WgXcQ")))
    }
    @Test fun collectionRequiresAnActualSelectedEntry() {
        val list=ResolvedLink.Playlist("url","Title","",listOf(PlaylistEntry("dQw4w9WgXcQ","","","",0)))
        assertFalse(CapturePolicy.canEnqueue(CaptureState(playlist=list,selected=setOf("stale"))))
        assertTrue(CapturePolicy.canEnqueue(CaptureState(playlist=list,selected=setOf("dQw4w9WgXcQ"))))
    }
    @Test fun playlistPlaybackPreservesSourceOrderAndSkipsMissingFiles() {
        val all=listOf(video(1,"b"),video(2,"a"),video(3,"other"))
        val memberships=listOf(PlaylistMembership(1,"b",2),PlaylistMembership(1,"missing",1),PlaylistMembership(1,"a",0),PlaylistMembership(2,"other",0))
        assertEquals(listOf("a","b"),CollectionPolicy.videos(1,memberships,all).map{it.ytId})
        assertEquals(listOf("other"),CollectionPolicy.videos(2,memberships,all).map{it.ytId})
    }
    @Test fun completedAndUnknownLengthChildrenHaveHonestAggregateProgress() {
        val rows=listOf(row(1,"DONE"),row(2,"DOWNLOADING"),row(3,"QUEUED"),row(4,"QUEUED"))
        assertEquals(.375f,CollectionPolicy.fraction(rows,mapOf(2L to Progress(50,100,10,5))),.0001f)
        assertEquals("DOWNLOADING",CollectionPolicy.state(rows))
        assertEquals(0f,CollectionPolicy.fraction(emptyList(),emptyMap()),0f)
    }
    @Test fun aPlaylistCountsAsOneTaskBesideLooseDownloads() {
        assertEquals(3,CollectionPolicy.taskCount(listOf(row(1,"QUEUED"),row(2,"QUEUED"),row(3,"QUEUED"),row(4,"QUEUED"),row(5,"QUEUED",null),row(6,"QUEUED",null))))
    }
    @Test fun partialFailuresNeverReportCompletedAndRemainResumable() {
        assertEquals("FAILED",CollectionPolicy.state(listOf(row(1,"DONE"),row(2,"FAILED"))))
        assertEquals("PAUSED",CollectionPolicy.state(listOf(row(1,"DONE"),row(2,"PAUSED"))))
        assertEquals("DONE",CollectionPolicy.state(listOf(row(1,"DONE"),row(2,"DONE"))))
        assertEquals("CANCELED",CollectionPolicy.state(listOf(row(1,"DONE"),row(2,"CANCELED"))))
        assertEquals("WAITING_NETWORK",CollectionPolicy.state(listOf(row(1,"FAILED"),row(2,"WAITING_NETWORK"))))
    }
}
