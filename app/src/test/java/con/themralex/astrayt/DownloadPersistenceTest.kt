package con.themralex.astrayt

import con.themralex.astrayt.download.*
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.file.Files
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class DownloadPersistenceTest {
    private fun temporary(test: (File) -> Unit) {
        val dir=Files.createTempDirectory("download-recovery").toFile()
        try { test(dir) } finally { dir.deleteRecursively() }
    }
    @Test fun restartResumesPartialParallelChunksAndReconstructsExactFile() = temporary { dir ->
        val expected=ByteArray((ChunkPlanner.CHUNK_SIZE*2+913).toInt()){(it*31+7).toByte()}
        val part=File(dir,"video.part")
        val chunks=ChunkPlanner.plan(expected.size.toLong())
        val state=ChunkState(dir)
        state.prepare("video",expected.size.toLong(),part,"video-137-es")
        RandomAccessFile(part,"rw").use { file ->
            file.setLength(expected.size.toLong())
            // Simulate workers reaching different offsets, including a fully downloaded last chunk.
            listOf(181_123L,72_019L,913L).forEachIndexed { index, count ->
                file.channel.write(ByteBuffer.wrap(expected,chunks[index].start.toInt(),count.toInt()),chunks[index].start)
                file.channel.force(true)
                state.advance("video",index,count);state.save()
            }
        }
        // No final save/cleanup: a new instance only sees the last durable checkpoint.
        val recovered=ChunkState(dir)
        val completed=recovered.prepare("video",expected.size.toLong(),part,"video-137-es")
        assertTrue(completed[2]);assertFalse(completed[0])
        assertEquals(254_055L,recovered.bytes())
        RandomAccessFile(part,"rw").use { file ->
            chunks.filterNot{completed[it.index]}.forEach { chunk ->
                val start=chunk.start+recovered.offset("video",chunk.index)
                val count=(chunk.end-start+1).toInt()
                file.channel.write(ByteBuffer.wrap(expected,start.toInt(),count),start)
                file.channel.force(true)
                recovered.done("video",chunk.index);recovered.save()
            }
        }
        assertArrayEquals(expected,part.readBytes())
        assertEquals(expected.size.toLong(),ChunkState(dir).validatedBytes())
    }
    @Test fun sameSizeDifferentAudioOrVideoNeverReusesBytes() = temporary { dir ->
        val part=File(dir,"audio.part").apply{writeBytes(ByteArray(100))}
        val state=ChunkState(dir)
        state.prepare("audio",100,part,"140-original");state.advance("audio",0,65);state.save()
        val restored=ChunkState(dir)
        restored.prepare("audio",100,part,"140-es")
        assertEquals(0L,restored.offset("audio",0))
    }
    @Test fun incompleteTemporaryCheckpointCannotEraseCommittedProgress() = temporary { dir ->
        val part=File(dir,"video.part").apply{writeBytes(ByteArray(100))}
        ChunkState(dir).apply{prepare("video",100,part,"137");advance("video",0,73);save()}
        File(dir,"state.tmp").writeBytes(byteArrayOf(1,2,3))
        val restored=ChunkState(dir)
        restored.prepare("video",100,part,"137")
        assertEquals(73L,restored.offset("video",0))
    }
    @Test fun corruptCheckpointCannotClaimUndownloadedBytes() = temporary { dir ->
        val part=File(dir,"video.part").apply{writeBytes(ByteArray(100))}
        ChunkState(dir).apply{prepare("video",100,part,"137");done("video",0);save()}
        val manifest=File(dir,"state.bin")
        manifest.writeBytes(manifest.readBytes().apply{this[lastIndex]=(this[lastIndex].toInt() xor 1).toByte()})
        assertEquals(0L,ChunkState(dir).validatedBytes())
    }
    @Test fun truncatedPartCannotKeepOldProgress() = temporary { dir ->
        val part=File(dir,"video.part").apply{writeBytes(ByteArray(100))}
        ChunkState(dir).apply{prepare("video",100,part,"137");done("video",0);save()}
        part.writeBytes(ByteArray(10))
        val restored=ChunkState(dir)
        assertEquals(0L,restored.validatedBytes())
        assertTrue(restored.prepare("video",100,part,"137").isEmpty)
    }
    @Test fun pinnedPlanSurvivesJsonAndOldQueueOptionsStillDecode() {
        val options=DownloadOptions(trackId="es",height=720,container="mp4",pinnedVideoId="136",pinnedAudioId="140",planPinned=true)
        assertEquals(options,Json.decodeFromString<DownloadOptions>(Json.encodeToString(options)))
        assertFalse(Json.decodeFromString<DownloadOptions>("{\"height\":720}").planPinned)
    }
}
