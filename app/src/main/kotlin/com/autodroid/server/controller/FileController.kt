package com.autodroid.server.controller

import com.autodroid.adapter.FileAdapter
import com.autodroid.server.ApiException
import com.autodroid.server.HttpServer
import com.autodroid.server.Request

/**
 * FileController retains only the static [validatePath] helper for backward
 * compatibility with existing tests (FileControllerTest).
 *
 * All route handlers use the Hilt-injected [FileAdapter] passed to [registerFileRoutes].
 * FileAdapterException is handled centrally by the Router -- no per-handler try-catch needed.
 */
object FileController {

    /**
     * Validates and resolves a path within the sandbox.
     * Delegates to [FileAdapter.validatePath] (companion-level, no instance required).
     *
     * Kept for backward compatibility with tests that call FileController.validatePath directly.
     */
    fun validatePath(path: String, sandbox: String = FileAdapter.SANDBOX_ROOT): java.io.File? =
        FileAdapter.validatePath(path, sandbox)
}

/** Extract and validate a path query parameter using the given [FileAdapter]. */
private fun requirePathFromQuery(req: Request, fileAdapter: FileAdapter): java.io.File {
    val path = req.query["path"]
    if (path.isNullOrBlank()) throw ApiException(400, "path query parameter is required")
    return fileAdapter.requireValidPath(path)
}

fun registerFileRoutes(server: HttpServer, fileAdapter: FileAdapter) {

    // GET /api/files/list?path=/sdcard/
    server.get("/api/files/list") { req, res ->
        val dir = requirePathFromQuery(req, fileAdapter)
        val files = fileAdapter.listDirectory(dir)
        res.sendJson(files)
    }

    // GET /api/files/read?path=/sdcard/test.txt
    server.get("/api/files/read") { req, res ->
        val file = requirePathFromQuery(req, fileAdapter)
        val result = fileAdapter.readFile(file)
        res.sendJson(result)
    }

    // POST /api/files/write  { "path": "/sdcard/test.txt", "content": "hello", "append": false }
    server.post("/api/files/write") { req, res ->
        val body = req.jsonBody
        val path = body.getString("path")
        val content = body.getString("content")
        val append = body.optBoolean("append", false)
        val file = fileAdapter.requireValidPath(path)
        val result = fileAdapter.writeFile(file, content, append)
        res.sendJson(result)
    }

    // DELETE /api/files?path=/sdcard/test.txt
    server.delete("/api/files") { req, res ->
        val file = requirePathFromQuery(req, fileAdapter)
        val result = fileAdapter.deleteFile(file)
        res.sendJson(result)
    }
}
