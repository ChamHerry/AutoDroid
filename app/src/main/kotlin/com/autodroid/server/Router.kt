package com.autodroid.server

import com.autodroid.adapter.FileAdapterException

/** Thrown by handlers to signal a specific HTTP error code. */
class ApiException(
    val statusCode: Int,
    message: String,
    val errorCode: String? = null,
) : Exception(message) {
    /** Common machine-readable error codes. */
    companion object ErrorCodes {
        const val ELEMENT_NOT_FOUND = "ELEMENT_NOT_FOUND"
        const val SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE"
        const val TIMEOUT = "TIMEOUT"
        const val INVALID_SELECTOR = "INVALID_SELECTOR"
        const val AUTH_REQUIRED = "AUTH_REQUIRED"
        const val RATE_LIMITED = "RATE_LIMITED"
        const val FILE_NOT_FOUND = "FILE_NOT_FOUND"
        const val PATH_DENIED = "PATH_DENIED"
        const val NOT_A_DIRECTORY = "NOT_A_DIRECTORY"
        const val NOT_A_FILE = "NOT_A_FILE"
        const val FILE_TOO_LARGE = "FILE_TOO_LARGE"
    }
}

data class Route(
    val method: String,
    val pathPattern: Regex,
    val paramNames: List<String>,
    val handler: suspend (Request, Response) -> Unit,
)

class Router {
    private val routes = mutableListOf<Route>()
    private var frozenRoutes: List<Route>? = null

    fun get(path: String, handler: suspend (Request, Response) -> Unit) {
        addRoute("GET", path, handler)
    }

    fun post(path: String, handler: suspend (Request, Response) -> Unit) {
        addRoute("POST", path, handler)
    }

    fun delete(path: String, handler: suspend (Request, Response) -> Unit) {
        addRoute("DELETE", path, handler)
    }

    fun put(path: String, handler: suspend (Request, Response) -> Unit) {
        addRoute("PUT", path, handler)
    }

    /** Freeze routes into an immutable snapshot for thread-safe request handling. */
    fun freeze() {
        frozenRoutes = routes.toList()
    }

    private fun addRoute(method: String, path: String, handler: suspend (Request, Response) -> Unit) {
        val paramNames = mutableListOf<String>()
        val regexPattern = path.split("/").joinToString("/") { segment ->
            if (segment.startsWith(":")) {
                paramNames.add(segment.removePrefix(":"))
                "([^/]+)"
            } else {
                Regex.escape(segment)
            }
        }
        routes.add(Route(method, Regex("^$regexPattern$"), paramNames, handler))
    }

    suspend fun handle(request: Request, response: Response): Boolean {
        val activeRoutes = frozenRoutes ?: routes
        for (route in activeRoutes) {
            if (route.method != request.method) continue
            val match = route.pathPattern.matchEntire(request.path) ?: continue

            val params = mutableMapOf<String, String>()
            route.paramNames.forEachIndexed { index, name ->
                params[name] = match.groupValues[index + 1]
            }

            val requestWithParams = request.copy(params = params)
            try {
                route.handler(requestWithParams, response)
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                // TimeoutCancellationException is a CancellationException subclass;
                // catch it first to map withTimeout failures to 504 instead of
                // letting them propagate and tear down the connection.
                if (!response.isSent) {
                    response.sendError(504, e.message ?: "Gateway Timeout", ApiException.TIMEOUT)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!response.isSent) {
                    val (code, errorCode) = when (e) {
                        is ApiException -> e.statusCode to e.errorCode
                        is FileAdapterException -> mapFileAdapterException(e)
                        is org.json.JSONException -> 400 to null
                        is IllegalArgumentException -> 400 to null
                        else -> 500 to null
                    }
                    // For 500 errors, don't leak internal details to client
                    val message = if (code == 500) "Internal Server Error" else (e.message ?: "Error")
                    response.sendError(code, message, errorCode)
                }
            }
            return true
        }
        return false
    }
}

/** Maps [FileAdapterException] subtypes to HTTP status code and machine-readable error code. */
private fun mapFileAdapterException(e: FileAdapterException): Pair<Int, String> = when (e) {
    is FileAdapterException.PathDeniedException -> 403 to ApiException.PATH_DENIED
    is FileAdapterException.FileNotFoundException -> 404 to ApiException.FILE_NOT_FOUND
    is FileAdapterException.NotADirectoryException -> 400 to ApiException.NOT_A_DIRECTORY
    is FileAdapterException.NotAFileException -> 400 to ApiException.NOT_A_FILE
    is FileAdapterException.FileTooLargeException -> 413 to ApiException.FILE_TOO_LARGE
}
