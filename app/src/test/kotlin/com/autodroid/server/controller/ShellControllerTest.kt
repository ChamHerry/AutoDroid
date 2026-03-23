package com.autodroid.server.controller

import com.autodroid.adapter.ShellAdapter
import com.autodroid.adapter.ShellResult
import com.autodroid.server.HttpServer
import com.autodroid.server.fakeRequest
import com.autodroid.server.fakeResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ShellControllerTest {

    private lateinit var shell: ShellAdapter
    private lateinit var server: HttpServer

    @BeforeEach
    fun setUp() {
        shell = mockk()
        server = HttpServer(port = 0)
        registerShellRoutes(server, shell)
    }

    @Test
    fun `exec returns stdout and exit code`() = runTest {
        coEvery { shell.exec("ls") } returns ShellResult(0, "file.txt\n", "")
        val req = fakeRequest(method = "POST", path = "/api/shell/exec", body = """{"command":"ls"}""")
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("\"success\":true"))
        assertTrue(body.contains("file.txt"))
    }

    @Test
    fun `exec with root calls execRoot`() = runTest {
        coEvery { shell.execRoot("id") } returns ShellResult(0, "uid=0(root)\n", "")
        val req = fakeRequest(method = "POST", path = "/api/shell/exec", body = """{"command":"id","root":true}""")
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("uid=0(root)"))
    }

    @Test
    fun `exec missing command returns error`() = runTest {
        val req = fakeRequest(method = "POST", path = "/api/shell/exec", body = """{}""")
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("\"success\":false"))
    }

    @Test
    fun `root check returns result`() = runTest {
        coEvery { shell.isRootAvailable() } returns true
        val req = fakeRequest(method = "GET", path = "/api/shell/root")
        val (res, out) = fakeResponse()

        server.routeRequest(req, res)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("\"success\":true"))
        assertTrue(body.contains("rootAvailable"))
    }
}
