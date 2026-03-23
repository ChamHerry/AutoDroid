package com.autodroid.server

import com.autodroid.adapter.AppAdapter
import com.autodroid.adapter.AutomatorAdapter
import com.autodroid.adapter.DeviceAdapter
import com.autodroid.adapter.EventAdapter
import com.autodroid.adapter.FileAdapter
import com.autodroid.adapter.ShellAdapter
/**
 * Groups adapters needed by REST controllers.
 * Only includes adapters that have corresponding controller routes.
 */
data class AdapterContainer(
    val app: AppAdapter,
    val automator: AutomatorAdapter,
    val device: DeviceAdapter,
    val shell: ShellAdapter,
    val events: EventAdapter,
    val file: FileAdapter,
)
