package con.themralex.astrayt.youtube

import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ExtractionTimeout(cause: Throwable) : IOException("EXTRACTION_TIMEOUT", cause)
class NoStreams(cause: Throwable? = null) : IOException("NO_STREAMS", cause)

/** Shared callers survive each other's cancellation; failed flights never poison later requests. */
internal class ExtractionRequest<T>(private val scope: CoroutineScope) {
    private val mutex = Mutex()
    private val flights = mutableMapOf<String, Deferred<T>>()
    suspend fun get(key: String, block: suspend () -> T): T {
        val flight = mutex.withLock {
            flights[key] ?: scope.async(start = CoroutineStart.LAZY) { block() }.also { flight ->
                flights[key] = flight
                flight.invokeOnCompletion {
                    scope.launch { mutex.withLock { if (flights[key] === flight) flights.remove(key) } }
                }
                flight.start()
            }
        }
        try { return flight.await() }
        finally {
            if (flight.isCompleted) withContext(NonCancellable) {
                mutex.withLock { if (flights[key] === flight) flights.remove(key) }
            }
        }
    }
}

internal suspend fun <T> extractionAttempt(timeoutMs: Long = 45000, block: suspend () -> T): T =
    try { withTimeout(timeoutMs) { block() } }
    catch (e: TimeoutCancellationException) { currentCoroutineContext().ensureActive(); throw ExtractionTimeout(e) }
