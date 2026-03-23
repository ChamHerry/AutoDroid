package com.autodroid.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/** Abstraction for accessing the AccessibilityService instance, enabling DI and testability. */
interface AccessibilityServiceProvider {
    fun get(): AccessibilityService?
    val isConnected: Boolean get() = get() != null
    fun getRootInActiveWindow(): AccessibilityNodeInfo? = get()?.rootInActiveWindow
    fun getWindows(): List<AccessibilityWindowInfo>? = null
    fun addDelegate(delegate: AccessibilityDelegate) {}
    fun removeDelegate(delegate: AccessibilityDelegate) {}
}

/** Production implementation backed by AutojsAccessibilityService static instance. */
class DefaultAccessibilityServiceProvider : AccessibilityServiceProvider {
    override fun get(): AccessibilityService? = AutojsAccessibilityService.instance

    override fun getWindows(): List<AccessibilityWindowInfo>? {
        val service = AutojsAccessibilityService.instance ?: return null
        return service.windows
    }

    override fun addDelegate(delegate: AccessibilityDelegate) {
        AutojsAccessibilityService.addDelegate(delegate)
    }

    override fun removeDelegate(delegate: AccessibilityDelegate) {
        AutojsAccessibilityService.removeDelegate(delegate)
    }
}
