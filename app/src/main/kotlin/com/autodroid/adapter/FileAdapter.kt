package com.autodroid.adapter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Domain exceptions for file operations in the adapter layer. */
sealed class FileAdapterException(message: String) : RuntimeException(message) {
    class PathDeniedException(path: String) : FileAdapterException("Access denied: $path")
    class FileNotFoundException(path: String) : FileAdapterException("Not found: $path")
    class NotADirectoryException(path: String) : FileAdapterException("Path is not a directory: $path")
    class NotAFileException(path: String) : FileAdapterException("Path is not a file: $path")
    class FileTooLargeException(size: Long, maxSize: Long) : FileAdapterException("File too large: $size > $maxSize")
}

@Singleton
class FileAdapter @Inject constructor() {

    companion object {
        const val SANDBOX_ROOT = "/sdcard"
        const val MAX_READ_SIZE = 10L * 1024 * 1024 // 10 MB

        /**
         * Validates and resolves a path within the sandbox.
         * Returns canonical File if within sandbox, null if path escapes.
         *
         * This is a pure function with no instance state dependency, so it lives
         * in the companion object for direct static access.
         */
        @JvmStatic
        fun validatePath(path: String, sandbox: String = SANDBOX_ROOT): File? {
            val sandboxDir = File(sandbox).canonicalFile
            val resolved = when {
                path.startsWith(sandbox) -> File(path).canonicalFile
                else -> File(sandbox, path.trimStart('/')).canonicalFile
            }
            return if (resolved.path == sandboxDir.path ||
                resolved.path.startsWith(sandboxDir.path + File.separator)) resolved else null
        }
    }

    /** Validate path or throw PathDeniedException. */
    fun requireValidPath(path: String): File =
        validatePath(path) ?: throw FileAdapterException.PathDeniedException(path)

    /** List files in a directory, returning a JSONArray of file info. */
    suspend fun listDirectory(dir: File): JSONArray = withContext(Dispatchers.IO) {
        if (!dir.exists()) throw FileAdapterException.FileNotFoundException(dir.absolutePath)
        if (!dir.isDirectory) throw FileAdapterException.NotADirectoryException(dir.absolutePath)
        val arr = JSONArray()
        dir.listFiles()?.forEach { file ->
            arr.put(JSONObject().apply {
                put("name", file.name)
                put("path", file.absolutePath)
                put("isDirectory", file.isDirectory)
                put("size", file.length())
                put("lastModified", file.lastModified())
            })
        }
        arr
    }

    /** Read text content from a file. */
    suspend fun readFile(file: File): Map<String, Any> = withContext(Dispatchers.IO) {
        if (!file.exists()) throw FileAdapterException.FileNotFoundException(file.absolutePath)
        if (!file.isFile) throw FileAdapterException.NotAFileException(file.absolutePath)
        if (file.length() > MAX_READ_SIZE) {
            throw FileAdapterException.FileTooLargeException(file.length(), MAX_READ_SIZE)
        }
        mapOf("path" to file.absolutePath, "content" to file.readText(), "size" to file.length())
    }

    /** Write or append text content to a file. */
    suspend fun writeFile(file: File, content: String, append: Boolean): Map<String, Any> =
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            if (append) file.appendText(content) else file.writeText(content)
            mapOf("path" to file.absolutePath, "size" to file.length(), "written" to true)
        }

    /** Delete a file or directory recursively. */
    suspend fun deleteFile(file: File): Map<String, Any> = withContext(Dispatchers.IO) {
        if (!file.exists()) throw FileAdapterException.FileNotFoundException(file.absolutePath)
        val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
        mapOf("path" to file.absolutePath, "deleted" to deleted)
    }
}
