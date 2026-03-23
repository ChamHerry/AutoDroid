package com.autodroid.server.controller

import com.autodroid.adapter.AppAdapter
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

class AppControllerTest {

    private lateinit var app: AppAdapter
    private lateinit var server: HttpServer

    @BeforeEach
    fun setUp() {
        app = mockk()
        server = HttpServer(port = 0)
        registerAppRoutes(server, app)
    }

    @Test
    fun `current app returns package name`() = runTest {
        every { app.getCurrentPackage() } returns "com.example.test"
        val req = fakeRequest(path = "/api/app/current")
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("\"success\":true"))
        assertTrue(body.contains("com.example.test"))
    }

    @Test
    fun `launch app succeeds`() = runTest {
        coEvery { app.launchApp("com.example.app") } returns Unit
        val req = fakeRequest(
            method = "POST", path = "/api/app/launch",
            body = """{"packageName":"com.example.app"}""",
        )
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("\"launched\":true"))
    }

    @Test
    fun `launch app with invalid package returns error`() = runTest {
        coEvery { app.launchApp("bad.pkg") } throws IllegalArgumentException("Package not found: bad.pkg")
        val req = fakeRequest(
            method = "POST", path = "/api/app/launch",
            body = """{"packageName":"bad.pkg"}""",
        )
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("\"success\":false"))
        assertTrue(body.contains("Package not found"))
    }

    @Test
    fun `app info returns name and installed status`() = runTest {
        every { app.getAppName("com.example.app") } returns "Example App"
        every { app.isAppInstalled("com.example.app") } returns true
        val req = fakeRequest(
            path = "/api/app/info",
            query = mapOf("packageName" to "com.example.app"),
        )
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("Example App"))
        assertTrue(body.contains("\"installed\":true"))
    }

    @Test
    fun `app info missing packageName returns 400`() = runTest {
        val req = fakeRequest(path = "/api/app/info")
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("\"success\":false"))
        assertTrue(body.contains("packageName"))
    }

    @Test
    fun `get clipboard returns text`() = runTest {
        coEvery { app.getClipboard() } returns "copied text"
        val req = fakeRequest(path = "/api/app/clipboard")
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("copied text"))
    }

    @Test
    fun `set clipboard succeeds`() = runTest {
        coEvery { app.setClipboard("hello") } returns Unit
        val req = fakeRequest(
            method = "POST", path = "/api/app/clipboard",
            body = """{"text":"hello"}""",
        )
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("\"set\":true"))
    }
}
