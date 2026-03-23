package com.autodroid.server

import org.json.JSONObject
import java.io.OutputStream

class Response(private val outputStream: OutputStream, private val socket: java.net.Socket? = null) {
    var statusCode: Int = 200
    private val headers = mutableMapOf<String, String>()
    @Volatile private var sent = false
    @Volatile private var sseStarted = false
    private val writeLock = Any()

    /** Called when SSE starts — used by HttpServer to release connection semaphore early. */
    var onSseStarted: (() -> Unit)? = null

    fun header(key: String, value: String): Response {
        // Prevent header injection by stripping CR/LF characters
        headers[key.replace(Regex("[\r\n]"), "")] = value.replace(Regex("[\r\n]"), "")
        return this
    }

    fun sendJson(data: Any?) {
        val wrapped = when (data) {
            is Map<*, *> -> JSONObject(data.mapKeys { it.key.toString() })
            else -> data ?: JSONObject.NULL
        }
        val json = JSONObject().apply {
            put("success", true)
            put("data", wrapped)
            put("timestamp", System.currentTimeMillis())
        }
        sendRaw(200, "application/json", json.toString())
    }

    /** Send a pre-serialized JSON string as the data field, avoiding re-parse overhead. */
    fun sendRawJson(jsonString: String) {
        val envelope = """{"success":true,"data":$jsonString,"timestamp":${System.currentTimeMillis()}}"""
        sendRaw(200, "application/json", envelope)
    }

    fun sendError(code: Int, message: String, errorCode: String? = null) {
        val json = JSONObject().apply {
            put("success", false)
            put("error", message)
            if (errorCode != null) {
                put("errorCode", errorCode)
            }
            put("timestamp", System.currentTimeMillis())
        }
        sendRaw(code, "application/json", json.toString())
    }

    fun startSSE() {
        synchronized(writeLock) {
            if (sseStarted || sent) return
            sseStarted = true
            socket?.soTimeout = 0 // Disable timeout for SSE long-lived connections
            val header = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: text/event-stream; charset=utf-8\r\n")
                append("Cache-Control: no-cache\r\n")
                append("Connection: keep-alive\r\n")
                headers.forEach { (k, v) -> append("$k: $v\r\n") }
                append("\r\n")
            }
            outputStream.write(header.toByteArray(Charsets.UTF_8))
            outputStream.flush()
        }
        // Notify after releasing writeLock to avoid holding lock during callback
        onSseStarted?.invoke()
    }

    fun sendSSE(event: String, data: String, id: String? = null) {
        synchronized(writeLock) {
            if (!sseStarted) startSSE()
            val message = buildString {
                if (id != null) append("id: $id\n")
                append("event: $event\n")
                data.lines().forEach { line -> append("data: $line\n") }
                append("\n")
            }
            outputStream.write(message.toByteArray(Charsets.UTF_8))
            outputStream.flush()
        }
    }

    fun sendRaw(status: Int, contentType: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        sendBytes(status, "$contentType; charset=utf-8", bytes)
    }

    fun sendBytes(status: Int, contentType: String, bytes: ByteArray) {
        synchronized(writeLock) {
            if (sent || sseStarted) return
            sent = true
            statusCode = status
            val header = buildString {
                append("HTTP/1.1 $status ${statusText(status)}\r\n")
                append("Content-Type: $contentType\r\n")
                append("Content-Length: ${bytes.size}\r\n")
                headers.forEach { (k, v) -> append("$k: $v\r\n") }
                append("\r\n")
            }
            outputStream.write(header.toByteArray(Charsets.UTF_8))
            outputStream.write(bytes)
            outputStream.flush()
        }
    }

    val isSent: Boolean get() = sent || sseStarted
    val isSseActive: Boolean get() = sseStarted

    /** Send SSE comment line as keepalive. Throws if client disconnected. */
    fun sendHeartbeat() {
        synchronized(writeLock) {
            if (!sseStarted) return
            outputStream.write(": heartbeat\n\n".toByteArray(Charsets.UTF_8))
            outputStream.flush()
        }
    }

    fun closeSse() {
        synchronized(writeLock) {
            sseStarted = false
            sent = true // Mark as completed so isSent stays true after close
            try { outputStream.close() } catch (_: Exception) {}
        }
    }

    companion object {
        fun statusText(code: Int): String = when (code) {
            200 -> "OK"
            201 -> "Created"
            204 -> "No Content"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            408 -> "Request Timeout"
            413 -> "Payload Too Large"
            429 -> "Too Many Requests"
            500 -> "Internal Server Error"
            501 -> "Not Implemented"
            503 -> "Service Unavailable"
            504 -> "Gateway Timeout"
            else -> "Unknown"
        }
    }
}
