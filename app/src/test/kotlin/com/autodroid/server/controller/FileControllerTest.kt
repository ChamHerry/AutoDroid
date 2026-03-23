package com.autodroid.server.controller

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FileControllerTest {

    @Test
    fun `validatePath allows path within sandbox`(@TempDir sandbox: File) {
        val result = FileController.validatePath("sub/file.txt", sandbox.absolutePath)
        assertNotNull(result)
        assertEquals(File(sandbox, "sub/file.txt").canonicalPath, result!!.canonicalPath)
    }

    @Test
    fun `validatePath blocks path traversal with dotdot`(@TempDir sandbox: File) {
        val result = FileController.validatePath("/../../../etc/passwd", sandbox.absolutePath)
        assertNull(result)
    }

    @Test
    fun `validatePath treats leading slash as relative to sandbox`(@TempDir sandbox: File) {
        val result = FileController.validatePath("/etc/passwd", sandbox.absolutePath)
        assertNotNull(result)
        assertEquals(File(sandbox, "etc/passwd").canonicalPath, result!!.canonicalPath)
    }

    @Test
    fun `validatePath allows root of sandbox with dot`(@TempDir sandbox: File) {
        val result = FileController.validatePath(".", sandbox.absolutePath)
        assertNotNull(result)
        assertEquals(sandbox.canonicalPath, result!!.canonicalPath)
    }

    @Test
    fun `validatePath allows root of sandbox with absolute sandbox path`(@TempDir sandbox: File) {
        val result = FileController.validatePath(sandbox.absolutePath, sandbox.absolutePath)
        assertNotNull(result)
        assertEquals(sandbox.canonicalPath, result!!.canonicalPath)
    }

    @Test
    fun `validatePath allows absolute path within sandbox`(@TempDir sandbox: File) {
        val result = FileController.validatePath(sandbox.absolutePath + "/sub/file.txt", sandbox.absolutePath)
        assertNotNull(result)
        assertEquals(File(sandbox, "sub/file.txt").canonicalPath, result!!.canonicalPath)
    }

    @Test
    fun `validatePath blocks dotdot traversal escaping sandbox`(@TempDir sandbox: File) {
        val result = FileController.validatePath("sub/../../etc/passwd", sandbox.absolutePath)
        assertNull(result)
    }

    @Test
    fun `validatePath handles path with trailing slash`(@TempDir sandbox: File) {
        val result = FileController.validatePath("Documents/", sandbox.absolutePath)
        assertNotNull(result)
        assertTrue(result!!.canonicalPath.startsWith(sandbox.canonicalPath))
    }

    @Test
    fun `validatePath blocks prefix bypass with similar dir name`(@TempDir sandbox: File) {
        // If sandbox is /tmp/foo, /tmp/foo-evil should be rejected
        val evilPath = sandbox.absolutePath + "-evil/secret.txt"
        val result = FileController.validatePath(evilPath, sandbox.absolutePath)
        assertNull(result)
    }
}
