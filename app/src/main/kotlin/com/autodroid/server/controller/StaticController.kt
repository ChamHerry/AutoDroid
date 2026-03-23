package com.autodroid.server.controller

import android.content.Context
import com.autodroid.server.HttpServer

private val MIME_TYPES = mapOf(
    "html" to "text/html",
    "css" to "text/css",
    "js" to "application/javascript",
    "json" to "application/json",
    "png" to "image/png",
    "jpg" to "image/jpeg",
    "jpeg" to "image/jpeg",
    "svg" to "image/svg+xml",
    "ico" to "image/x-icon",
    "woff" to "font/woff",
    "woff2" to "font/woff2",
    "ttf" to "font/ttf",
)

// Serves static files from assets/web/.
// GET /  -> assets/web/index.html
// GET /assets/xxx.js -> assets/web/assets/xxx.js
// Registered as fallback handler so API routes take priority.
fun registerStaticRoutes(server: HttpServer, context: Context) {
    server.setFallbackHandler { req, res ->
        if (req.method != "GET" || res.isSent) return@setFallbackHandler

        val path = req.path.trimStart('/')
        // Defense in depth: reject path traversal (HttpServer already normalizes, but guard here too)
        if (path.contains("..")) {
            res.sendError(403, "Path traversal not allowed")
            return@setFallbackHandler
        }
        val assetPath = when {
            path.isEmpty() || path == "/" -> "web/index.html"
            else -> "web/$path"
        }

        try {
            val bytes = context.assets.open(assetPath).use { it.readBytes() }
            val ext = assetPath.substringAfterLast('.', "")
            val mime = MIME_TYPES[ext] ?: "application/octet-stream"
            res.sendBytes(200, mime, bytes)
        } catch (_: Exception) {
            // SPA fallback: serve index.html for unmatched paths (client-side routing)
            try {
                val bytes = context.assets.open("web/index.html").use { it.readBytes() }
                res.sendBytes(200, "text/html", bytes)
            } catch (_: Exception) {
                res.sendError(404, "Not found: ${req.path}")
            }
        }
    }
}
