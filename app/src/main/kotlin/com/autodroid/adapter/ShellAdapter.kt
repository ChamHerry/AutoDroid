package com.autodroid.adapter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class ShellResult(
    val code: Int,
    val stdout: String,
    val stderr: String,
)

/** Abstraction for process creation, enabling test injection. */
fun interface ProcessExecutor {
    fun start(command: List<String>): Process
}

private val defaultProcessExecutor = ProcessExecutor { command ->
    ProcessBuilder(command)
        .directory(java.io.File("/sdcard"))
        .redirectErrorStream(false)
        .start()
}

@Singleton
class ShellAdapter @Inject constructor(
    private val processExecutor: ProcessExecutor = defaultProcessExecutor,
) {

    private companion object {
        const val TIMEOUT_SECONDS = 30L
        const val MAX_OUTPUT_BYTES = 2 * 1024 * 1024 // 2 MB per stream
    }

    suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        runProcess(processExecutor.start(listOf("sh", "-c", command)))
    }

    suspend fun execRoot(command: String): ShellResult = withContext(Dispatchers.IO) {
        runProcess(processExecutor.start(listOf("su", "-c", command)))
    }

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = processExecutor.start(listOf("su", "-c", "id"))
            val exited = awaitProcess(process)
            exited && process.exitValue() == 0
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    private fun readLimited(stream: java.io.InputStream, maxBytes: Int): String {
        stream.bufferedReader().use { reader ->
            val sb = StringBuilder()
            val buf = CharArray(8192)
            var total = 0
            while (total < maxBytes) {
                val n = reader.read(buf, 0, minOf(buf.size, maxBytes - total))
                if (n == -1) break
                sb.append(buf, 0, n)
                total += n
            }
            return sb.toString()
        }
    }

    /**
     * Wait for a process to exit with cancellation-aware polling.
     * Returns true if the process exited within the timeout, false if it timed out (and was destroyed).
     */
    private suspend fun awaitProcess(process: Process): Boolean {
        val deadline = System.currentTimeMillis() + TIMEOUT_SECONDS * 1000
        while (process.isAlive) {
            if (System.currentTimeMillis() > deadline) {
                process.destroyForcibly()
                return false
            }
            kotlinx.coroutines.delay(50) // Yields to cancellation
        }
        return true
    }

    private suspend fun runProcess(process: Process): ShellResult = withContext(Dispatchers.IO) {
        // Read stdout and stderr in parallel to prevent pipe buffer deadlock
        val stdoutDeferred = async { readLimited(process.inputStream, MAX_OUTPUT_BYTES) }
        val stderrDeferred = async { readLimited(process.errorStream, MAX_OUTPUT_BYTES) }
        try {
            if (!awaitProcess(process)) {
                stdoutDeferred.cancel()
                stderrDeferred.cancel()
                val stdout = try { stdoutDeferred.await() } catch (_: Exception) { "" }
                return@withContext ShellResult(-1, stdout, "Command timed out after ${TIMEOUT_SECONDS}s")
            }
            ShellResult(process.exitValue(), stdoutDeferred.await(), stderrDeferred.await())
        } catch (e: kotlinx.coroutines.CancellationException) {
            process.destroyForcibly()
            throw e
        }
    }
}
