package com.autodroid.server

import android.content.Context
import com.autodroid.log.ConsoleRepository
import com.autodroid.server.controller.registerActionRoutes
import com.autodroid.server.controller.registerAppRoutes
import com.autodroid.server.controller.registerDeviceRoutes
import com.autodroid.server.controller.registerEventRoutes
import com.autodroid.server.controller.registerFileRoutes
import com.autodroid.server.controller.registerLogRoutes
import com.autodroid.server.controller.registerScreenshotRoutes
import com.autodroid.server.controller.registerShellRoutes
import com.autodroid.server.controller.registerStaticRoutes
import com.autodroid.server.controller.registerStatusRoutes
import com.autodroid.server.controller.registerUiRoutes

// Registers all REST API routes on the given HttpServer.
// Static routes are registered LAST so API routes take priority.
fun registerAllRoutes(
    server: HttpServer,
    adapters: AdapterContainer,
    context: Context,
    consoleLog: ConsoleLog? = null,
    consoleRepo: ConsoleRepository? = null,
) {
    // API routes (matched first)
    registerStatusRoutes(server, adapters.automator)
    registerDeviceRoutes(server, adapters.device)
    registerActionRoutes(server, adapters.automator)
    registerUiRoutes(server, adapters.automator)
    registerAppRoutes(server, adapters.app)
    registerShellRoutes(server, adapters.shell, consoleLog)
    registerEventRoutes(server, adapters.events)
    registerScreenshotRoutes(server, adapters.device)
    registerFileRoutes(server, adapters.file)
    if (consoleRepo != null) registerLogRoutes(server, consoleRepo)

    // Static file serving (fallback, must be last)
    registerStaticRoutes(server, context)
}
