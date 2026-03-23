package com.autodroid.server.controller

import com.autodroid.adapter.EventAdapter
import com.autodroid.server.HttpServer
import com.autodroid.server.fakeRequest
import com.autodroid.server.fakeResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EventControllerTest {

    private lateinit var events: EventAdapter
    private lateinit var server: HttpServer
    private val listeners = mutableListOf<EventAdapter.EventListener>()

    @BeforeEach
    fun setUp() {
        listeners.clear()
        events = mockk(relaxUnitFun = true)
        every { events.addListener(any()) } answers { listeners.add(firstArg()) }
        every { events.removeListener(any()) } answers { listeners.remove(firstArg()) }
        server = HttpServer(port = 0)
        registerEventRoutes(server, events)
    }

    @Test
    fun `SSE sends connected event and registers listener`() = runTest {
        val req = fakeRequest(path = "/api/events/stream")
        val (res, out) = fakeResponse()

        val job = launch {
            server.routeRequest(req, res)
        }

        advanceTimeBy(100)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("event: connected"))
        assertTrue(body.contains("SSE stream opened"))
        assertEquals(1, listeners.size)

        job.cancel()
        advanceTimeBy(100)

        verify(atLeast = 1) { events.removeListener(any()) }
    }

    @Test
    fun `SSE forwards events from listener via channel`() = runTest {
        val req = fakeRequest(path = "/api/events/stream")
        val (res, out) = fakeResponse()

        val job = launch {
            server.routeRequest(req, res)
        }

        advanceTimeBy(100)
        assertEquals(1, listeners.size)

        // Simulate event from system
        listeners.first().onEvent("accessibility", """{"type":"click"}""")
        advanceTimeBy(100)

        val body = out.toString(Charsets.UTF_8)
        assertTrue(body.contains("event: accessibility"))
        assertTrue(body.contains("click"))

        job.cancel()
    }
}
