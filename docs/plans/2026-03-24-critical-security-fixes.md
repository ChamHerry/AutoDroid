# Critical Security Fixes Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix 3 critical security vulnerabilities: unauthenticated HTTP API, file path traversal, and NodePool memory leak.

**Architecture:** Add AuthMiddleware with token-based authentication to the existing middleware pipeline; add path sandboxing to FileController; add TTL-based auto-eviction to NodePool. All changes are backward-compatible — existing middleware/routing patterns are preserved.

**Tech Stack:** Kotlin, Android SharedPreferences (token storage), existing HttpServer middleware pipeline.

---

### Task 1: AuthMiddleware — Token-Based Authentication

**Files:**
- Create: `app/src/main/kotlin/com/autodroid/server/AuthMiddleware.kt`
- Modify: `app/src/main/kotlin/com/autodroid/server/ApiRoutes.kt`
- Modify: `app/src/main/kotlin/com/autodroid/server/Middleware.kt` (reference only)
- Test: `app/src/test/kotlin/com/autodroid/server/AuthMiddlewareTest.kt`

**Context:**
- Middleware interface: `fun interface Middleware { suspend fun handle(request: Request, response: Response, next: suspend () -> Unit) }`
- CorsMiddleware already checks `Authorization` in allowed headers
- Server pipeline: `server.use(middleware)` adds to list; pipeline runs in order before routing
- Request headers are lowercase-keyed: `request.headers["authorization"]`

**Step 1: Write the failing test**

```kotlin
// app/src/test/kotlin/com/autodroid/server/AuthMiddlewareTest.kt
package com.autodroid.server

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AuthMiddlewareTest {

    private lateinit var middleware: AuthMiddleware

    @BeforeEach
    fun setUp() {
        middleware = AuthMiddleware("test-secret-token")
    }

    private fun makeRequest(path: String, auth: String? = null): Request {
        val headers = mutableMapOf<String, String>()
        if (auth != null) headers["authorization"] = auth
        return Request(
            method = "GET",
            path = path,
            headers = headers,
            query = emptyMap(),
            params = emptyMap(),
            body = "",
        )
    }

    private fun makeResponse(): Response {
        val socket = mockk<java.net.Socket>(relaxed = true)
        val stream = java.io.ByteArrayOutputStream()
        every { socket.getOutputStream() } returns stream
        return Response(socket)
    }

    @Test
    fun `static file requests bypass auth`() = runBlocking {
        val req = makeRequest("/index.html")
        val res = makeResponse()
        var nextCalled = false
        middleware.handle(req, res) { nextCalled = true }
        assertTrue(nextCalled)
    }

    @Test
    fun `status endpoint bypasses auth`() = runBlocking {
        val req = makeRequest("/api/status")
        val res = makeResponse()
        var nextCalled = false
        middleware.handle(req, res) { nextCalled = true }
        assertTrue(nextCalled)
    }

    @Test
    fun `valid bearer token passes auth`() = runBlocking {
        val req = makeRequest("/api/shell/exec", "Bearer test-secret-token")
        val res = makeResponse()
        var nextCalled = false
        middleware.handle(req, res) { nextCalled = true }
        assertTrue(nextCalled)
    }

    @Test
    fun `valid query token passes auth`() = runBlocking {
        val req = Request(
            method = "GET", path = "/api/ui/dump",
            headers = emptyMap(), query = mapOf("token" to "test-secret-token"),
            params = emptyMap(), body = "",
        )
        val res = makeResponse()
        var nextCalled = false
        middleware.handle(req, res) { nextCalled = true }
        assertTrue(nextCalled)
    }

    @Test
    fun `missing token returns 401`() = runBlocking {
        val req = makeRequest("/api/shell/exec")
        val res = makeResponse()
        var nextCalled = false
        middleware.handle(req, res) { nextCalled = true }
        assertFalse(nextCalled)
    }

    @Test
    fun `wrong token returns 403`() = runBlocking {
        val req = makeRequest("/api/shell/exec", "Bearer wrong-token")
        val res = makeResponse()
        var nextCalled = false
        middleware.handle(req, res) { nextCalled = true }
        assertFalse(nextCalled)
    }

    @Test
    fun `empty token disables auth`() = runBlocking {
        val openMiddleware = AuthMiddleware("")
        val req = makeRequest("/api/shell/exec")
        val res = makeResponse()
        var nextCalled = false
        openMiddleware.handle(req, res) { nextCalled = true }
        assertTrue(nextCalled)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.autodroid.server.AuthMiddlewareTest" --info`
