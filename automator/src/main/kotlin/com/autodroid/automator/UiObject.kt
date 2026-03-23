package com.autodroid.automator

import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Wrapper around AccessibilityNodeInfo providing a richer API
 * for UI automation operations.
 */
class UiObject private constructor(
    private val nodeInfo: AccessibilityNodeInfo,
) {

    // ── Properties ──

    val text: CharSequence? get() = nodeInfo.text
    val contentDescription: CharSequence? get() = nodeInfo.contentDescription
    val className: CharSequence? get() = nodeInfo.className
    val packageName: CharSequence? get() = nodeInfo.packageName
    val viewIdResourceName: String? get() = nodeInfo.viewIdResourceName
    val isClickable: Boolean get() = nodeInfo.isClickable
    val isLongClickable: Boolean get() = nodeInfo.isLongClickable
    val isScrollable: Boolean get() = nodeInfo.isScrollable
    val isEnabled: Boolean get() = nodeInfo.isEnabled
    val isFocusable: Boolean get() = nodeInfo.isFocusable
    val isFocused: Boolean get() = nodeInfo.isFocused
    val isCheckable: Boolean get() = nodeInfo.isCheckable
    val isChecked: Boolean get() = nodeInfo.isChecked
    val isSelected: Boolean get() = nodeInfo.isSelected
    val isEditable: Boolean get() = nodeInfo.isEditable
    val isVisibleToUser: Boolean get() = nodeInfo.isVisibleToUser
    val childCount: Int get() = nodeInfo.childCount

    // ── Bounds ──

    fun boundsInScreen(): Rect {
        val rect = Rect()
        nodeInfo.getBoundsInScreen(rect)
        return rect
    }

    fun boundsInParent(): Rect {
        val rect = Rect()
        nodeInfo.getBoundsInParent(rect)
        return rect
    }

    // ── Tree Navigation ──

    fun parent(): UiObject? = nodeInfo.parent?.let { UiObject(it) }

    fun child(index: Int): UiObject? {
        if (index < 0 || index >= childCount) return null
        return nodeInfo.getChild(index)?.let { UiObject(it) }
    }

    fun children(): List<UiObject> = (0 until childCount).mapNotNull { child(it) }

    // ── Actions ──

    fun performAction(action: Int): Boolean = nodeInfo.performAction(action)

    fun performAction(action: Int, args: Bundle): Boolean = nodeInfo.performAction(action, args)

    fun click(): Boolean = performAction(AccessibilityNodeInfo.ACTION_CLICK)

    fun longClick(): Boolean = performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)

    fun setText(text: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun scrollForward(): Boolean = performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    fun scrollBackward(): Boolean = performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)

    // ── Lifecycle ──

    fun recycle() {
        nodeInfo.recycle()
    }

    companion object {
        fun createRoot(nodeInfo: AccessibilityNodeInfo): UiObject = UiObject(nodeInfo)
    }
}
