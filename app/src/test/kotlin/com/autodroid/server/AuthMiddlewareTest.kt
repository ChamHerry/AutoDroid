package com.autodroid.server

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class AuthMiddlewareTest {

    private lateinit var middleware: AuthMiddleware

    @BeforeEach
    fun setUp() {
        middleware = AuthMiddleware(mapOf(
            TokenScope.FULL to "full-secret-token",
            TokenScope.READ to "read-secret-token",
        ))
    }

    private fun makeRequest(
        method: String = "GET",
        path: String,
        auth: String? = null,
        query: Map<String, String> = emptyMap(),
    ): Request {
        val headers = mutableMapOf<String, String>()
        if (auth != null) headers["authorization"] = auth
        return Request(method = method, path = path, headers = headers, query = query, params = emptyMap(), body = "")
    }

    private fun makeResponse(): Response = Response(ByteArrayOutputStream())

    @Test
    fun `static file requests bypass auth`() = runBlocking {
        val req = makeRequest(path = "/index.html")
        val res = makeResponse()
        var nextCalled = false
        middleware.handle(req, res) { nextCalled = true }
        assertTrue(nextCalled)
    }

    @Test
    fun `status endpoint bypasses auth`() = runBlocking {
        val req = makeRequest(path = "/api/status")
        val res = makeResponse()
        var nextCalled = false
        middleware.handle(req, res) { nextCalled = true }
        assertTrue(nextCalled)
    }

    @Test
    fun `valid full bearer token passes auth`() = runBlocking {
        val req = makeRequest(path = "/api/shell/exec", auth = "Bearer full-secret-token")
        val res = makeResponse()
        var nextCalled = false
        middleware.handle(req, res) { nextCalled = true }
        assertTrue(nextCalled)
    }

    @Test
    fun `valid query token passes auth`() = runBlocking {
        val req = makeRequest(
            path = "/api/ui/dump",
            query = mapOf("token" to "full-secret-token"),
        )
        val res = makeResponse()
        var nextCalled = false
        middleware.handle(req, res) { nextCalled = true }
        assertTrue(nextCalled)
    }

    @Test
    fun `missing token returns 401`() = runBlocking {
        val req = makeRequest(path = "/api/shell/exec")
        val res = makeResponse()
        var nextCalled = false
        middleware.handle(req, res) { nextCalled = true }
        assertFalse(nextCalled)
        assertEquals(401, res.statusCode)
    }

    @Test
    fun `wrong token returns 403`() = runBlocking {
        val req = makeRequest(path = "/api/shell/exec", auth = "Bearer wrong-token")
        val res = makeResponse()
        var nextCalled = false
        middleware.handle(req, res) { nextCalled = true }
        assertFalse(nextCalled)
        assertEquals(403, res.statusCode)
    }

    @Test
    fun `empty token rejects api requests`() = runBlocking {
        val openMiddleware = AuthMiddleware("")
        val req = makeRequest(path = "/api/shell/exec")
        val res = makeResponse()
        var nextCalled = false
        openMiddleware.handle(req, res) { nextCalled = true }
        assertFalse(nextCalled)
        assertEquals(401, res.statusCode)
    }

    // ── Scope tests ──

    @Test
    fun `read token allows GET requests`() = runBlocking {
        val req = makeRequest(path = "/api/device/info", auth = "Bearer read-secret-token")
        val res = makeResponse()
        var nextCalled = false
        middleware.handle(req, res) { nextCalled = true }
        assertTrue(nextCalled)
    }

    @Test
    fun `read token blocks POST requests`() = runBlocking {
        val req = makeRequest(method = "POST", path = "/api/shell/exec", auth = "Bearer read-secret-token")
        val res = makeResponse()
        var nextCalled = false
        middleware.handle(req, res) { nextCalled = true }
        assertFalse(nextCalled)
        assertEquals(403, res.statusCode)
    }

    @Test
    fun `read token blocks DELETE requests`() = runBlocking {
        val req = makeRequest(method = "DELETE", path = "/api/files", auth = "Bearer read-secret-token")
        val res = makeResponse()
        var nextCalled = false
        middleware.handle(req, res) { nextCalled = true }
        assertFalse(nextCalled)
        assertEquals(403, res.statusCode)
    }

    @Test
    fun `full token allows POST requests`() = runBlocking {
        val req = makeRequest(method = "POST", path = "/api/shell/exec", auth = "Bearer full-secret-token")
        val res = makeResponse()
        var nextCalled = false
        middleware.handle(req, res) { nextCalled = true }
        assertTrue(nextCalled)
    }
}
