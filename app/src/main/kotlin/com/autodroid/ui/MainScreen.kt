package com.autodroid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

import com.autodroid.log.ConsoleRepository
import com.autodroid.service.AccessibilityServiceProvider

enum class Screen(val label: String, val icon: ImageVector) {
    Dashboard("仪表盘", Icons.Default.Home),
    Logs("日志", Icons.AutoMirrored.Filled.List),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(serviceProvider: AccessibilityServiceProvider) {
    var currentScreen by remember { mutableStateOf(Screen.Dashboard) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("AutoDroid") })
        },
        bottomBar = {
            NavigationBar {
                Screen.entries.forEach { screen ->
                    NavigationBarItem(
                        selected = currentScreen == screen,
                        onClick = { currentScreen = screen },
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (currentScreen) {
                Screen.Dashboard -> DashboardScreen(serviceProvider)
                Screen.Logs -> ConsoleScreen()
            }
        }
    }
}
