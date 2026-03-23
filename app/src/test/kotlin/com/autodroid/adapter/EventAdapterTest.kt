package com.autodroid.adapter

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import io.mockk.every
import io.mockk.mockk
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EventAdapterTest {

    private lateinit var adapter: EventAdapter

    @BeforeEach
    fun setUp() {
        adapter = EventAdapter()
    }

    // ── Listener management ──

    @Test
    fun `addListener registers a listener`() {
        val events = mutableListOf<String>()
        adapter.addListener { name, _ -> events.add(name) }

        val service = mockk<AccessibilityService>()
        val event = mockAccessibilityEvent(type = 1, pkg = "com.test")
        adapter.onAccessibilityEvent(service, event)

        assertEquals(1, events.size)
        assertEquals("accessibility", events[0])
    }

    @Test
    fun `removeListener stops receiving events`() {
        val events = mutableListOf<String>()
        val listener = EventAdapter.EventListener { name, _ -> events.add(name) }

        adapter.addListener(listener)
        adapter.removeListener(listener)

        val service = mockk<AccessibilityService>()
        val event = mockAccessibilityEvent(type = 1, pkg = "com.test")
        adapter.onAccessibilityEvent(service, event)

        assertTrue(events.isEmpty())
    }

    @Test
    fun `multiple listeners all receive events`() {
        val events1 = mutableListOf<String>()
        val events2 = mutableListOf<String>()

        adapter.addListener { name, _ -> events1.add(name) }
        adapter.addListener { name, _ -> events2.add(name) }

        val service = mockk<AccessibilityService>()
        val event = mockAccessibilityEvent(type = 1, pkg = "com.test")
        adapter.onAccessibilityEvent(service, event)

        assertEquals(1, events1.size)
        assertEquals(1, events2.size)
    }

    // ── Accessibility events ──

    @Test
    fun `onAccessibilityEvent sends correct JSON`() {
        var receivedData = ""
        adapter.addListener { _, data -> receivedData = data }

        val service = mockk<AccessibilityService>()
        val event = mockAccessibilityEvent(
            type = 32,
            pkg = "com.example.app",
            cls = "android.widget.Button",
            time = 1000L,
        )
        adapter.onAccessibilityEvent(service, event)

        val json = JSONObject(receivedData)
        assertEquals(32, json.getInt("type"))
        assertEquals("com.example.app", json.getString("packageName"))
        assertEquals("android.widget.Button", json.getString("className"))
        assertEquals(1000L, json.getLong("time"))
    }

    @Test
    fun `onAccessibilityEvent with no listeners is no-op`() {
        // Should not throw
        val service = mockk<AccessibilityService>()
        val event = mockAccessibilityEvent(type = 1, pkg = "com.test")
        adapter.onAccessibilityEvent(service, event)
    }

    // ── Key events ──

    @Test
    fun `onKeyEvent sends correct JSON`() {
        var receivedName = ""
        var receivedData = ""
        adapter.addListener { name, data ->
            receivedName = name
            receivedData = data
        }

        val keyEvent = mockk<KeyEvent> {
            every { keyCode } returns 24
            every { action } returns KeyEvent.ACTION_DOWN
            every { eventTime } returns 2000L
        }
        adapter.onKeyEvent(keyEvent)

        assertEquals("key", receivedName)
        val json = JSONObject(receivedData)
        assertEquals(24, json.getInt("keyCode"))
        assertEquals(KeyEvent.ACTION_DOWN, json.getInt("action"))
        assertEquals(2000L, json.getLong("time"))
    }

    @Test
    fun `onKeyEvent with no listeners is no-op`() {
        val keyEvent = mockk<KeyEvent> {
            every { keyCode } returns 24
            every { action } returns KeyEvent.ACTION_DOWN
            every { eventTime } returns 2000L
        }
        // Should not throw
        adapter.onKeyEvent(keyEvent)
    }

    // ── Helpers ──

    private fun mockAccessibilityEvent(
        type: Int,
        pkg: String,
        cls: String = "android.view.View",
        time: Long = System.currentTimeMillis(),
    ): AccessibilityEvent = mockk {
        every { eventType } returns type
        every { packageName } returns pkg
        every { className } returns cls
        every { text } returns mutableListOf<CharSequence>()
        every { eventTime } returns time
    }
}
