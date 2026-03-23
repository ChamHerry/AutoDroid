package com.autodroid.server.controller

import com.autodroid.adapter.AutomatorAdapter
import com.autodroid.server.HttpServer

fun registerStatusRoutes(server: HttpServer, automator: AutomatorAdapter) {

    val startTime = System.currentTimeMillis()

    server.get("/api/status") { _, res ->
        res.sendJson(mapOf(
            "version" to "1.0.0",
            "uptime" to (System.currentTimeMillis() - startTime),
            "status" to "running",
            "accessibilityServiceConnected" to automator.isServiceConnected,
        ))
    }
}