Expected: FAIL — class AuthMiddleware not found

**Step 3: Implement AuthMiddleware**

```kotlin
// app/src/main/kotlin/com/autodroid/server/AuthMiddleware.kt
package com.autodroid.server

/**
 * Token-based authentication middleware.
 * Checks Bearer token in Authorization header or ?token= query parameter.
 * Bypasses auth for static files and /api/status.
 * Pass empty token to disable auth (open access).
 */
class AuthMiddleware(private val token: String) : Middleware {

    private val publicPrefixes = listOf("/api/status")

    override suspend fun handle(request: Request, response: Response, next: suspend () -> Unit) {
        // Disabled when token is empty
        if (token.isEmpty()) {
            next()
            return
        }

        // Bypass auth for non-API requests (static files) and public endpoints
        if (!request.path.startsWith("/api/") || publicPrefixes.any { request.path.startsWith(it) }) {
            next()
            return
        }

        // Check Bearer token in Authorization header
        val authHeader = request.headers["authorization"]
        if (authHeader != null) {
            val bearerToken = authHeader.removePrefix("Bearer ").trim()
            if (bearerToken == token) {
                next()
                return
            }
            response.sendError(403, "Invalid token")
            return
        }

        // Check ?token= query parameter (for SSE/browser access)
        val queryToken = request.query["token"]
        if (queryToken == token) {
            next()
            return
        }

        response.sendError(401, "Authentication required. Use 'Authorization: Bearer <token>' header or '?token=<token>' query parameter.")
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.autodroid.server.AuthMiddlewareTest" --info`
Expected: All 7 tests PASS

**Step 5: Wire AuthMiddleware into server startup**

Modify `app/src/main/kotlin/com/autodroid/app/AutojsNextApp.kt`:
- Read token from SharedPreferences `boot_config` key `api_token`
- Add `AuthMiddleware` to pipeline (AFTER CorsMiddleware, BEFORE LoggerMiddleware)
- Generate random token on first launch if not set
- Log token to console for user visibility

```kotlin
// In startHttpServer(), after server.use(CorsMiddleware()):
val prefs = getSharedPreferences("boot_config", MODE_PRIVATE)
var apiToken = prefs.getString("api_token", null)
if (apiToken == null) {
    apiToken = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
    prefs.edit().putString("api_token", apiToken).apply()
}
server.use(AuthMiddleware(apiToken))
// then server.use(LoggerMiddleware()) as before
Log.i(TAG, "API token: $apiToken")
com.autodroid.ui.ConsoleRepository.append("info", "API token: $apiToken")
```

**Step 6: Add token display to DashboardScreen**

Modify `app/src/main/kotlin/com/autodroid/ui/DashboardScreen.kt`:
- Read `api_token` from SharedPreferences
- Display in API Server card below the URL

**Step 7: Build and verify**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 8: Commit**

```bash
git add app/src/main/kotlin/com/autodroid/server/AuthMiddleware.kt \
       app/src/test/kotlin/com/autodroid/server/AuthMiddlewareTest.kt \
       app/src/main/kotlin/com/autodroid/app/AutojsNextApp.kt \
       app/src/main/kotlin/com/autodroid/ui/DashboardScreen.kt
git commit -m "feat: add token-based AuthMiddleware for HTTP API security"
```

---

### Task 2: FileController — Path Sandboxing

**Files:**
- Modify: `app/src/main/kotlin/com/autodroid/server/controller/FileController.kt`
- Test: `app/src/test/kotlin/com/autodroid/server/controller/FileControllerTest.kt`

**Context:**
- Current FileController accepts arbitrary paths with no validation
- 4 endpoints: list, read, write, delete (delete supports recursive)
- Android external storage: `/sdcard/` or `Environment.getExternalStorageDirectory()`

**Step 1: Write the failing test**

