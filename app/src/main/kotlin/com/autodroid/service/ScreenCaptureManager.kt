package com.autodroid.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Screenshot via AccessibilityService.takeScreenshot() (Android 11+).
 * No MediaProjection permission dialog needed.
 */
@Singleton
class ScreenCaptureManager @Inject constructor(
    private val serviceProvider: AccessibilityServiceProvider,
) {
    private val captureMutex = Mutex()

    /** Cached screenshot to avoid hitting Android API cooldown on rapid calls. */
    private var cachedBytes: ByteArray? = null
    private var cachedQuality: Int = 0
    private var cachedScale: Float = 0f
    private var cachedAt: Long = 0L

    val isAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && serviceProvider.isConnected

    /**
     * Captures the screen and returns JPEG bytes.
     * Results are cached for [CACHE_TTL_MS] ms — repeated calls with the same
     * quality/scale within the window return instantly without hitting the system API.
     *
     * @param quality JPEG compression quality (1-100). Default 80.
     * @param scale   Down-scale factor (0.0-1.0]. 1.0 means original size,
     *                0.5 means half width/height. Default 1.0.
     */
    suspend fun capture(quality: Int = 80, scale: Float = 1.0f): ByteArray = captureMutex.withLock {
        val clampedQuality = quality.coerceIn(1, 100)
        val clampedScale = scale.coerceIn(0.1f, 1.0f)

        // Return cached result if same params and within TTL
        val now = System.currentTimeMillis()
        cachedBytes?.let { bytes ->
            if (cachedQuality == clampedQuality && cachedScale == clampedScale
                && now - cachedAt < CACHE_TTL_MS) {
                return bytes
            }
        }

        val service = serviceProvider.get()
            ?: throw IllegalStateException("Accessibility service not connected")

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            throw IllegalStateException("takeScreenshot requires Android 11+")
        }

        val bytes = withTimeout(10_000L) { suspendCancellableCoroutine { cont ->
            service.takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        var bitmap: Bitmap? = null
                        try {
                            bitmap = Bitmap.wrapHardwareBuffer(
                                result.hardwareBuffer, result.colorSpace
                            )
                            if (bitmap == null) {
                                cont.resumeWithException(IllegalStateException("Failed to create bitmap"))
                                return
                            }
                            val softBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                            bitmap.recycle()
                            bitmap = null // mark as handled

                            // Apply scale if < 1.0
                            val finalBitmap = if (clampedScale < 1.0f) {
                                val scaledWidth = (softBitmap.width * clampedScale).toInt().coerceAtLeast(1)
                                val scaledHeight = (softBitmap.height * clampedScale).toInt().coerceAtLeast(1)
                                val scaled = Bitmap.createScaledBitmap(softBitmap, scaledWidth, scaledHeight, true)
                                softBitmap.recycle()
                                scaled
                            } else {
                                softBitmap
                            }

                            val baos = ByteArrayOutputStream(finalBitmap.byteCount / 10)
                            finalBitmap.compress(Bitmap.CompressFormat.JPEG, clampedQuality, baos)
                            finalBitmap.recycle()

                            cont.resume(baos.toByteArray())
                        } catch (e: Exception) {
                            cont.resumeWithException(e)
                        } finally {
                            // Ensure native resources are always released
                            bitmap?.recycle()
                            result.hardwareBuffer.close()
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        cont.resumeWithException(
                            IllegalStateException("Screenshot failed with error code: $errorCode")
                        )
                    }
                }
            )
        } }

        // Cache the result
        cachedBytes = bytes
        cachedQuality = clampedQuality
        cachedScale = clampedScale
        cachedAt = System.currentTimeMillis()
        return bytes
    }

    companion object {
        /** Cache TTL — avoids Android takeScreenshot() API cooldown on rapid calls. */
        private const val CACHE_TTL_MS = 500L
    }
}
