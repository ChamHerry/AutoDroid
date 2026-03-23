package com.autodroid.server.controller

import com.autodroid.adapter.AutomatorAdapter
import com.autodroid.server.HttpServer
import com.autodroid.server.fakeRequest
import com.autodroid.server.fakeResponse
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UiControllerTest {

    private lateinit var automator: AutomatorAdapter
    private lateinit var server: HttpServer

    @BeforeEach
    fun setUp() {
        automator = mockk()
        server = HttpServer(port = 0)
        registerUiRoutes(server, automator)
    }

    @Test
    fun `dump returns tree`() = runTest {
        coEvery { automator.dumpUiTree() } returns """{"text":"root"}"""
        val req = fakeRequest(path = "/api/ui/dump")
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("\"success\":true"))
        assertTrue(body.contains("root"))
    }

    @Test
    fun `dump returns 503 when service unavailable`() = runTest {
        coEvery { automator.dumpUiTree() } returns null
        val req = fakeRequest(path = "/api/ui/dump")
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("503"))
    }

    @Test
    fun `click finds and clicks element`() = runTest {
        coEvery { automator.findOne(any(), any()) } returns 42
        coEvery { automator.click(42) } returns true
        every { automator.releaseNode(42) } returns Unit
        val req = fakeRequest(
            method = "POST", path = "/api/ui/click",
            body = """{"selector":{"text":"OK"},"timeout":1000}""",
        )
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("\"clicked\":true"))
        verify { automator.releaseNode(42) }
    }

    @Test
    fun `click returns 404 when element not found`() = runTest {
        coEvery { automator.findOne(any(), any()) } returns null
        val req = fakeRequest(
            method = "POST", path = "/api/ui/click",
            body = """{"selector":{"text":"Missing"}}""",
        )
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("404"))
        assertTrue(body.contains("not found"))
    }

    @Test
    fun `find returns results and releases handles`() = runTest {
        coEvery { automator.find(any(), any()) } returns listOf(1, 2)
        every { automator.getNodeInfo(1) } returns JSONObject().apply { put("text", "A") }
        every { automator.getNodeInfo(2) } returns JSONObject().apply { put("text", "B") }
        every { automator.releaseNode(any()) } returns Unit
        val req = fakeRequest(
            method = "POST", path = "/api/ui/find",
            body = """{"selector":{"className":"Button"},"max":5}""",
        )
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("\"success\":true"))
        verify { automator.releaseNode(1) }
        verify { automator.releaseNode(2) }
    }

    @Test
    fun `release endpoint releases handle`() = runTest {
        every { automator.releaseNode(99) } returns Unit
        val req = fakeRequest(method = "POST", path = "/api/ui/release", body = """{"handle":99}""")
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("\"released\":true"))
        verify { automator.releaseNode(99) }
    }

    @Test
    fun `releaseAll endpoint releases all handles`() = runTest {
        every { automator.releaseAllNodes() } returns Unit
        val req = fakeRequest(method = "POST", path = "/api/ui/releaseAll")
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("\"released\":true"))
        verify { automator.releaseAllNodes() }
    }
}
