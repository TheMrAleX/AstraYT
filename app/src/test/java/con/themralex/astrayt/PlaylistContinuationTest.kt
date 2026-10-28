package con.themralex.astrayt

import java.util.Base64
import org.junit.Assert.*
import org.junit.Test
import org.schabi.newpipe.extractor.services.youtube.protos.playlist.PlaylistProtobufContinuation.ContinuationParams
import org.schabi.newpipe.extractor.services.youtube.protos.playlist.PlaylistProtobufContinuation.PlaylistContinuation

/** Exercise the playlist-only protocol used before NewPipe can list any entries. */
class PlaylistContinuationTest {
    @Test fun playlistContinuationPreservesBothIdsAndUnavailableVideoFlag() {
        val id="PLdhkfDGRVbD4GkFHwqpqaxufXqQWUCm_Z"
        val request=PlaylistContinuation.newBuilder().setParameters(ContinuationParams.newBuilder()
            .setBrowseId("VL$id").setPlaylistId(id)
            .setContinuationProperties("CADCBgIIAA%3D%3D").build()).build()
        val encoded=Base64.getUrlEncoder().encodeToString(request.toByteArray())
        val restored=PlaylistContinuation.parseFrom(Base64.getUrlDecoder().decode(encoded))
        assertTrue(restored.hasParameters())
        assertEquals("VL$id",restored.parameters.browseId)
        assertEquals(id,restored.parameters.playlistId)
        assertEquals("CADCBgIIAA%3D%3D",restored.parameters.continuationProperties)
    }
}
