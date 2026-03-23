package com.autodroid.adapter

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import com.autodroid.service.ScreenCaptureManager as ScreenCapture
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceAdapter @Inject constructor(
    private val context: Context,
    private val screenCapture: ScreenCapture,
) {

    fun getDeviceInfo(): Map<String, Any?> = mapOf(
        "brand" to Build.BRAND,
        "model" to Build.MODEL,
        "androidVersion" to Build.VERSION.RELEASE,
        "sdkVersion" to Build.VERSION.SDK_INT,
        "screenWidth" to getScreenWidth(),
        "screenHeight" to getScreenHeight(),
        "density" to getDensity(),
        "batteryLevel" to getBatteryLevel(),
    )

    fun getScreenWidth(): Int = getDisplayMetrics().widthPixels

    fun getScreenHeight(): Int = getDisplayMetrics().heightPixels

    @Suppress("DEPRECATION")
    private fun getDisplayMetrics(): DisplayMetrics {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    private fun getDensity(): Float {
        return context.resources.displayMetrics.density
    }

    fun getBatteryLevel(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    fun isScreenOn(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isInteractive
    }

    fun getBrightness(): Int {
        return Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
    }

    fun isScreenshotAvailable(): Boolean = screenCapture.isAvailable

    /**
     * Takes a screenshot with optional quality and scale parameters.
     *
     * @param quality JPEG quality (1-100). Default 80.
     * @param scale   Down-scale factor (0.0-1.0]. Default 1.0 (original size).
     */
    suspend fun takeScreenshot(quality: Int = 80, scale: Float = 1.0f): ByteArray =
        screenCapture.capture(quality, scale)
}
