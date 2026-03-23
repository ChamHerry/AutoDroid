package com.autodroid.automator

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Performs coordinate-based gestures via AccessibilityService.dispatchGesture().
 * Requires API 24+ (Android 7.0).
 */
class GlobalActionAutomator(private val service: AccessibilityService) {

    suspend fun click(x: Int, y: Int): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        return dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build())
    }

    suspend fun longClick(x: Int, y: Int): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        return dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 600))
            .build())
    }

    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Long): Boolean {
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        return dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration.coerceAtLeast(1)))
            .build())
    }

    suspend fun gesture(delay: Long, duration: Long, points: List<IntArray>): Boolean {
        if (points.size < 2) return false
        val path = Path().apply {
            moveTo(points[0][0].toFloat(), points[0][1].toFloat())
            for (i in 1 until points.size) {
                lineTo(points[i][0].toFloat(), points[i][1].toFloat())
            }
        }
        return dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, delay, duration.coerceAtLeast(1)))
            .build())
    }

    private suspend fun dispatchGesture(gesture: GestureDescription): Boolean =
        suspendCancellableCoroutine { cont ->
            val callback = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    cont.resume(true)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    cont.resume(false)
                }
            }
            if (!service.dispatchGesture(gesture, callback, null)) {
                cont.resume(false)
            }
        }
}
