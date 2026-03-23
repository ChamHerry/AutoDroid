package com.autodroid.server.controller

import com.autodroid.adapter.AppAdapter
import com.autodroid.server.ApiException
import com.autodroid.server.HttpServer

fun registerAppRoutes(server: HttpServer, app: AppAdapter) {

    // GET /api/app/current - Get the foreground app package name
    server.get("/api/app/current") { _, res ->
        val packageName = app.getCurrentPackage()
        res.sendJson(mapOf("packageName" to packageName))
    }

    // POST /api/app/launch  { "packageName": "com.example.app" }
    server.post("/api/app/launch") { req, res ->
        val packageName = req.jsonBody.getString("packageName")
        app.launchApp(packageName)
        res.sendJson(mapOf(
            "launched" to true,
            "packageName" to packageName,
        ))
    }

    // GET /api/app/info?packageName=com.example.app
    server.get("/api/app/info") { req, res ->
        val packageName = req.query["packageName"]
        if (packageName.isNullOrBlank()) {
            res.sendError(400, "packageName query parameter is required")
            return@get
        }
        res.sendJson(mapOf(
            "packageName" to packageName,
            "appName" to app.getAppName(packageName),
            "installed" to app.isAppInstalled(packageName),
        ))
    }

    // POST /api/app/openUrl  { "url": "https://example.com" }
    server.post("/api/app/openUrl") { req, res ->
        val url = req.jsonBody.getString("url")
        app.openUrl(url)
        res.sendJson(mapOf("opened" to true))
    }

    // GET /api/app/clipboard
    server.get("/api/app/clipboard") { _, res ->
        res.sendJson(mapOf("text" to app.getClipboard()))
    }

    // POST /api/app/clipboard  { "text": "hello" }
    server.post("/api/app/clipboard") { req, res ->
        val text = req.jsonBody.getString("text")
        app.setClipboard(text)
        res.sendJson(mapOf("set" to true))
    }
}
