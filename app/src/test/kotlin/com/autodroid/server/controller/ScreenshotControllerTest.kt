package com.autodroid.server.controller

import com.autodroid.adapter.DeviceAdapter
import com.autodroid.server.HttpServer
import com.autodroid.server.fakeRequest
import com.autodroid.server.fakeResponse
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ScreenshotControllerTest {

    private lateinit var device: DeviceAdapter
    private lateinit var server: HttpServer

    @BeforeEach
    fun setUp() {
        device = mockk()
        server = HttpServer(port = 0)
        registerScreenshotRoutes(server, device)
    }

    @Test
    fun `screenshot returns bytes when available`() = runTest {
        every { device.isScreenshotAvailable() } returns true
        coEvery { device.takeScreenshot(any(), any()) } returns byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        val req = fakeRequest(path = "/api/screenshot")
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val bytes = out.toByteArray()
        val header = String(bytes, Charsets.UTF_8).substringBefore("\r\n\r\n")
        assertTrue(header.contains("200 OK"))
        assertTrue(header.contains("image/jpeg"))
    }

    @Test
    fun `screenshot returns 503 when unavailable`() = runTest {
        every { device.isScreenshotAvailable() } returns false
        val req = fakeRequest(path = "/api/screenshot")
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("503"))
    }

    @Test
    fun `screenshot returns 500 on error`() = runTest {
        every { device.isScreenshotAvailable() } returns true
        coEvery { device.takeScreenshot(any(), any()) } throws RuntimeException("capture failed")
        val req = fakeRequest(path = "/api/screenshot")
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("\"success\":false"))
        assertTrue(body.contains("Internal Server Error"))
        assertFalse(body.contains("capture failed"))
    }
}
