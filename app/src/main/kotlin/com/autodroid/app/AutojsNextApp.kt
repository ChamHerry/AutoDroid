package com.autodroid.app

import android.app.Application
import android.content.ComponentCallbacks2
import android.provider.Settings
import android.util.Log
import com.autodroid.adapter.EventAdapter
import com.autodroid.server.AdapterContainer
import com.autodroid.service.AccessibilityServiceProvider
import com.autodroid.server.AuthMiddleware
import com.autodroid.server.CorsMiddleware
import com.autodroid.server.HttpServer
import com.autodroid.server.LoggerMiddleware
import com.autodroid.server.TokenScope
import com.autodroid.server.registerAllRoutes
import com.autodroid.server.controller.registerAuthRoutes
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AutojsNextApp : Application() {

    @Inject lateinit var adapterContainer: AdapterContainer
    @Inject lateinit var eventAdapter: EventAdapter
    @Inject lateinit var serviceProvider: AccessibilityServiceProvider

    private var httpServer: HttpServer? = null
    private var authMiddleware: AuthMiddleware? = null

    override fun onCreate() {
        super.onCreate()
        enableAccessibilityService()
        serviceProvider.addDelegate(eventAdapter)
        startHttpServer()
    }

    /**
     * Programmatically enable our AccessibilityService via Settings.Secure.
     * Requires WRITE_SECURE_SETTINGS (granted via: adb shell pm grant com.autodroid android.permission.WRITE_SECURE_SETTINGS)
     */
    private fun enableAccessibilityService() {
        val serviceName = "$packageName/${com.autodroid.service.AutojsAccessibilityService::class.java.canonicalName}"
        try {
            val existing = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""

            // Always toggle off→on to recover from crashed state.
            val cleaned = existing.replace(Regex(":?${Regex.escape(serviceName)}"), "")

            Settings.Secure.putString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                cleaned
            )

            val newValue = if (cleaned.isEmpty()) serviceName else "$cleaned:$serviceName"
            Settings.Secure.putString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                newValue
            )
            Settings.Secure.putString(
                contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                "1"
            )
            Log.i(TAG, "Accessibility service enabled (toggle reset)")
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot auto-enable accessibility: WRITE_SECURE_SETTINGS not granted. " +
                "Run: adb shell pm grant com.autodroid android.permission.WRITE_SECURE_SETTINGS")
        }
    }

    private fun startHttpServer() {
        // Stop any existing server instance (e.g. process recreation)
        httpServer?.let { old ->
            Log.w(TAG, "Stopping existing HTTP server before starting new one")
            old.stop()
            httpServer = null
        }

        val server = HttpServer(port = 8080, bindAddress = "0.0.0.0")
        server.use(CorsMiddleware())

        // Auth middleware — dual tokens (full + read-only)
        val prefs = SecurePrefs.get(this)
        var fullToken = prefs.getString("api_token", null)
        if (fullToken == null) {
            fullToken = SecurePrefs.generateToken()
            prefs.edit().putString("api_token", fullToken).apply()
        }
        var readToken = prefs.getString("api_token_read", null)
        if (readToken == null) {
            readToken = SecurePrefs.generateToken()
            prefs.edit().putString("api_token_read", readToken).apply()
        }
        val auth = AuthMiddleware(mapOf(
            TokenScope.FULL to fullToken,
            TokenScope.READ to readToken,
        ))
        server.use(auth)
        authMiddleware = auth

        val consoleLog = com.autodroid.server.ConsoleLog { level, msg ->
            com.autodroid.log.ConsoleRepository.append(level, msg)
        }
        server.use(LoggerMiddleware(consoleLog = consoleLog))

        // Auth route (token rotation) — registered before generic API routes
        registerAuthRoutes(server) { regenerateTokens() }

        registerAllRoutes(server, adapterContainer, this, consoleLog,
            consoleRepo = com.autodroid.log.ConsoleRepository)

        server.start()
        httpServer = server
        Log.i(TAG, "HTTP API server started on port 8080")
        consoleLog.append("info", "API server started on port 8080")
    }

    /**
     * Regenerate both FULL and READ tokens. Persists new tokens to
     * EncryptedSharedPreferences and updates the AuthMiddleware so that
     * old tokens are invalidated immediately.
     *
     * Synchronized to prevent concurrent calls from causing token
     * inconsistency between SharedPreferences and AuthMiddleware.
     * Uses commit() instead of apply() to ensure tokens are persisted
     * before updating the in-memory middleware state.
     *
     * @return Pair of (fullToken, readToken)
     */
    @Synchronized
    fun regenerateTokens(): Pair<String, String> {
        val prefs = SecurePrefs.get(this)
        val newFull = SecurePrefs.generateToken()
        val newRead = SecurePrefs.generateToken()
        val success = prefs.edit()
            .putString("api_token", newFull)
            .putString("api_token_read", newRead)
            .commit()  // Synchronous write — ensures persistence before updating middleware

        if (!success) throw RuntimeException("Failed to persist tokens")

        authMiddleware?.updateTokens(mapOf(
            TokenScope.FULL to newFull,
            TokenScope.READ to newRead,
        ))

        Log.w(TAG, "API tokens rotated — old tokens invalidated")
        return newFull to newRead
    }

    override fun onTerminate() {
        // Note: onTerminate() is never called on real Android devices.
        // Cleanup relies on process death (OS reclaims resources) and
        // startHttpServer() guarding against duplicate instances.
        super.onTerminate()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) {
            // Only release regenerable caches under memory pressure; keep HTTP server running
            adapterContainer.automator.releaseAllNodes()
        }
    }

    companion object {
        private const val TAG = "AutoDroid"
    }
}
