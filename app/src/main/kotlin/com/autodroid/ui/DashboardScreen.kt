package com.autodroid.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.wifi.WifiManager
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.autodroid.receiver.BootReceiver
import com.autodroid.service.AccessibilityServiceProvider
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Formatter

@Composable
fun DashboardScreen(serviceProvider: AccessibilityServiceProvider) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Refresh state periodically
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(2000)
            tick++
        }
    }

    val a11yEnabled = remember(tick) { serviceProvider.isConnected }
    val ipAddress = remember(tick) { getDeviceIp(context) }
    val apiUrl = "http://$ipAddress:8080"
    val apiToken = remember {
        com.autodroid.app.SecurePrefs.get(context)
            .getString("api_token", "") ?: ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Server Status Card ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Default.Cloud,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "API 服务运行中",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(12.dp))

                // API URL (copyable)
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            apiUrl,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                        )
                        IconButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("api_url", apiUrl))
                            Toast.makeText(context, "已复制!", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, "Copy URL")
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "端口: 8080 · CORS: 已启用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )

                // API Token (copyable)
                if (apiToken.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Key,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Token: $apiToken",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("api_token", apiToken))
                                    Toast.makeText(context, "Token 已复制!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "复制 Token",
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Status Cards ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusCard(
                icon = Icons.Default.Accessibility,
                title = "无障碍服务",
                value = if (a11yEnabled) "已开启" else "未开启",
                ok = a11yEnabled,
                modifier = Modifier.weight(1f),
            )
            StatusCard(
                icon = Icons.Default.Wifi,
                title = "IP 地址",
                value = ipAddress,
                ok = ipAddress != "127.0.0.1",
                modifier = Modifier.weight(1f),
            )
        }

        // ── Boot Unlock Settings ──
        BootUnlockSettings()

        // ── Quick Test Section ──
        Text(
            "快速测试",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "curl $apiUrl/api/status",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "curl -H 'Authorization: Bearer $apiToken' $apiUrl/api/ui/dump",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "curl -H 'Authorization: Bearer $apiToken' -X POST $apiUrl/api/actions/click -d '{\"x\":500,\"y\":500}'",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "curl -H 'Authorization: Bearer $apiToken' -X POST $apiUrl/api/shell/exec -d '{\"command\":\"ls /\"}'",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // ── API Endpoints Summary ──
        Text(
            "可用接口",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        EndpointGroup("设备", listOf(
            "GET  /api/status",
            "GET  /api/device/info",
            "GET  /api/device/screen",
            "GET  /api/device/battery",
        ))
        EndpointGroup("UI 自动化", listOf(
            "GET  /api/ui/dump",
            "POST /api/ui/find",
            "POST /api/ui/click",
            "POST /api/ui/input",
            "POST /api/ui/scroll",
            "POST /api/ui/wait",
        ))
        EndpointGroup("坐标操作", listOf(
            "POST /api/actions/click",
            "POST /api/actions/swipe",
            "POST /api/actions/key",
            "POST /api/actions/gesture",
        ))
        EndpointGroup("应用 / Shell / 文件", listOf(
            "POST /api/app/launch",
            "GET  /api/app/current",
            "POST /api/shell/exec",
            "GET  /api/files/list",
        ))
        EndpointGroup("日志", listOf(
            "GET  /api/logs",
            "GET  /api/logs/stream  (SSE)",
        ))
    }
}

@Composable
private fun StatusCard(
    icon: ImageVector,
    title: String,
    value: String,
    ok: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (ok) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun EndpointGroup(title: String, endpoints: List<String>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            endpoints.forEach { ep ->
                Text(
                    ep,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun BootUnlockSettings() {
    val context = LocalContext.current
    val prefs = remember {
        com.autodroid.app.SecurePrefs.get(context)
    }
    var password by remember { mutableStateOf(prefs.getString(BootReceiver.KEY_PASSWORD, "") ?: "") }
    var passwordVisible by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "开机自动解锁",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "设置锁屏密码，开机后自动解锁",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; saved = false },
                    label = { Text("锁屏密码") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility,
                                contentDescription = "切换可见性",
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = {
                    prefs.edit().putString(BootReceiver.KEY_PASSWORD, password).apply()
                    saved = true
                }) {
                    Text(if (saved) "已保存" else "保存")
                }
            }
            if (password.isEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "留空则跳过自动解锁",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

private fun getDeviceIp(context: Context): String {
    try {
        NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { intf ->
            intf.inetAddresses?.toList()?.forEach { addr ->
                if (!addr.isLoopbackAddress && addr is Inet4Address) {
                    return addr.hostAddress ?: "127.0.0.1"
                }
            }
        }
    } catch (_: Exception) {}
    // Fallback: WifiManager
    try {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip = wm.connectionInfo.ipAddress
        if (ip != 0) {
            return Formatter().format(
                "%d.%d.%d.%d",
                ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff
            ).toString()
        }
    } catch (_: Exception) {}
    return "127.0.0.1"
}
