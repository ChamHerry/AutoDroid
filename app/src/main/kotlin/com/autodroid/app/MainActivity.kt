package com.autodroid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.autodroid.service.AccessibilityServiceProvider
import com.autodroid.ui.AutoDroidTheme
import com.autodroid.ui.MainScreen
import com.autodroid.ui.PermissionGuideScreen
import com.autodroid.ui.checkAllRequiredPermissions
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var serviceProvider: AccessibilityServiceProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AutoDroidTheme {
                var permissionsGranted by remember {
                    mutableStateOf(checkAllRequiredPermissions(this@MainActivity))
                }

                // Re-check permissions when the activity resumes (user returns from settings)
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            permissionsGranted = checkAllRequiredPermissions(this@MainActivity)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                if (permissionsGranted) {
                    MainScreen(serviceProvider)
                } else {
                    PermissionGuideScreen(
                        onAllGranted = { permissionsGranted = true }
                    )
                }
            }
        }
    }
}
