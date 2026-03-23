package com.autodroid.server.controller

import com.autodroid.adapter.AutomatorAdapter
import com.autodroid.server.HttpServer
import com.autodroid.server.fakeRequest
import com.autodroid.server.fakeResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ActionControllerTest {

    private lateinit var automator: AutomatorAdapter
    private lateinit var server: HttpServer

    @BeforeEach
    fun setUp() {
        automator = mockk()
        server = HttpServer(port = 0)
        registerActionRoutes(server, automator)
    }

    @Test
    fun `click returns result`() = runTest {
        coEvery { automator.clickPoint(100, 200) } returns true
        val req = fakeRequest(method = "POST", path = "/api/actions/click", body = """{"x":100,"y":200}""")
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("\"clicked\":true"))
    }

    @Test
    fun `click missing params returns error`() = runTest {
        val req = fakeRequest(method = "POST", path = "/api/actions/click", body = """{}""")
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("\"success\":false"))
    }

    @Test
    fun `swipe returns result`() = runTest {
        coEvery { automator.swipe(0, 0, 100, 100, 300L) } returns true
        val req = fakeRequest(
            method = "POST", path = "/api/actions/swipe",
            body = """{"x1":0,"y1":0,"x2":100,"y2":100,"duration":300}""",
        )
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("\"swiped\":true"))
    }

    @Test
    fun `key back returns result`() = runTest {
        coEvery { automator.back() } returns true
        val req = fakeRequest(method = "POST", path = "/api/actions/key", body = """{"action":"back"}""")
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("\"performed\":true"))
    }

    @Test
    fun `key unknown action returns error`() = runTest {
        val req = fakeRequest(method = "POST", path = "/api/actions/key", body = """{"action":"invalid"}""")
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("\"success\":false"))
        assertTrue(body.contains("Unknown key action"))
    }

    @Test
    fun `gesture returns result`() = runTest {
        coEvery { automator.gesture(0L, 300L, any()) } returns true
        val req = fakeRequest(
            method = "POST", path = "/api/actions/gesture",
            body = """{"delay":0,"duration":300,"points":[[0,0],[100,200]]}""",
        )
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("\"performed\":true"))
    }
}
