package com.autodroid.server.controller

import com.autodroid.log.ConsoleRepository
import com.autodroid.server.HttpServer
import com.autodroid.server.fakeRequest
import com.autodroid.server.fakeResponse
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LogControllerTest {

    private lateinit var server: HttpServer

    @BeforeEach
    fun setUp() {
        ConsoleRepository.clear()
        server = HttpServer(port = 0)
        registerLogRoutes(server, ConsoleRepository)
    }

    @Test
    fun `get logs returns empty list initially`() = runTest {
        val req = fakeRequest(path = "/api/logs")
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("\"success\":true"))
        assertTrue(body.contains("\"total\":0"))
    }

    @Test
    fun `get logs returns appended entries`() = runTest {
        ConsoleRepository.append("info", "test message 1")
        ConsoleRepository.append("error", "test message 2")

        val req = fakeRequest(path = "/api/logs")
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("\"total\":2"))
        assertTrue(body.contains("test message 1"))
        assertTrue(body.contains("test message 2"))
    }

    @Test
    fun `get logs respects limit and offset`() = runTest {
        repeat(5) { ConsoleRepository.append("info", "msg$it") }

        val req = fakeRequest(path = "/api/logs", query = mapOf("limit" to "2", "offset" to "1"))
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("\"total\":5"))
        assertTrue(body.contains("msg1"))
        assertTrue(body.contains("msg2"))
        assertFalse(body.contains("msg0"))
        assertFalse(body.contains("msg3"))
    }

    @Test
    fun `delete logs clears all entries`() = runTest {
        ConsoleRepository.append("info", "to be cleared")

        val req = fakeRequest(method = "DELETE", path = "/api/logs")
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("\"cleared\":true"))
        assertEquals(0, ConsoleRepository.logs.size)
    }
}
