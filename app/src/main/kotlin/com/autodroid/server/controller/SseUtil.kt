package com.autodroid.server.controller

import com.autodroid.server.Response
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

/** Shared SSE heartbeat interval used by all SSE controllers. */
const val SSE_HEARTBEAT_INTERVAL_MS = 30_000L

/**
 * Runs a standard SSE event loop with heartbeat support.
 *
 * 1. Calls [res.startSSE()] to initiate the SSE connection.
 * 2. Invokes [setup] to let the caller wire up event sources that feed the [channel].
 * 3. Enters a loop: drains events from [channel] and writes them via [res.sendSSE].
 *    Sends a heartbeat comment if no event arrives within [SSE_HEARTBEAT_INTERVAL_MS].
 * 4. On client disconnect (write failure) or cancellation, invokes [cleanup].
 *
 * @param res The HTTP response to stream SSE events on.
 * @param setup Called once before the loop starts. Use it to register listeners / launch
 *              collectors that send `Pair<event, data>` into the provided channel.
 * @param cleanup Called in the finally block to unregister listeners / cancel collectors.
 */
suspend fun sseLoop(
    res: Response,
    setup: (channel: Channel<Pair<String, String>>) -> Unit,
    cleanup: (channel: Channel<Pair<String, String>>) -> Unit,
) {
    res.startSSE()

    val channel = Channel<Pair<String, String>>(Channel.BUFFERED)
    setup(channel)

    try {
        while (true) {
            val event = withTimeoutOrNull(SSE_HEARTBEAT_INTERVAL_MS) {
                channel.receive()
            }
            if (event != null) {
                res.sendSSE(event.first, event.second)
            } else {
                res.sendHeartbeat()
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // Client disconnected (write failed) or channel closed
    } finally {
        cleanup(channel)
        channel.close()
        res.closeSse()
    }
}
