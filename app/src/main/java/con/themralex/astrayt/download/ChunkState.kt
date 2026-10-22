package con.themralex.astrayt.download

import java.io.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.BitSet
import java.util.zip.CRC32

/** Offsets describe only bytes forced to disk before the manifest is committed. */
class ChunkState(private val dir: File) {
    private data class Entry(val length: Long, val identity: String, val offsets: LongArray)
    private val entries = mutableMapOf<String, Entry>()
    init {
        // An incomplete temporary write never replaces the previous valid checkpoint.
        runCatching {
            val bytes = File(dir, "state.bin").readBytes()
            require(bytes.size in 16..2_000_000)
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                val checksum = input.readLong()
                val payload = bytes.copyOfRange(8, bytes.size)
                require(CRC32().apply { update(payload) }.value == checksum)
                require(input.readInt() == 0x41535432)
                repeat(input.readInt().also { require(it in 0..2) }) {
                    val key = input.readUTF()
                    val length = input.readLong().also { require(it > 0) }
                    val identity = input.readUTF()
                    require(length <= 200_000L*ChunkPlanner.CHUNK_SIZE)
                    val chunks = ChunkPlanner.plan(length)
                    val count = input.readInt().also { require(it == chunks.size && it <= 200_000) }
                    val offsets = LongArray(count) { index -> input.readLong().also { require(it in 0..chunks[index].length) } }
                    require(entries.put(key, Entry(length, identity, offsets)) == null)
                }
                require(input.available() == 0)
            }
        }.onFailure { entries.clear() }
    }
    @Synchronized fun prepare(key: String, length: Long, file: File, identity: String = ""): BitSet {
        val chunks = ChunkPlanner.plan(length)
        val old = entries[key]
        val entry = old?.takeIf { it.length == length && it.identity == identity && file.length() == length }
            ?: Entry(length, identity, LongArray(chunks.size))
        entries[key] = entry
        return BitSet().apply { chunks.forEach { if (entry.offsets[it.index] == it.length) set(it.index) } }
    }
    private fun chunkLength(entry:Entry,index:Int)=minOf(ChunkPlanner.CHUNK_SIZE,entry.length-index*ChunkPlanner.CHUNK_SIZE)
    @Synchronized fun validatedBytes():Long=entries.entries.sumOf{(key,entry)->if(File(dir,"$key.part").length()==entry.length)entry.offsets.sum() else 0L}
    @Synchronized fun offset(key: String, index: Int): Long = entries.getValue(key).offsets[index]
    @Synchronized fun bytes(): Long = entries.values.sumOf { it.offsets.sum() }
    @Synchronized fun done(key: String, index: Int) { advance(key, index, chunkLength(entries.getValue(key),index)) }
    @Synchronized fun has(key: String, index: Int): Boolean {
        val entry = entries.getValue(key)
        return index in entry.offsets.indices && entry.offsets[index] == chunkLength(entry,index)
    }
    @Synchronized fun advance(key: String, index: Int, bytes: Long) {
        val entry = entries.getValue(key)
        require(bytes in entry.offsets[index]..chunkLength(entry,index))
        entry.offsets[index] = bytes
    }
    @Synchronized fun save() {
        dir.mkdirs()
        val payload = ByteArrayOutputStream().apply {
            DataOutputStream(this).use { out ->
                out.writeInt(0x41535432); out.writeInt(entries.size)
                entries.forEach { (key, entry) ->
                    out.writeUTF(key); out.writeLong(entry.length); out.writeUTF(entry.identity)
                    out.writeInt(entry.offsets.size); entry.offsets.forEach(out::writeLong)
                }
            }
        }.toByteArray()
        val temp = File(dir, "state.tmp")
        FileOutputStream(temp).use { raw ->
            val out = DataOutputStream(raw)
            out.writeLong(CRC32().apply { update(payload) }.value); out.write(payload)
            out.flush(); raw.fd.sync()
        }
        Files.move(temp.toPath(), File(dir, "state.bin").toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
}
