package con.themralex.astrayt
import con.themralex.astrayt.youtube.*
import con.themralex.astrayt.download.*
import con.themralex.astrayt.core.util.Formatters
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale
class LogicTest {
    private fun track(id: String,language: String,type: String="DUBBED")=AudioTrackOption(id,language,type,id,emptyList())
    @Test fun parsesSupportedUrls() {val id="dQw4w9WgXcQ";listOf("https://youtube.com/watch?v=$id&si=tracking","https://youtu.be/$id?t=1","https://m.youtube.com/shorts/$id","https://music.youtube.com/watch?v=$id","https://www.youtube.com/live/$id","https://www.youtube.com/embed/$id").forEach{assertEquals(it,YoutubeLink.Video(id),UrlParser.parse(it))};assertEquals(YoutubeLink.Playlist("PL123"),UrlParser.parse("https://youtube.com/playlist?list=PL123"))}
    @Test fun rejectsSpoofedAndMalformedUrls(){listOf("https://youtube.com.evil.org/watch?v=dQw4w9WgXcQ","https://evil.org/youtube.com/watch?v=dQw4w9WgXcQ","https://youtube.com/watch?v=bad","file:///watch?v=dQw4w9WgXcQ","https://youtube.com@evil.org/watch?v=dQw4w9WgXcQ").forEach{assertNull(it,UrlParser.parse(it))}}
    @Test fun originalPreferredNeverPrompts(){val original=track("o","es-ES","ORIGINAL");assertEquals(AudioTrackSelector.Selection(original,false),AudioTrackSelector.select(listOf(original,track("d","en")),"es-419"))}
    @Test fun remembersChannelBeforeAutoDub(){val tracks=listOf(track("o","en","ORIGINAL"),track("es","es-419"),track("fr","fr"));assertEquals("fr",AudioTrackSelector.select(tracks,"es",remembered="fr",autoDub=true).track?.id)}
    @Test fun defaultsPromptAndPreselect(){val ts=listOf(track("o","en","ORIGINAL"),track("es","es-ES"),track("lat","es-419"));val s=AudioTrackSelector.select(ts,"es",deviceLocale="es-419");assertTrue(s.prompt);assertEquals("lat",s.track?.id);assertEquals("es",AudioTrackSelector.select(ts,"es-ES",deviceLocale="es-ES").track?.id)}
    @Test fun batchFallsBackToOriginal(){val ts=listOf(track("o","en","ORIGINAL"),track("es","es"));assertEquals(AudioTrackSelector.Selection(ts[0],false),AudioTrackSelector.select(ts,"es",batch=true));assertEquals("es",AudioTrackSelector.select(ts,"es",batch=true,autoDub=true).track?.id)}
    @Test fun neverAutoSelectsDescriptive(){val ts=listOf(track("desc","es","DESCRIPTIVE"),track("o","en","ORIGINAL"));assertEquals("o",AudioTrackSelector.select(ts,"es",autoDub=true).track?.id);assertNull(AudioTrackSelector.select(ts.take(1),"es").track)}
    @Test fun disabledPromptStillPrefersLanguage(){val ts=listOf(track("o","en","ORIGINAL"),track("es","es"));val s=AudioTrackSelector.select(ts,"es",ask=false);assertFalse(s.prompt);assertEquals("es",s.track?.id)}
    @Test fun chunkCoverageHasNoGaps(){val length=ChunkPlanner.CHUNK_SIZE*2+17;val chunks=ChunkPlanner.plan(length);assertEquals(3,chunks.size);assertEquals(17,chunks.last().length);assertEquals(length,chunks.sumOf{it.length});chunks.zipWithNext().forEach{(a,b)->assertEquals(a.end+1,b.start)};assertTrue(ChunkPlanner.plan(0).isEmpty())}
    @Test fun speedAndEtaUseTransferredBytes(){val m=TransferMeter();m.sample(0,0,10000);val (speed,eta)=m.sample(1000,2000,10000);assertEquals(2000L,speed);assertEquals(4L,eta);assertEquals(0L,m.sample(2000,0,10000).first)}
    @Test fun formattersRespectLocale(){assertEquals("1,5 MB",Formatters.bytes(1572864,Locale.forLanguageTag("es")));assertEquals("1:01:01",Formatters.duration(3661000));assertEquals("0:00",Formatters.duration(-1))}
    @Test fun plannerChoosesCompatiblePairs(){val a=MediaStream("a","https://x","m4a",bitrate=128000);val w=MediaStream("w","https://x","webm","opus",128000);val t=track("o","en","ORIGINAL").copy(streams=listOf(a,w));val vs=listOf(MediaStream("1080","https://x","mp4","avc1",4000000,1080,60,videoOnly=true),MediaStream("2160","https://x","webm","vp09",8000000,2160,30,videoOnly=true));val d=VideoDetails("id","t","c","c","","",10000,0,0,"",vs,listOf(t));val options=StreamPlanner.options(d,t);assertEquals(listOf("mp4","webm"),options.map{it.container});assertEquals("a",options.first().audio?.id);assertEquals("w",options.last().audio?.id);assertEquals(1080,StreamPlanner.default(options,1080)?.height);assertTrue(options.first().estimatedBytes>0);assertEquals("m4a",StreamPlanner.options(d,t,true).single().container)}

    @Test fun stateSurvivesRestartAndRejectsWrongLength() {
        val dir=java.nio.file.Files.createTempDirectory("astrayt-state").toFile()
        try {
            val part=java.io.File(dir,"video.part")
            java.io.RandomAccessFile(part,"rw").use{it.setLength(1024)}
            val state=ChunkState(dir)
            state.prepare("video",1024,part)
            state.done("video",0)
            state.save()
            val restored=ChunkState(dir)
            assertTrue(restored.prepare("video",1024,part)[0])
            assertFalse(restored.has("video",1))
            java.io.RandomAccessFile(part,"rw").use{it.setLength(10)}
            assertTrue(restored.prepare("video",1024,part).isEmpty)
        } finally {dir.deleteRecursively()}
    }
    @Test fun corruptStateDoesNotClaimCompletedChunks() {
        val dir=java.nio.file.Files.createTempDirectory("astrayt-corrupt").toFile()
        try {
            java.io.File(dir,"state.bin").writeBytes(byteArrayOf(1,2,3))
            val part=java.io.File(dir,"audio.part").apply{writeBytes(ByteArray(10))}
            assertTrue(ChunkState(dir).prepare("audio",10,part).isEmpty)
        } finally {dir.deleteRecursively()}
    }
    @Test fun embeddedShareTextIsAccepted() {assertEquals(YoutubeLink.Video("dQw4w9WgXcQ"),UrlParser.parse("Mira este vídeo https://youtu.be/dQw4w9WgXcQ?t=42"))}
    @Test fun unavailableAudioFormatProducesNoInvalidMuxPlan() {
        val t=track("o","en","ORIGINAL").copy(streams=listOf(MediaStream("a","https://x","m4a","mp4a",128000)))
        val d=VideoDetails("id","t","c","c","","",10000,0,0,"",listOf(MediaStream("v","https://x","webm","vp09",8000000,2160,30,videoOnly=true)),listOf(t))
        assertTrue(StreamPlanner.options(d,t).isEmpty())
    }
}
