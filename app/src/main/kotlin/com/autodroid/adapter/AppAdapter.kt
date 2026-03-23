package com.autodroid.adapter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.autodroid.service.AccessibilityServiceProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppAdapter @Inject constructor(
    private val context: Context,
    private val serviceProvider: AccessibilityServiceProvider,
) {

    suspend fun launchApp(packageName: String) {
        withContext(Dispatchers.Main) {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: throw IllegalArgumentException("App not found or has no launcher: $packageName")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    suspend fun openUrl(url: String) {
        val uri = Uri.parse(url)
        require(uri.scheme in listOf("http", "https")) {
            "Unsupported URL scheme: ${uri.scheme}. Only http and https are allowed."
        }
        withContext(Dispatchers.Main) {
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun getCurrentPackage(): String {
        val root = serviceProvider.get()?.rootInActiveWindow
        val packageName = root?.packageName?.toString() ?: ""
        root?.recycle()
        return packageName
    }

    suspend fun getClipboard(): String = withContext(Dispatchers.Main) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
    }

    suspend fun setClipboard(text: String) = withContext(Dispatchers.Main) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("autojs", text))
    }

    fun getAppName(packageName: String): String {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            ""
        }
    }

    fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
}
