package com.autodroid.server.controller

import com.autodroid.adapter.EventAdapter
import com.autodroid.server.HttpServer

fun registerEventRoutes(server: HttpServer, events: EventAdapter) {

    // GET /api/events/stream  — SSE stream of system events
    server.get("/api/events/stream") { _, res ->
        res.sendSSE("connected", """{"message":"SSE stream opened"}""")

        var listener: EventAdapter.EventListener? = null

        sseLoop(
            res = res,
            setup = { channel ->
                listener = EventAdapter.EventListener { eventName, jsonData ->
                    // Non-blocking: drop event if channel full rather than blocking AccessibilityService thread
                    channel.trySend(eventName to jsonData)
                }
                events.addListener(listener!!)
            },
            cleanup = { _ ->
                listener?.let { events.removeListener(it) }
            },
        )
    }
}