```kotlin
// app/src/test/kotlin/com/autodroid/server/controller/FileControllerTest.kt
package com.autodroid.server.controller

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FileControllerTest {

    @Test
    fun `validatePath allows path within sandbox`(@TempDir sandbox: File) {
        val result = FileController.validatePath("/sub/file.txt", sandbox.absolutePath)
        assertNotNull(result)
        assertEquals(File(sandbox, "sub/file.txt").canonicalPath, result!!.canonicalPath)
    }

    @Test
    fun `validatePath blocks path traversal with dotdot`(@TempDir sandbox: File) {
        val result = FileController.validatePath("/../../../etc/passwd", sandbox.absolutePath)
        assertNull(result)
    }

    @Test
    fun `validatePath blocks absolute path outside sandbox`(@TempDir sandbox: File) {
        val result = FileController.validatePath("/etc/passwd", sandbox.absolutePath)
        assertNull(result)
    }

    @Test
    fun `validatePath allows root of sandbox`(@TempDir sandbox: File) {
        val result = FileController.validatePath("/", sandbox.absolutePath)
        assertNotNull(result)
        assertEquals(sandbox.canonicalPath, result!!.canonicalPath)
    }

    @Test
    fun `validatePath blocks symlink escape`(@TempDir sandbox: File) {
        // Create symlink inside sandbox pointing outside
        val link = File(sandbox, "escape")
        try {
            java.nio.file.Files.createSymbolicLink(link.toPath(), File("/etc").toPath())
            val result = FileController.validatePath("/escape/passwd", sandbox.absolutePath)
            assertNull(result)
        } catch (e: Exception) {
            // Symlink creation may fail on some systems; skip
        }
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.autodroid.server.controller.FileControllerTest" --info`
Expected: FAIL — validatePath not found

**Step 3: Implement path validation and apply to all endpoints**

In `FileController.kt`, add `validatePath` companion function and refactor all endpoints to use it:

```kotlin
companion object {
    private const val SANDBOX_ROOT = "/sdcard"

    /** Returns canonical File within sandbox, or null if path escapes. */
    fun validatePath(relativePath: String, sandbox: String = SANDBOX_ROOT): File? {
        val resolved = File(sandbox, relativePath.trimStart('/')).canonicalFile
        val sandboxDir = File(sandbox).canonicalFile
        return if (resolved.path.startsWith(sandboxDir.path)) resolved else null
    }
}
```

Each endpoint extracts `path` param, calls `validatePath()`, returns 403 if null.

**Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.autodroid.server.controller.FileControllerTest" --info`
Expected: All 5 tests PASS

**Step 5: Build and verify**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add app/src/main/kotlin/com/autodroid/server/controller/FileController.kt \
       app/src/test/kotlin/com/autodroid/server/controller/FileControllerTest.kt
git commit -m "fix: add path sandboxing to FileController, restrict to /sdcard"
```

---

### Task 3: NodePool — TTL Auto-Eviction & Capacity Limit

**Files:**
- Modify: `app/src/main/kotlin/com/autodroid/adapter/AutomatorAdapter.kt` (NodePool class)
- Test: `app/src/test/kotlin/com/autodroid/adapter/NodePoolTest.kt`

**Context:**
- NodePool uses `ConcurrentHashMap<Int, UiObject>` with `AtomicInteger` counter
- Nodes registered via `register()`, retrieved via `get()`, released via `release()/releaseAll()`
- No TTL, no capacity limit, counter never wraps

**Step 1: Write the failing test**

```kotlin
// app/src/test/kotlin/com/autodroid/adapter/NodePoolTest.kt
package com.autodroid.adapter

