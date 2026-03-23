package com.autodroid.server

import android.util.Log
import org.json.JSONObject

fun interface Middleware {
    suspend fun handle(request: Request, response: Response, next: suspend () -> Unit)
}

/** Callback for appending to the in-memory console log. */
fun interface ConsoleLog {
    fun append(level: String, message: String)
}

class CorsMiddleware(
    private val allowedOrigins: Set<String>? = null,
) : Middleware {
    override suspend fun handle(request: Request, response: Response, next: suspend () -> Unit) {
        val origin = request.headers["origin"]
        if (origin != null && isAllowedOrigin(origin)) {
            response.header("Access-Control-Allow-Origin", origin)
            response.header("Vary", "Origin")
            response.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
            response.header("Access-Control-Allow-Headers", "Content-Type, Authorization")
            response.header("Access-Control-Max-Age", "86400")
        }

        if (request.method == "OPTIONS") {
            response.sendRaw(204, "text/plain", "")
            return
        }

        next()
    }

    private fun isAllowedOrigin(origin: String): Boolean {
        // If explicit origins are configured, use exact match
        if (allowedOrigins != null) {
            return origin in allowedOrigins
        }
        // Default: allow localhost and private IP ranges
        val host = try { java.net.URI(origin).host ?: "" } catch (_: Exception) { "" }
        return host == "localhost" || host == "127.0.0.1" ||
            isPrivateIp(host)
    }

    private fun isPrivateIp(host: String): Boolean {
        val parts = host.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        val (a, b) = parts[0] to parts[1]
        return a == 10 ||                          // 10.0.0.0/8
            (a == 172 && b in 16..31) ||            // 172.16.0.0/12
            (a == 192 && b == 168) ||               // 192.168.0.0/16
            (a == 169 && b == 254)                  // 169.254.0.0/16 (link-local)
    }
}

class LoggerMiddleware(
    private val tag: String = "HttpServer",
    private val consoleLog: ConsoleLog? = null,
) : Middleware {
    override suspend fun handle(request: Request, response: Response, next: suspend () -> Unit) {
        val start = System.currentTimeMillis()
        val safePath = sanitizePath(request.path)
        val reqLine = "${request.method} $safePath"
        Log.d(tag, "--> $reqLine")
        consoleLog?.append("info", "--> $reqLine")

        next()

        val duration = System.currentTimeMillis() - start
        val resLine = "${request.method} $safePath ${response.statusCode} (${duration}ms)"
        Log.d(tag, "<-- $resLine")
        consoleLog?.append("info", "<-- $resLine")

        // Structured log entry for machine consumption
        val structured = JSONObject().apply {
            put("method", request.method)
            put("path", safePath)
            put("status", response.statusCode)
            put("duration_ms", duration)
            put("remoteIp", request.remoteIp)
        }
        Log.i(tag, structured.toString())
    }

    private fun sanitizePath(path: String): String =
        path.replace(Regex("""([?&])token=[^&]*"""), "$1token=***")
}
