package con.themralex.astrayt.download
import java.io.*
import java.util.BitSet
class ChunkState(private val dir:File) {
    private data class Entry(val length:Long,val bits:BitSet)
    private val entries=mutableMapOf<String,Entry>()
    init { runCatching { DataInputStream(FileInputStream(File(dir,"state.bin"))).use{input->require(input.readInt()==0x41535431);repeat(input.readInt().also{require(it in 0..2)}){val key=input.readUTF();val length=input.readLong();val count=input.readInt();require(count in 0..1_000_000);val data=ByteArray(count);input.readFully(data);entries[key]=Entry(length,BitSet.valueOf(data))}} }.onFailure{entries.clear()} }
    @Synchronized fun prepare(key:String,length:Long,file:File):BitSet {val old=entries[key];val bits=if(old?.length==length && file.length()==length)old.bits else BitSet();entries[key]=Entry(length,bits);return bits}
    @Synchronized fun done(key:String,index:Int){entries.getValue(key).bits.set(index)}
    @Synchronized fun has(key:String,index:Int)=entries.getValue(key).bits.get(index)
    @Synchronized fun save(){dir.mkdirs();val temp=File(dir,"state.tmp");FileOutputStream(temp).use{raw->val out=DataOutputStream(raw);out.writeInt(0x41535431);out.writeInt(entries.size);entries.forEach{(key,value)->out.writeUTF(key);out.writeLong(value.length);val bytes=value.bits.toByteArray();out.writeInt(bytes.size);out.write(bytes)};out.flush();raw.fd.sync()};val target=File(dir,"state.bin");if(!temp.renameTo(target))throw IOException("Could not persist download state")}
}
