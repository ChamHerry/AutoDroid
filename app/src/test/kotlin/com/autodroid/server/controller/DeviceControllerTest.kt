package com.autodroid.server.controller

import com.autodroid.adapter.DeviceAdapter
import com.autodroid.server.HttpServer
import com.autodroid.server.fakeRequest
import com.autodroid.server.fakeResponse
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeviceControllerTest {

    private lateinit var device: DeviceAdapter
    private lateinit var server: HttpServer

    @BeforeEach
    fun setUp() {
        device = mockk()
        server = HttpServer(port = 0)
        registerDeviceRoutes(server, device)
    }

    @Test
    fun `device info returns device data`() = runTest {
        every { device.getDeviceInfo() } returns mapOf(
            "brand" to "TestBrand",
            "model" to "TestModel",
            "sdkVersion" to 35,
        )
        val req = fakeRequest(path = "/api/device/info")
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("\"success\":true"))
        assertTrue(body.contains("TestBrand"))
        assertTrue(body.contains("TestModel"))
    }

    @Test
    fun `screen returns dimensions`() = runTest {
        every { device.getScreenWidth() } returns 1080
        every { device.getScreenHeight() } returns 2400
        val req = fakeRequest(path = "/api/device/screen")
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("1080"))
        assertTrue(body.contains("2400"))
    }

    @Test
    fun `battery returns level`() = runTest {
        every { device.getBatteryLevel() } returns 85
        val req = fakeRequest(path = "/api/device/battery")
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("85"))
    }

    @Test
    fun `screenOn returns state`() = runTest {
        every { device.isScreenOn() } returns true
        val req = fakeRequest(path = "/api/device/screenOn")
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("\"screenOn\":true"))
    }

    @Test
    fun `adapter exception returns 500`() = runTest {
        every { device.getDeviceInfo() } throws RuntimeException("Device error")
        val req = fakeRequest(path = "/api/device/info")
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("\"success\":false"))
        assertTrue(body.contains("Internal Server Error"))
        assertFalse(body.contains("Device error"))
    }
}
