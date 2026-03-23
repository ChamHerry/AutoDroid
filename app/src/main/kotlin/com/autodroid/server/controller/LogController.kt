package com.autodroid.server.controller

import com.autodroid.log.ConsoleRepository
import com.autodroid.log.LogEvent
import com.autodroid.server.HttpServer
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

fun registerLogRoutes(server: HttpServer, consoleRepo: ConsoleRepository) {

    // GET /api/logs?limit=100&offset=0
    server.get("/api/logs") { req, res ->
        val limit = (req.query["limit"]?.toIntOrNull() ?: 100).coerceIn(1, 1000)
        val offset = (req.query["offset"]?.toIntOrNull() ?: 0).coerceAtLeast(0)
        val allLogs = consoleRepo.logs
        val slice = allLogs.drop(offset).take(limit)
        val arr = JSONArray()
        slice.forEach { entry ->
            arr.put(JSONObject().apply {
                put("level", entry.level)
                put("message", entry.message)
                put("timestamp", entry.timestamp)
            })
        }
        val data = JSONObject().apply {
            put("logs", arr)
            put("total", allLogs.size)
            put("offset", offset)
            put("limit", limit)
        }
        res.sendJson(data)
    }

    // GET /api/logs/stream  - Server-Sent Events stream (push-based via Flow + Channel bridge)
    server.get("/api/logs/stream") { _, res ->
        coroutineScope {
            var collectJob: Job? = null

            sseLoop(
                res = res,
                setup = { channel ->
                    collectJob = launch {
                        consoleRepo.events.collect { event ->
                            val pair = when (event) {
                                is LogEvent.NewEntry -> {
                                    val json = JSONObject().apply {
                                        put("level", event.entry.level)
                                        put("message", event.entry.message)
                                        put("timestamp", event.entry.timestamp)
                                    }
                                    "log" to json.toString()
                                }
                                is LogEvent.Cleared -> "clear" to "{}"
                            }
                            channel.trySend(pair)
                        }
                    }
                },
                cleanup = { channel ->
                    collectJob?.cancel()
                    channel.close()
                },
            )
        }
    }

    // DELETE /api/logs
    server.delete("/api/logs") { _, res ->
        consoleRepo.clear()
        res.sendJson(mapOf("cleared" to true))
    }
}
