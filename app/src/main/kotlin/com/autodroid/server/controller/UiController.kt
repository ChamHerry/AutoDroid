package com.autodroid.server.controller

import com.autodroid.adapter.AutomatorAdapter
import com.autodroid.server.ApiException
import com.autodroid.server.HttpServer
import com.autodroid.server.Request
import com.autodroid.server.Response

import org.json.JSONArray
import org.json.JSONObject

/**
 * Parses a selector from the request body, finds a single matching node within the
 * given timeout, and executes [action] on it. The node is always released in a finally block.
 *
 * Throws [ApiException] with ELEMENT_NOT_FOUND if no element was found.
 */
private suspend inline fun withFoundNode(
    req: Request,
    res: Response,
    automator: AutomatorAdapter,
    notFoundMessage: String = "Element not found",
    crossinline action: suspend (Long) -> Unit,
) {
    val body = req.jsonBody
    val selectorJson = body.getJSONObject("selector").toString()
    val timeout = body.optLong("timeout", 5000L).coerceIn(100L, 60_000L)
    val handle = automator.findOne(selectorJson, timeout)
        ?: throw ApiException(404, notFoundMessage, ApiException.ELEMENT_NOT_FOUND)
    try {
        action(handle)
    } finally {
        automator.releaseNode(handle)
    }
}

fun registerUiRoutes(server: HttpServer, automator: AutomatorAdapter) {

    // GET /api/ui/dump  - Dump the full accessibility tree as JSON (all windows)
    server.get("/api/ui/dump") { _, res ->
        val treeJson = automator.dumpUiTree()
            ?: throw ApiException(503, "Accessibility service not connected or no accessible windows found", ApiException.SERVICE_UNAVAILABLE)
        res.sendRawJson(treeJson)
    }

    // POST /api/ui/find  { "selector": {...}, "max": 10 }
    server.post("/api/ui/find") { req, res ->
        val body = req.jsonBody
        val selectorJson = body.getJSONObject("selector").toString()
        val max = body.optInt("max", 10)
        val handles = automator.find(selectorJson, max)
        val results = JSONArray()
        val released = mutableSetOf<Long>()
        try {
            for (handle in handles) {
                val nodeJson = automator.getNodeInfo(handle)
                if (nodeJson != null) results.put(nodeJson)
                automator.releaseNode(handle)
                released.add(handle)
            }
        } catch (e: Exception) {
            // Release any handles not yet released
            handles.filter { it !in released }.forEach { automator.releaseNode(it) }
            throw e
        }
        res.sendJson(results)
    }

    // POST /api/ui/click  { "selector": {...}, "timeout": 5000 }
    server.post("/api/ui/click") { req, res ->
        withFoundNode(req, res, automator) { handle ->
            val clicked = automator.click(handle)
            res.sendJson(mapOf("clicked" to clicked))
        }
    }

    // POST /api/ui/input  { "selector": {...}, "text": "hello", "timeout": 5000 }
    server.post("/api/ui/input") { req, res ->
        val text = req.jsonBody.getString("text")
        withFoundNode(req, res, automator) { handle ->
            val result = automator.setText(handle, text)
            res.sendJson(mapOf("inputSet" to result))
        }
    }

    // POST /api/ui/scroll  { "selector": {...}, "direction": "forward"|"backward", "timeout": 5000 }
    server.post("/api/ui/scroll") { req, res ->
        val direction = req.jsonBody.optString("direction", "forward")
        withFoundNode(req, res, automator, notFoundMessage = "Scrollable element not found") { handle ->
            val result = when (direction.lowercase()) {
                "forward", "down" -> automator.scrollForward(handle)
                "backward", "up" -> automator.scrollBackward(handle)
                else -> throw IllegalArgumentException("Unknown direction: $direction")
            }
            res.sendJson(mapOf("scrolled" to result))
        }
    }

    // POST /api/ui/wait  { "selector": {...}, "timeout": 10000 }
    server.post("/api/ui/wait") { req, res ->
        val body = req.jsonBody
        val selectorJson = body.getJSONObject("selector").toString()
        val timeout = body.optLong("timeout", 10000L).coerceIn(100L, 60_000L)
        val pollInterval = body.optLong("pollInterval", 500L).coerceIn(100L, 5_000L)

        val deadline = System.currentTimeMillis() + timeout
        var handle: Long? = null
        while (System.currentTimeMillis() < deadline) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) break
            handle = automator.findOne(selectorJson, remaining.coerceAtMost(pollInterval))
            if (handle != null) break
            kotlinx.coroutines.delay(pollInterval.coerceAtMost(remaining))
        }

        if (handle == null) {
            throw ApiException(408, "Element not found within timeout", ApiException.TIMEOUT)
        }
        val data = automator.getNodeInfo(handle)
        automator.releaseNode(handle)
        res.sendJson(data ?: JSONObject())
    }

    // POST /api/ui/release  { "handle": 1 }
    server.post("/api/ui/release") { req, res ->
        val handle = req.jsonBody.getLong("handle")
        automator.releaseNode(handle)
        res.sendJson(mapOf("released" to true))
    }

    // POST /api/ui/releaseAll
    server.post("/api/ui/releaseAll") { _, res ->
        automator.releaseAllNodes()
        res.sendJson(mapOf("released" to true))
    }
}
