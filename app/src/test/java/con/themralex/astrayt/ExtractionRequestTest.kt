package con.themralex.astrayt

import con.themralex.astrayt.youtube.*
import con.themralex.astrayt.core.util.Errors
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class ExtractionRequestTest {
    @Test fun simultaneousCallersShareWorkEvenWhenCaptureCancels() = runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val request=ExtractionRequest<Int>(scope)
            val started=CompletableDeferred<Unit>();val finish=CompletableDeferred<Unit>();val calls=AtomicInteger()
            val capture=async {request.get("video") {calls.incrementAndGet();started.complete(Unit);finish.await();42}}
            started.await()
            val queue=async(start=CoroutineStart.UNDISPATCHED) {request.get("video"){error("Duplicate extraction")}}
            capture.cancelAndJoin();finish.complete(Unit)
            assertEquals(42,queue.await());assertEquals(1,calls.get())
        } finally {scope.cancel()}
    }
    @Test fun failedFlightCanBeRetriedImmediately() = runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val request=ExtractionRequest<Int>(scope)
            try {request.get("video"){throw IOException("offline")};fail()}catch(_:IOException){}
            assertEquals(7,request.get("video"){7})
        } finally {scope.cancel()}
    }
    @Test fun deadlineIsFailureNotUserCancellation() = runBlocking {
        try {extractionAttempt(20){awaitCancellation()};fail()}catch(e:ExtractionTimeout){assertEquals("TIMEOUT",Errors.code(e))}
        assertTrue(currentCoroutineContext().isActive)
    }
    @Test fun explicitCancellationStillPropagates() = runBlocking {
        val started=CompletableDeferred<Unit>()
        val job=async {extractionAttempt {started.complete(Unit);awaitCancellation()}}
        started.await();job.cancelAndJoin();assertTrue(job.isCancelled)
    }
    @Test fun unavailableFormatsDoNotAskForAnAppUpdate() {
        assertEquals("NO_STREAMS",Errors.code(NoStreams()))
        assertEquals("NETWORK",Errors.code(IOException("wrapped",java.net.UnknownHostException())))
    }
}
