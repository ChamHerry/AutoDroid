package com.autodroid.server.controller

import com.autodroid.adapter.ShellAdapter
import com.autodroid.server.ApiException
import com.autodroid.server.ConsoleLog
import com.autodroid.server.HttpServer
import java.util.logging.Logger

private const val MAX_COMMAND_LENGTH = 10_000

private val auditLogger: Logger = Logger.getLogger("ShellAudit")

/** Commands that warrant an extra WARNING-level audit entry. */
private val DANGEROUS_PATTERNS = listOf(
    Regex("""rm\s+-[rf]*\s+/"""),          // rm -rf / variants
    Regex("""dd\s+.*of=/dev/"""),           // dd writing to device nodes
    Regex("""mkfs\."""),                     // filesystem format
    Regex(""">\s*/dev/"""),                  // redirect to device nodes
    Regex("""chmod\s+777\s+/"""),           // global permission escalation
    Regex("""setprop\b"""),                  // system property modification
    Regex("""reboot\b"""),                   // device reboot
    Regex("""pm\s+uninstall\b"""),           // uninstall packages
    Regex("""am\s+force-stop\b"""),          // force-stop applications
)

fun registerShellRoutes(server: HttpServer, shell: ShellAdapter, consoleLog: ConsoleLog? = null) {

    // POST /api/shell/exec  { "command": "ls -la", "root": false }
    server.post("/api/shell/exec") { req, res ->
        val body = req.jsonBody
        val command = body.getString("command")
        val root = body.optBoolean("root", false)
        if (command.length > MAX_COMMAND_LENGTH) {
            throw ApiException(400, "Command too long (max $MAX_COMMAND_LENGTH chars)")
        }

        // Full command audit log (complete, not truncated)
        val prefix = if (root) "[ROOT] " else ""
        auditLogger.warning("${prefix}exec: $command")

        // Flag potentially dangerous commands (does not block execution)
        val commandLower = command.lowercase()
        for (pattern in DANGEROUS_PATTERNS) {
            if (pattern.containsMatchIn(commandLower)) {
                auditLogger.warning("DANGEROUS pattern detected \"${pattern.pattern}\" in command: $command")
                break
            }
        }

        consoleLog?.append("warn", "Shell exec${if (root) " [ROOT]" else ""}: ${command.take(200)}")
        val result = if (root) shell.execRoot(command) else shell.exec(command)
        res.sendJson(mapOf(
            "code" to result.code,
            "stdout" to result.stdout,
            "stderr" to result.stderr,
        ))
    }

    // GET /api/shell/root - Check if root is available
    server.get("/api/shell/root") { _, res ->
        res.sendJson(mapOf("rootAvailable" to shell.isRootAvailable()))
    }
}
