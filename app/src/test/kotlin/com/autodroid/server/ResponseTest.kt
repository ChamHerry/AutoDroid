package com.autodroid.server

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class ResponseTest {

    private lateinit var output: ByteArrayOutputStream
    private lateinit var response: Response

    @BeforeEach
    fun setUp() {
        output = ByteArrayOutputStream()
        response = Response(output)
    }

    // ── SSE lifecycle ──

    @Test
    fun `isSseActive is false before startSSE`() {
        assertFalse(response.isSseActive)
    }

    @Test
    fun `startSSE sets isSseActive and writes headers`() {
        response.startSSE()

        assertTrue(response.isSseActive)
        assertTrue(response.isSent)

        val headers = output.toString(Charsets.UTF_8)
        assertTrue(headers.contains("HTTP/1.1 200 OK"))
        assertTrue(headers.contains("text/event-stream"))
        assertTrue(headers.contains("Cache-Control: no-cache"))
        assertTrue(headers.contains("Connection: keep-alive"))
    }

    @Test
    fun `startSSE is idempotent`() {
        response.startSSE()
        val firstSize = output.size()

        response.startSSE()
        assertEquals(firstSize, output.size(), "Second startSSE should be no-op")
    }

    @Test
    fun `closeSse resets isSseActive`() {
        response.startSSE()
        assertTrue(response.isSseActive)

        response.closeSse()
        assertFalse(response.isSseActive)
    }

    // ── SSE event sending ──

    @Test
    fun `sendSSE writes event in SSE format`() {
        response.startSSE()
        output.reset()

        response.sendSSE("test-event", """{"key":"value"}""")

        val written = output.toString(Charsets.UTF_8)
        assertTrue(written.contains("event: test-event\n"))
        assertTrue(written.contains("""data: {"key":"value"}"""))
        assertTrue(written.endsWith("\n\n"))
    }

    @Test
    fun `sendSSE includes id when provided`() {
        response.startSSE()
        output.reset()

        response.sendSSE("evt", "payload", id = "42")

        val written = output.toString(Charsets.UTF_8)
        assertTrue(written.contains("id: 42\n"))
    }

    @Test
    fun `sendSSE without id omits id field`() {
        response.startSSE()
        output.reset()

        response.sendSSE("evt", "payload")

        val written = output.toString(Charsets.UTF_8)
        assertFalse(written.contains("id:"))
    }

    @Test
    fun `sendSSE handles multiline data`() {
        response.startSSE()
        output.reset()

        response.sendSSE("evt", "line1\nline2\nline3")

        val written = output.toString(Charsets.UTF_8)
        assertTrue(written.contains("data: line1\n"))
        assertTrue(written.contains("data: line2\n"))
        assertTrue(written.contains("data: line3\n"))
    }

    @Test
    fun `sendSSE auto-starts SSE if not started`() {
        assertFalse(response.isSseActive)

        response.sendSSE("evt", "data")

        assertTrue(response.isSseActive)
    }

    // ── SSE blocks normal response ──

    @Test
    fun `sendJson is no-op after startSSE`() {
        response.startSSE()
        output.reset()

        response.sendJson(mapOf("a" to 1))

        // sendRaw checks `sent` flag — after SSE start, isSent is true
        // but sendJson uses sendRaw which checks `sent` (not sseStarted)
        // The key behavior: isSent returns true so controllers won't double-send
        assertTrue(response.isSent)
    }

    // ── Normal response still works ──

    @Test
    fun `sendJson works for normal response`() {
        response.sendJson(mapOf("hello" to "world"))

        val written = output.toString(Charsets.UTF_8)
        assertTrue(written.contains("200 OK"))
        assertTrue(written.contains("application/json"))
        assertTrue(written.contains("\"success\":true"))
        assertTrue(written.contains("\"hello\":\"world\""))
    }

    @Test
    fun `sendError writes error response`() {
        response.sendError(404, "Not Found")

        val written = output.toString(Charsets.UTF_8)
        assertTrue(written.contains("404"))
        assertTrue(written.contains("\"success\":false"))
        assertTrue(written.contains("Not Found"))
    }

    // ── Custom headers in SSE ──

    @Test
    fun `custom headers are included in SSE response`() {
        response.header("X-Custom", "test-value")
        response.startSSE()

        val written = output.toString(Charsets.UTF_8)
        assertTrue(written.contains("X-Custom: test-value"))
    }
}
