package com.autodroid.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.CopyOnWriteArrayList

class AutojsAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        delegates.forEach { it.onAccessibilityEvent(this, event) }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        delegates.forEach { it.onKeyEvent(event) }
        return super.onKeyEvent(event)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        // Don't clear delegates — they are registered by Application.onCreate() and shared
        // across service lifecycle. Clearing them here would lose EventAdapter registration
        // if the service is destroyed and recreated by the system.
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: AutojsAccessibilityService? = null
            private set

        private val delegates = CopyOnWriteArrayList<AccessibilityDelegate>()

        fun addDelegate(delegate: AccessibilityDelegate) {
            delegates.add(delegate)
        }

        fun removeDelegate(delegate: AccessibilityDelegate) {
            delegates.remove(delegate)
        }
    }
}

interface AccessibilityDelegate {
    fun onAccessibilityEvent(service: AccessibilityService, event: AccessibilityEvent) {}
    fun onKeyEvent(event: KeyEvent) {}
}