import android.view.accessibility.AccessibilityNodeInfo
import com.autodroid.automator.UiObject
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NodePoolTest {

    @BeforeEach
    fun setUp() {
        mockkConstructor(UiObject::class)
        every { anyConstructed<UiObject>().recycle() } just Runs
    }

    private fun mockNode(): UiObject {
        return mockk<UiObject>(relaxed = true)
    }

    @Test
    fun `register and get returns node`() {
        val pool = NodePool(maxSize = 100, ttlMs = 60_000)
        val node = mockNode()
        val handle = pool.register(node)
        assertSame(node, pool.get(handle))
    }

    @Test
    fun `release removes and recycles node`() {
        val pool = NodePool(maxSize = 100, ttlMs = 60_000)
        val node = mockNode()
        val handle = pool.register(node)
        pool.release(handle)
        assertNull(pool.get(handle))
        verify { node.recycle() }
    }

    @Test
    fun `evicts oldest when exceeding max size`() {
        val pool = NodePool(maxSize = 3, ttlMs = 60_000)
        val nodes = (1..4).map { mockNode() }
        val handles = nodes.map { pool.register(it) }

        // First node should be evicted
        assertNull(pool.get(handles[0]))
        verify { nodes[0].recycle() }

        // Last 3 should still exist
        assertNotNull(pool.get(handles[1]))
        assertNotNull(pool.get(handles[2]))
        assertNotNull(pool.get(handles[3]))
    }

    @Test
    fun `size returns current count`() {
        val pool = NodePool(maxSize = 100, ttlMs = 60_000)
        assertEquals(0, pool.size)
        pool.register(mockNode())
        pool.register(mockNode())
        assertEquals(2, pool.size)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.autodroid.adapter.NodePoolTest" --info`
Expected: FAIL — NodePool constructor mismatch or class not accessible

**Step 3: Implement NodePool with TTL and capacity**

Replace NodePool in `AutomatorAdapter.kt`:

```kotlin
internal class NodePool(
    private val maxSize: Int = 500,
    private val ttlMs: Long = 60_000,
) {
    private data class Entry(val node: UiObject, val createdAt: Long = System.currentTimeMillis())

    private val counter = AtomicInteger(0)
    private val pool = ConcurrentHashMap<Int, Entry>()

    val size: Int get() = pool.size

    fun register(node: UiObject): Int {
        evictExpired()
        if (pool.size >= maxSize) evictOldest()
        val handle = counter.incrementAndGet()
        pool[handle] = Entry(node)
        return handle
    }

    fun get(handle: Int): UiObject? {
        val entry = pool[handle] ?: return null
        if (System.currentTimeMillis() - entry.createdAt > ttlMs) {
            pool.remove(handle)?.node?.recycle()
            return null
        }
        return entry.node
    }

    fun release(handle: Int) {
        pool.remove(handle)?.node?.recycle()
    }

    fun releaseAll() {
        pool.values.forEach { it.node.recycle() }
        pool.clear()
    }

    private fun evictExpired() {
        val now = System.currentTimeMillis()
        pool.entries.removeAll { (_, entry) ->
            val expired = now - entry.createdAt > ttlMs
            if (expired) entry.node.recycle()
            expired
        }
    }

    private fun evictOldest() {
        val oldest = pool.entries.minByOrNull { it.value.createdAt } ?: return
        pool.remove(oldest.key)?.node?.recycle()
    }
}
```

Update `AutomatorAdapter` to use `NodePool()` with defaults and change visibility from `private` to `internal`.

**Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.autodroid.adapter.NodePoolTest" --info`
Expected: All 4 tests PASS

**Step 5: Build and verify**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add app/src/main/kotlin/com/autodroid/adapter/AutomatorAdapter.kt \
       app/src/test/kotlin/com/autodroid/adapter/NodePoolTest.kt
git commit -m "fix: add TTL and capacity limit to NodePool, prevent memory leak"
```

---

### Task 4: Integration Verification

**Step 1: Run all unit tests**

Run: `./gradlew :automator:test :app:testDebugUnitTest`
Expected: All tests pass

**Step 2: Build APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Install and verify on device**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.autodroid/.app.MainActivity
```

Verify:
- Dashboard shows API token
- `curl http://<ip>:8080/api/status` → 200 OK (public endpoint)
- `curl http://<ip>:8080/api/ui/dump` → 401 (requires auth)
- `curl -H "Authorization: Bearer <token>" http://<ip>:8080/api/ui/dump` → 200 OK
- `curl -H "Authorization: Bearer <token>" http://<ip>:8080/api/files/list?path=/etc` → 403 (outside sandbox)
- `curl -H "Authorization: Bearer <token>" http://<ip>:8080/api/files/list?path=/` → 200 OK (within /sdcard)

**Step 4: Commit integration verification**

```bash
git commit --allow-empty -m "verify: all critical security fixes tested on device"
```
