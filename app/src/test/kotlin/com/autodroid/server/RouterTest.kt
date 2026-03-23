package com.autodroid.server

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class RouterTest {

    private lateinit var router: Router

    @BeforeEach
    fun setUp() {
        router = Router()
    }

    private fun fakeRequest(
        method: String = "GET",
        path: String = "/",
    ) = Request(method = method, path = path, headers = emptyMap(), query = emptyMap(), params = emptyMap(), body = "")

    private fun fakeResponse() = Response(ByteArrayOutputStream())

    // ── Basic routing ──

    @Test
    fun `GET route matches GET request`() = runTest {
        var called = false
        router.get("/api/status") { _, _ -> called = true }

        val handled = router.handle(fakeRequest("GET", "/api/status"), fakeResponse())

        assertTrue(handled)
        assertTrue(called)
    }

    @Test
    fun `POST route matches POST request`() = runTest {
        var called = false
        router.post("/api/actions/click") { _, _ -> called = true }

        val handled = router.handle(fakeRequest("POST", "/api/actions/click"), fakeResponse())

        assertTrue(handled)
        assertTrue(called)
    }

    @Test
    fun `returns false when no route matches`() = runTest {
        router.get("/api/status") { _, _ -> }

        val handled = router.handle(fakeRequest("GET", "/api/nonexistent"), fakeResponse())

        assertFalse(handled)
    }

    @Test
    fun `method mismatch does not match`() = runTest {
        router.get("/api/status") { _, _ -> }

        val handled = router.handle(fakeRequest("POST", "/api/status"), fakeResponse())

        assertFalse(handled)
    }

    // ── Path parameters ──

    @Test
    fun `extracts named path parameters`() = runTest {
        var extractedId = ""
        router.get("/api/users/:id") { req, _ ->
            extractedId = req.params["id"] ?: ""
        }

        router.handle(fakeRequest("GET", "/api/users/42"), fakeResponse())

        assertEquals("42", extractedId)
    }

    @Test
    fun `extracts multiple path parameters`() = runTest {
        var userId = ""
        var postId = ""
        router.get("/api/users/:userId/posts/:postId") { req, _ ->
            userId = req.params["userId"] ?: ""
            postId = req.params["postId"] ?: ""
        }

        router.handle(fakeRequest("GET", "/api/users/5/posts/99"), fakeResponse())

        assertEquals("5", userId)
        assertEquals("99", postId)
    }

    // ── PUT / DELETE ──

    @Test
    fun `PUT route matches`() = runTest {
        var called = false
        router.put("/api/items/:id") { _, _ -> called = true }

        val handled = router.handle(fakeRequest("PUT", "/api/items/1"), fakeResponse())

        assertTrue(handled)
        assertTrue(called)
    }

    @Test
    fun `DELETE route matches`() = runTest {
        var called = false
        router.delete("/api/items/:id") { _, _ -> called = true }

        val handled = router.handle(fakeRequest("DELETE", "/api/items/1"), fakeResponse())

        assertTrue(handled)
        assertTrue(called)
    }

    // ── Priority ──

    @Test
    fun `first matching route wins`() = runTest {
        var which = 0
        router.get("/api/test") { _, _ -> which = 1 }
        router.get("/api/test") { _, _ -> which = 2 }

        router.handle(fakeRequest("GET", "/api/test"), fakeResponse())

        assertEquals(1, which)
    }

    // ── Edge cases ──

    @Test
    fun `root path matches`() = runTest {
        var called = false
        router.get("/") { _, _ -> called = true }

        val handled = router.handle(fakeRequest("GET", "/"), fakeResponse())

        assertTrue(handled)
        assertTrue(called)
    }

    @Test
    fun `partial path does not match`() = runTest {
        router.get("/api") { _, _ -> }

        val handled = router.handle(fakeRequest("GET", "/api/extra"), fakeResponse())

        assertFalse(handled)
    }

    // ── Global error handling ──

    @Test
    fun `ApiException returns custom status code`() = runTest {
        router.post("/api/test") { _, _ ->
            throw ApiException(403, "Forbidden action")
        }
        val out = ByteArrayOutputStream()
        val res = Response(out)

        router.handle(fakeRequest("POST", "/api/test"), res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("403"))
        assertTrue(body.contains("Forbidden action"))
    }

    @Test
    fun `JSONException returns 400`() = runTest {
        router.post("/api/test") { _, _ ->
            throw org.json.JSONException("Missing key")
        }
        val out = ByteArrayOutputStream()
        val res = Response(out)

        router.handle(fakeRequest("POST", "/api/test"), res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("400"))
        assertTrue(body.contains("Missing key"))
    }

    @Test
    fun `IllegalArgumentException returns 400`() = runTest {
        router.post("/api/test") { _, _ ->
            throw IllegalArgumentException("Bad input")
        }
        val out = ByteArrayOutputStream()
        val res = Response(out)

        router.handle(fakeRequest("POST", "/api/test"), res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("400"))
        assertTrue(body.contains("Bad input"))
    }

    @Test
    fun `unknown exception returns 500 without leaking details`() = runTest {
        router.get("/api/test") { _, _ ->
            throw RuntimeException("secret internal path /data/db.sqlite")
        }
        val out = ByteArrayOutputStream()
        val res = Response(out)

        router.handle(fakeRequest("GET", "/api/test"), res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("500"))
        assertTrue(body.contains("Internal Server Error"))
        assertFalse(body.contains("secret internal path"))
    }

    @Test
    fun `error handler does not send if response already sent`() = runTest {
        router.get("/api/test") { _, res ->
            res.sendJson(mapOf("ok" to true))
            throw RuntimeException("after send")
        }
        val out = ByteArrayOutputStream()
        val res = Response(out)

        router.handle(fakeRequest("GET", "/api/test"), res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("\"success\":true"))
        assertFalse(body.contains("Internal Server Error"))
    }
}
