package com.autodroid.server.controller

import com.autodroid.adapter.AutomatorAdapter
import com.autodroid.server.HttpServer
import com.autodroid.server.fakeRequest
import com.autodroid.server.fakeResponse
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StatusControllerTest {

    @Test
    fun `status returns version and uptime`() = runTest {
        val automator = mockk<AutomatorAdapter>(relaxed = true)
        every { automator.isServiceConnected } returns true

        val server = HttpServer(port = 0)
        registerStatusRoutes(server, automator)

        val req = fakeRequest(path = "/api/status")
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("\"success\":true"))
        assertTrue(body.contains("\"version\":\"1.0.0\""))
        assertTrue(body.contains("\"status\":\"running\""))
        assertTrue(body.contains("\"accessibilityServiceConnected\":true"))
    }
}
