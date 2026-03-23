package com.autodroid.server

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.BindException
import java.net.SocketException
import kotlinx.coroutines.sync.Semaphore

class HttpServer(
    private val port: Int = 8080,
    private val maxConnections: Int = 64,
    private val bindAddress: String = "127.0.0.1",
) {
    private var serverSocket: ServerSocket? = null
    private val router = Router()
    private val middlewares = mutableListOf<Middleware>()
    @Volatile private var frozenMiddlewares: List<Middleware> = emptyList()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var fallback: (suspend (Request, Response) -> Unit)? = null
    private val connectionSemaphore = Semaphore(maxConnections)

    companion object {
        private const val TAG = "HttpServer"
        private const val MAX_BODY_SIZE = 1024 * 1024 // 1 MB
    }

    fun use(middleware: Middleware) {
        middlewares.add(middleware)
    }

    fun get(path: String, handler: suspend (Request, Response) -> Unit) {
        router.get(path, handler)
    }

    fun post(path: String, handler: suspend (Request, Response) -> Unit) {
        router.post(path, handler)
    }

    fun delete(path: String, handler: suspend (Request, Response) -> Unit) {
        router.delete(path, handler)
    }

    fun put(path: String, handler: suspend (Request, Response) -> Unit) {
        router.put(path, handler)
    }

    fun setFallbackHandler(handler: suspend (Request, Response) -> Unit) {
        fallback = handler
    }

    /** Dispatch a request through the router (for testing). */
    internal suspend fun routeRequest(request: Request, response: Response): Boolean {
        return router.handle(request, response)
    }

    fun start() {
        frozenMiddlewares = middlewares.toList()
        router.freeze()
        scope.launch {
            try {
                val addr = InetAddress.getByName(bindAddress)
                serverSocket = tryBindServerSocket(addr)
                Log.i(TAG, "Server started on $bindAddress:$port")
                while (isActive) {
                    try {
                        val socket = serverSocket!!.accept()
                        launch {
                            connectionSemaphore.acquire()
                            var semaphoreReleased = false
                            try {
                                handleConnection(socket) {
                                    if (!semaphoreReleased) {
                                        semaphoreReleased = true
                                        connectionSemaphore.release()
                                    }
                                }
                            } finally {
                                if (!semaphoreReleased) {
                                    connectionSemaphore.release()
                                }
                            }
                        }
                    } catch (_: SocketException) {
                        // Server socket closed during accept, normal shutdown
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error", e)
            }
        }
    }

    /**
     * Attempt to bind the server socket. Sets SO_REUSEADDR so the port can be
     * reclaimed quickly after a previous process crash (TIME_WAIT state).
     * If BindException still occurs (port held by another live process), it is
     * logged and propagated -- the outer catch in start() will handle it.
     */
    private fun tryBindServerSocket(addr: InetAddress): ServerSocket {
        return try {
            ServerSocket().apply {
                reuseAddress = true
                bind(java.net.InetSocketAddress(addr, port), 50)
            }
        } catch (e: BindException) {
            Log.w(TAG, "Port $port bind failed (address already in use)", e)
            throw e
        }
    }

    fun stop() {
        Log.i(TAG, "Server stopping")
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        scope.cancel()
    }

    private suspend fun handleConnection(socket: Socket, onSseStarted: () -> Unit = {}) {
        var response: Response? = null
        try {
            socket.soTimeout = 30_000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val outputStream = java.io.BufferedOutputStream(socket.getOutputStream(), 8192)
            response = Response(outputStream, socket)
            // Release semaphore early when SSE starts (before handler blocks)
            response.onSseStarted = onSseStarted

            val remoteIp = socket.inetAddress?.hostAddress ?: "unknown"
            val request = parseRequest(reader, remoteIp)
            if (request == null) {
                response.sendError(400, "Bad Request")
                return
            }

            // Run middleware pipeline then route
            runMiddlewarePipeline(request, response, frozenMiddlewares, 0) {
                val handled = router.handle(request, response)
                if (!handled && !response.isSent) {
                    val fb = fallback
                    if (fb != null) {
                        fb(request, response)
                    }
                    if (!response.isSent) {
                        response.sendError(404, "Not Found: ${request.method} ${request.path}")
                    }
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "Connection error", e)
            try {
                if (response != null && !response.isSent) {
                    val code = if (e is ApiException) e.statusCode else 500
                    val msg = if (e is ApiException) e.message ?: "Error" else "Internal Server Error"
                    response.sendError(code, msg)
                }
            } catch (_: Exception) {
                // Socket may already be closed, ignore
            }
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private suspend fun runMiddlewarePipeline(
        request: Request,
        response: Response,
        middlewares: List<Middleware>,
        index: Int,
        final: suspend () -> Unit,
    ) {
        if (index >= middlewares.size) {
            final()
            return
        }
        middlewares[index].handle(request, response) {
            runMiddlewarePipeline(request, response, middlewares, index + 1, final)
        }
    }

    private fun parseRequest(reader: BufferedReader, remoteIp: String = "unknown"): Request? {
        // Read request line: GET /path?query HTTP/1.1
        val requestLine = reader.readLine() ?: return null
        val parts = requestLine.split(" ")
        if (parts.size < 3) return null

        val method = parts[0].uppercase()
        val rawUri = parts[1]

        // Split path and query string
        val queryIndex = rawUri.indexOf('?')
        val rawPath = if (queryIndex >= 0) rawUri.substring(0, queryIndex) else rawUri
        val path = normalizePath(decodePercent(rawPath))
        val queryString = if (queryIndex >= 0) rawUri.substring(queryIndex + 1) else ""
        val query = parseQueryString(queryString)

        // Read headers
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                val key = line.substring(0, colonIndex).trim().lowercase()
                val value = line.substring(colonIndex + 1).trim()
                headers[key] = value
            }
        }

        // Read body based on Content-Length
        val body = readBody(reader, headers)

        return Request(
            method = method,
            path = path,
            headers = headers,
            query = query,
            params = emptyMap(),
            body = body,
            remoteIp = remoteIp,
        )
    }

    private fun readBody(reader: BufferedReader, headers: Map<String, String>): String {
        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
        if (contentLength <= 0) return ""
        if (contentLength > MAX_BODY_SIZE) {
            throw ApiException(413, "Request body too large (max ${MAX_BODY_SIZE / 1024}KB)")
        }
        val length = contentLength
        val buffer = CharArray(length)
        var totalRead = 0
        while (totalRead < length) {
            val read = reader.read(buffer, totalRead, length - totalRead)
            if (read == -1) break
            totalRead += read
        }
        return String(buffer, 0, totalRead)
    }

    private fun parseQueryString(queryString: String): Map<String, String> {
        if (queryString.isBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        queryString.split("&").forEach { pair ->
            val eqIndex = pair.indexOf('=')
            if (eqIndex > 0) {
                val key = decodePercent(pair.substring(0, eqIndex))
                val value = decodePercent(pair.substring(eqIndex + 1))
                result[key] = value
            } else if (pair.isNotEmpty()) {
                result[decodePercent(pair)] = ""
            }
        }
        return result
    }

    private fun decodePercent(value: String): String {
        return try {
            java.net.URLDecoder.decode(value, "UTF-8")
        } catch (_: Exception) {
            value
        }
    }

    /** Normalize path: resolve . and .., collapse multiple slashes, ensure leading /. */
    private fun normalizePath(path: String): String {
        val segments = path.split("/").filter { it.isNotEmpty() && it != "." }
        val normalized = mutableListOf<String>()
        for (seg in segments) {
            if (seg == "..") {
                if (normalized.isNotEmpty()) normalized.removeAt(normalized.size - 1)
            } else {
                normalized.add(seg)
            }
        }
        return "/" + normalized.joinToString("/")
    }
}
