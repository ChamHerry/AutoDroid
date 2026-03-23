package com.autodroid.adapter

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.autodroid.service.AccessibilityDelegate
import org.json.JSONArray
import org.json.JSONObject
import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges Android AccessibilityEvents and KeyEvents.
 * Listeners subscribe to events (used by HTTP SSE endpoint).
 */
@Singleton
class EventAdapter @Inject constructor() : AccessibilityDelegate {

    fun interface EventListener {
        fun onEvent(eventName: String, jsonData: String)
    }

    private val listeners = CopyOnWriteArrayList<EventListener>()

    fun addListener(listener: EventListener) { listeners.add(listener) }
    fun removeListener(listener: EventListener) { listeners.remove(listener) }

    override fun onAccessibilityEvent(service: AccessibilityService, event: AccessibilityEvent) {
        if (listeners.isEmpty()) return
        val data = try {
            val json = JSONObject().apply {
                put("type", event.eventType)
                put("packageName", event.packageName?.toString() ?: JSONObject.NULL)
                put("className", event.className?.toString() ?: JSONObject.NULL)
                put("text", event.text?.let { texts ->
                    JSONArray().apply { texts.forEach { put(it.toString()) } }
                } ?: JSONObject.NULL)
                put("time", event.eventTime)
            }
            json.toString()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to serialize accessibility event", e)
            return
        }
        dispatchToListeners("accessibility", data)
    }

    override fun onKeyEvent(event: KeyEvent) {
        if (listeners.isEmpty()) return
        val data = try {
            val json = JSONObject().apply {
                put("keyCode", event.keyCode)
                put("action", event.action)
                put("time", event.eventTime)
            }
            json.toString()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to serialize key event", e)
            return
        }
        dispatchToListeners("key", data)
    }

    /** Dispatch to each listener independently so one failure doesn't block others. */
    private fun dispatchToListeners(eventName: String, data: String) {
        listeners.forEach { listener ->
            try {
                listener.onEvent(eventName, data)
            } catch (e: Exception) {
                Log.w(TAG, "Event listener failed for '$eventName'", e)
            }
        }
    }

    companion object {
        private const val TAG = "EventAdapter"
    }
}
