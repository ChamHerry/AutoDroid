package com.autodroid.receiver

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import com.autodroid.adapter.AutomatorAdapter
import com.autodroid.adapter.ShellAdapter
import com.autodroid.app.MainActivity
import com.autodroid.service.AccessibilityServiceProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BootEntryPoint {
        fun shellAdapter(): ShellAdapter
        fun automatorAdapter(): AutomatorAdapter
        fun serviceProvider(): AccessibilityServiceProvider
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.i(TAG, "Boot completed, starting AutoDroid")

        val prefs = com.autodroid.app.SecurePrefs.get(context)
        val password = prefs.getString(KEY_PASSWORD, null)

        // Wake screen via WakeLock (proper API, always works)
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wakeLock = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "AutoDroid:BootWakeLock"
        )
        wakeLock.acquire(60_000L)

        if (!password.isNullOrEmpty()) {
            val pendingResult = goAsync()
            // Single runBlocking entry point — all internal calls are suspend
            runBlocking(Dispatchers.IO) {
                try {
                    unlockScreen(context, password)
                } catch (e: Exception) {
                    Log.e(TAG, "Unlock failed", e)
                } finally {
                    launchMainActivity(context)
                    if (wakeLock.isHeld) wakeLock.release()
                    pendingResult.finish()
                }
            }
        } else {
            launchMainActivity(context)
            wakeLock.release()
        }
    }

    private suspend fun unlockScreen(context: Context, password: String) {
        val ep = EntryPointAccessors.fromApplication(
            context.applicationContext, BootEntryPoint::class.java
        )
        val shell = ep.shellAdapter()
        val automator = ep.automatorAdapter()
        val serviceProvider = ep.serviceProvider()

        // Wait for AccessibilityService to connect (up to 15s after boot)
        if (!waitForService(serviceProvider)) {
            Log.w(TAG, "AccessibilityService not available, falling back to shell")
            unlockViaShell(shell, password)
            return
        }

        val service = serviceProvider.get() ?: run {
            Log.w(TAG, "AccessibilityService not available after wait, falling back to shell")
            unlockViaShell(shell, password)
            return
        }

        Log.i(TAG, "Using AccessibilityService for unlock")
        delay(1000)

        // Swipe up to reveal PIN pad
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val bounds = wm.currentWindowMetrics.bounds
        val x = bounds.width() / 2
        val yFrom = bounds.height() * 3 / 4
        val yTo = bounds.height() / 4
        automator.swipe(x, yFrom, x, yTo, 300)
        delay(1500)

        // Input PIN digits by finding buttons in accessibility tree
        if (!inputPinViaAccessibility(service, password)) {
            Log.w(TAG, "Accessibility PIN input failed, falling back to shell")
            shell.exec("input text ${shellQuote(password)}")
            delay(300)
            shell.exec("input keyevent KEYCODE_ENTER")
            return
        }

        // Confirm — click "Enter" / "确认" button, or send ENTER key
        delay(300)
        if (!clickConfirmButton(service)) {
            shell.exec("input keyevent KEYCODE_ENTER")
        }

        Log.i(TAG, "Unlock sequence completed via AccessibilityService")
    }

    private suspend fun waitForService(provider: AccessibilityServiceProvider): Boolean {
        repeat(30) {
            if (provider.isConnected) return true
            delay(500)
        }
        return false
    }

    private suspend fun inputPinViaAccessibility(service: AccessibilityService, pin: String): Boolean {
        for (digit in pin) {
            val digitStr = digit.toString()
            val node = findNodeByText(service, digitStr)
            if (node != null) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                node.recycle()
                delay(100)
            } else {
                Log.w(TAG, "Cannot find button for digit: $digitStr")
                return false
            }
        }
        return true
    }

    private fun clickConfirmButton(service: AccessibilityService): Boolean {
        for (label in listOf("确认", "确定", "OK", "Enter", "Done", "✓")) {
            val node = findNodeByText(service, label)
            if (node != null) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                node.recycle()
                return true
            }
        }
        return false
    }

    private fun findNodeByText(service: AccessibilityService, text: String): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        val nodes = root.findAccessibilityNodeInfosByText(text)
        for (node in nodes) {
            val nodeText = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
            if (nodeText == text) {
                root.recycle()
                return node
            }
        }
        nodes.forEach { it.recycle() }
        root.recycle()
        return null
    }

    /** Fallback: unlock entirely via shell commands */
    private suspend fun unlockViaShell(shell: ShellAdapter, password: String) {
        shell.exec("""
            input keyevent KEYCODE_WAKEUP
            sleep 0.5
            dumpsys power | grep -q 'mWakefulness=Asleep' && input keyevent KEYCODE_POWER
            sleep 1
            SIZE=$(wm size | grep -oE '[0-9]+x[0-9]+' | tail -1)
            W=$(echo ${'$'}SIZE | cut -dx -f1)
            H=$(echo ${'$'}SIZE | cut -dx -f2)
            X=${'$'}((W / 2))
            Y1=${'$'}((H * 3 / 4))
            Y2=${'$'}((H / 4))
            input swipe ${'$'}X ${'$'}Y1 ${'$'}X ${'$'}Y2 300
            sleep 1
        """.trimIndent())
        shell.exec("input text ${shellQuote(password)}")
        delay(300)
        shell.exec("input keyevent KEYCODE_ENTER")
        Log.i(TAG, "Unlock sequence completed via shell fallback")
    }

    private fun launchMainActivity(context: Context) {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(launchIntent)
    }

    /** Escape text for safe use in shell commands via single-quoting. */
    private fun shellQuote(s: String): String = "'" + s.replace("'", "'\"'\"'") + "'"

    companion object {
        private const val TAG = "BootReceiver"
        const val KEY_PASSWORD = "unlock_password"
    }
}
