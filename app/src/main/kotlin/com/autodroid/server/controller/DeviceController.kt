package com.autodroid.server.controller

import com.autodroid.adapter.DeviceAdapter
import com.autodroid.server.HttpServer

fun registerDeviceRoutes(server: HttpServer, device: DeviceAdapter) {

    server.get("/api/device/info") { _, res ->
        res.sendJson(device.getDeviceInfo())
    }

    server.get("/api/device/screen") { _, res ->
        res.sendJson(mapOf(
            "width" to device.getScreenWidth(),
            "height" to device.getScreenHeight(),
        ))
    }

    server.get("/api/device/battery") { _, res ->
        res.sendJson(mapOf("level" to device.getBatteryLevel()))
    }

    server.get("/api/device/brightness") { _, res ->
        res.sendJson(mapOf("brightness" to device.getBrightness()))
    }

    server.get("/api/device/screenOn") { _, res ->
        res.sendJson(mapOf("screenOn" to device.isScreenOn()))
    }
}
