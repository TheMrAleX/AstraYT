package con.themralex.astrayt.download

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.ParcelFileDescriptor
import androidx.media3.common.util.MediaFormatUtil
import androidx.media3.muxer.BufferInfo
import androidx.media3.muxer.Mp4Muxer
import androidx.media3.muxer.SeekableMuxerOutput
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.nio.ByteBuffer

/** MP4 samples are buffered into chunks instead of one native/storage write per sample. */
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
internal class SampleMuxer(container:String,fd:FileDescriptor?,path:String?,batched:Boolean) {
    private val mp4:Mp4Muxer?
    private val platform:MediaMuxer?
    private var closed=false
    init {
        if(container!="webm" && batched) {
            // Duplicate the descriptor: the muxer owns its stream, the caller owns the original.
            val output=if(fd!=null)ParcelFileDescriptor.AutoCloseOutputStream(ParcelFileDescriptor.dup(fd))else FileOutputStream(path!!)
            try {
                mp4=Mp4Muxer.Builder(SeekableMuxerOutput.of(output)).setSampleBatchingEnabled(true)
                    .setSampleCopyingEnabled(true).setAttemptStreamableOutputEnabled(false).build()
            }catch(e:Exception){output.close();throw e}
            platform=null
        }else {
            mp4=null
            val format=if(container=="webm")MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            platform=if(fd!=null)MediaMuxer(fd,format)else MediaMuxer(path!!,format)
        }
    }
    fun addTrack(format:MediaFormat):Int = mp4?.addTrack(MediaFormatUtil.createFormatFromMediaFormat(format)) ?: platform!!.addTrack(format)
    fun start(){platform?.start()}
    fun writeSampleData(track:Int,data:ByteBuffer,info:MediaCodec.BufferInfo) {
        val writer=mp4
        if(writer==null){platform!!.writeSampleData(track,data,info);return}
        data.position(info.offset);data.limit(info.offset+info.size)
        // Copying is enabled because MediaStorage reuses this buffer for the next sample.
        writer.writeSampleData(track,data,BufferInfo(info.presentationTimeUs,info.size,info.flags))
    }
    fun stop(){if(mp4!=null){if(!closed){closed=true;mp4.close()}}else platform!!.stop()}
    fun release(){if(mp4!=null){if(!closed){closed=true;mp4.close()}}else platform!!.release()}
}
