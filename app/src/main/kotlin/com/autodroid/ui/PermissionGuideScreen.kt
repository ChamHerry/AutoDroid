package com.autodroid.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.autodroid.service.AutojsAccessibilityService

// --- Permission check helpers ---

fun isAccessibilityEnabled(context: Context): Boolean {
    val service =
        "${context.packageName}/${AutojsAccessibilityService::class.java.canonicalName}"
    val enabledServices = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: ""
    return enabledServices.contains(service)
}

fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

fun hasStoragePermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        true // Below API 30, handled via runtime permissions
    }
}

fun checkAllRequiredPermissions(context: Context): Boolean =
    isAccessibilityEnabled(context) && canDrawOverlays(context)

// --- Data model ---

private data class PermissionEntry(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val required: Boolean,
    val isGranted: (Context) -> Boolean,
    val openSettings: (Context) -> Unit,
)

private val permissionEntries = listOf(
    PermissionEntry(
        icon = Icons.Default.Star,
        title = "无障碍服务",
        description = "用于 UI 自动化操作",
        required = true,
        isGranted = ::isAccessibilityEnabled,
        openSettings = { ctx ->
            ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        },
    ),
    PermissionEntry(
        icon = Icons.Default.Info,
        title = "悬浮窗权限",
        description = "用于悬浮窗和对话框显示",
        required = true,
        isGranted = ::canDrawOverlays,
        openSettings = { ctx ->
            try {
                ctx.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${ctx.packageName}")
                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                )
            } catch (_: Exception) {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                        .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                )
            }
        },
    ),
    PermissionEntry(
        icon = Icons.Default.Build,
        title = "存储权限 (可选)",
        description = "用于访问外部存储中的脚本文件",
        required = false,
        isGranted = { hasStoragePermission() },
        openSettings = { ctx ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    ctx.startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:${ctx.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                } catch (_: Exception) {
                    ctx.startActivity(
                        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    )
                }
            }
        },
    ),
)

// --- Composables ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionGuideScreen(onAllGranted: () -> Unit) {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }

    // Re-evaluated whenever refreshKey changes
    val statuses = remember(refreshKey) {
        permissionEntries.map { it.isGranted(context) }
    }

    val allRequiredGranted = permissionEntries.zip(statuses).all { (entry, granted) ->
        !entry.required || granted
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("权限引导") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text(
                text = "AutoDroid 需要以下权限才能正常运行，请逐项开启。",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(permissionEntries.size) { index ->
                    val entry = permissionEntries[index]
                    val granted = statuses[index]
                    PermissionItem(
                        icon = entry.icon,
                        title = entry.title,
                        description = entry.description,
                        granted = granted,
                        onClick = {
                            entry.openSettings(context)
                        },
                    )
                }
            }

            // Refresh button — user taps after returning from settings
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { refreshKey++ },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("刷新状态")
                }

                Button(
                    onClick = onAllGranted,
                    enabled = allRequiredGranted,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("继续")
                }
            }
        }
    }
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (granted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    if (granted) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Done,
                            contentDescription = "已开启",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!granted) {
                Spacer(modifier = Modifier.width(12.dp))
                FilledTonalButton(onClick = onClick) {
                    Text("开启")
                }
            }
        }
    }
}
