package com.autodroid.server.controller

import com.autodroid.adapter.DeviceAdapter
import com.autodroid.server.HttpServer

fun registerScreenshotRoutes(server: HttpServer, device: DeviceAdapter) {

    // GET /api/screenshot?quality=80&scale=1.0
    server.get("/api/screenshot") { req, res ->
        if (!device.isScreenshotAvailable()) {
            res.sendError(503, "Accessibility service not connected or Android < 11")
            return@get
        }
        val quality = (req.query["quality"]?.toIntOrNull() ?: 80).coerceIn(1, 100)
        val scale = (req.query["scale"]?.toFloatOrNull() ?: 1.0f).coerceIn(0.1f, 1.0f)
        val bytes = device.takeScreenshot(quality, scale)
        res.sendBytes(200, "image/jpeg", bytes)
    }
}
