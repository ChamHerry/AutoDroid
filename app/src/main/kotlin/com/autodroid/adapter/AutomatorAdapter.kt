package com.autodroid.adapter

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.autodroid.automator.UiObject
import com.autodroid.automator.SelectorParser
import com.autodroid.service.AccessibilityServiceProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapter bridging Node.js automator calls to Android AccessibilityService.
 *
 * All public methods are suspend functions, designed to be called from
 * the JNI bridge which maps them to N-API Promise resolution.
 */
@Singleton
class AutomatorAdapter @Inject constructor(
    private val serviceProvider: AccessibilityServiceProvider,
    private val gestureFactory: (android.accessibilityservice.AccessibilityService) -> com.autodroid.automator.GlobalActionAutomator
        = { com.autodroid.automator.GlobalActionAutomator(it) },
) {

    private val nodePool = NodePool()

    /** Coroutine scope for background maintenance tasks (node pool cleanup). */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        nodePool.startPeriodicCleanup(scope)
    }

    /** Exposes accessibility service connection status for status endpoint. */
    val isServiceConnected: Boolean get() = serviceProvider.isConnected

    // ── Node Selection ──

    suspend fun findOne(selectorJson: String, timeout: Long): Long? =
        try {
            withTimeout(timeout) {
                val selector = SelectorParser.parse(selectorJson)
                val root = getRoot() ?: return@withTimeout null
                val rootUiObject = UiObject.createRoot(root)
                val node = try {
                    selector.findOneOf(rootUiObject)
                } catch (e: Exception) {
                    rootUiObject.recycle()
                    throw e
                }
                // Search algorithms skip recycling root (if (node !== root) node.recycle()),
                // so we must recycle it here -- unless the result IS the root itself.
                if (node == null || node !== rootUiObject) rootUiObject.recycle()
                node?.let { nodePool.register(it) }
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            null // Timeout expired — return null instead of propagating CancellationException
        }

    suspend fun find(selectorJson: String, max: Int, timeout: Long = 10_000L): List<Long> =
        try {
            withTimeout(timeout) {
                val selector = SelectorParser.parse(selectorJson)
                val root = getRoot() ?: return@withTimeout emptyList()
                val rootUiObject = UiObject.createRoot(root)
                val results = try {
                    selector.findOf(rootUiObject, max)
                } catch (e: Exception) {
                    rootUiObject.recycle()
                    throw e
                }
                // Search algorithms skip recycling root, so we must recycle it here
                // -- unless it was returned as part of the results.
                if (results.none { it === rootUiObject }) rootUiObject.recycle()
                results.map { nodePool.register(it) }
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            emptyList()
        }

    // ── Node Actions ──

    suspend fun click(handle: Long): Boolean {
        val node = nodePool.get(handle) ?: return false
        if (node.isClickable) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        val ancestor = findClickableAncestor(node) ?: return false
        val result = ancestor.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        ancestor.recycle()
        return result
    }

    suspend fun longClick(handle: Long): Boolean {
        val node = nodePool.get(handle) ?: return false
        if (node.isLongClickable) return node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
        val ancestor = findLongClickableAncestor(node) ?: return false
        val result = ancestor.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
        ancestor.recycle()
        return result
    }

    suspend fun setText(handle: Long, text: String): Boolean {
        val node = nodePool.get(handle) ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    suspend fun scrollForward(handle: Long): Boolean {
        val node = nodePool.get(handle) ?: return false
        return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    suspend fun scrollBackward(handle: Long): Boolean {
        val node = nodePool.get(handle) ?: return false
        return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    // ── Node Properties ──

    fun getNodeInfo(handle: Long): JSONObject? {
        val node = nodePool.get(handle) ?: return null
        return uiObjectToJson(node, computeDepth(node))
    }

    private fun computeDepth(node: UiObject): Int {
        var depth = 0
        var current = node.parent()
        while (current != null) {
            depth++
            val next = current.parent()
            current.recycle()
            current = next
        }
        return depth
    }

    // ── Coordinate Actions (via dispatchGesture) ──

    suspend fun clickPoint(x: Int, y: Int): Boolean {
        val automator = getGestureAutomator() ?: return false
        return automator.click(x, y)
    }

    suspend fun longClickPoint(x: Int, y: Int): Boolean {
        val automator = getGestureAutomator() ?: return false
        return automator.longClick(x, y)
    }

    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Long): Boolean {
        val automator = getGestureAutomator() ?: return false
        return automator.swipe(x1, y1, x2, y2, duration)
    }

    suspend fun gesture(delay: Long, duration: Long, points: List<IntArray>): Boolean {
        val automator = getGestureAutomator() ?: return false
        return automator.gesture(delay, duration, points)
    }

    // ── Global Actions ──

    suspend fun back(): Boolean = performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
    suspend fun home(): Boolean = performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
    suspend fun recents(): Boolean = performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)
    suspend fun notifications(): Boolean = performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)

    // ── UI Tree Dump ──

    /**
     * Lightweight snapshot of an AccessibilityNodeInfo's properties.
     * Extracted on Main thread (where AccessibilityNodeInfo access is required),
     * then serialized to JSON on a background thread.
     */
    private data class NodeSnapshot(
        val text: String?,
        val desc: String?,
        val id: String?,
        val className: String?,
        val packageName: String?,
        val clickable: Boolean,
        val longClickable: Boolean,
        val scrollable: Boolean,
        val enabled: Boolean,
        val checked: Boolean,
        val focused: Boolean,
        val selected: Boolean,
        val editable: Boolean,
        val visibleToUser: Boolean,
        val depth: Int,
        val indexInParent: Int,
        val drawingOrder: Int,
        val childCount: Int,
        val boundsInScreen: android.graphics.Rect,
        val boundsInParent: android.graphics.Rect,
        val children: List<NodeSnapshot> = emptyList(),
        // Window metadata (only set for window root nodes in multi-window dump)
        val windowType: String? = null,
        val windowLayer: Int? = null,
        val windowTitle: String? = null,
    )

    /**
     * Dump the full accessibility tree (all windows) as a JSON object.
     * Phase 1 (Main): extract node data into lightweight snapshots.
     * Phase 2 (Default): serialize snapshots to JSON.
     * Returns null if the accessibility service is not connected or no windows are available.
     */
    suspend fun dumpUiTree(): String? = withTimeout(10_000L) {
        // Phase 1: extract snapshots on Main thread (AccessibilityNodeInfo requires it)
        val snapshot = withContext(Dispatchers.Main) { extractTreeSnapshot() }
            ?: return@withTimeout null
        // Phase 2: serialize directly to JSON string on a background thread,
        // bypassing intermediate JSONObject tree to reduce peak memory.
        withContext(Dispatchers.Default) {
            snapshotToJsonString(snapshot)
        }
    }

    /**
     * Extract the full accessibility tree into lightweight [NodeSnapshot] objects.
     * Must be called on Main thread.
     */
    private fun extractTreeSnapshot(): NodeSnapshot? {
        if (!serviceProvider.isConnected) return null

        val windows = serviceProvider.getWindows()
        if (windows.isNullOrEmpty()) {
            val root = serviceProvider.getRootInActiveWindow() ?: return null
            val snapshot = extractNodeSnapshot(root)
            root.recycle()
            return snapshot
        }

        // Multi-window: compute screen size from union of all window bounds
        var screenW = 0
        var screenH = 0
        for (window in windows) {
            val rect = android.graphics.Rect()
            window.getBoundsInScreen(rect)
            if (rect.right > screenW) screenW = rect.right
            if (rect.bottom > screenH) screenH = rect.bottom
        }

        val windowSnapshots = mutableListOf<NodeSnapshot>()
        for (window in windows) {
            val root = window.root ?: continue
            val windowSnapshot = extractNodeSnapshot(root).copy(
                windowType = when (window.type) {
                    AccessibilityWindowInfo.TYPE_APPLICATION -> "application"
                    AccessibilityWindowInfo.TYPE_SYSTEM -> "system"
                    AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "input_method"
                    AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "overlay"
                    else -> "unknown(${window.type})"
                },
                windowLayer = window.layer,
                windowTitle = window.title?.toString(),
            )
            windowSnapshots.add(windowSnapshot)
            root.recycle()
        }

        val fullScreenBounds = android.graphics.Rect(0, 0, screenW, screenH)
        return NodeSnapshot(
            text = null, desc = null, id = null,
            className = "RootWindows", packageName = null,
            clickable = false, longClickable = false, scrollable = false,
            enabled = true, checked = false, focused = false,
            selected = false, editable = false, visibleToUser = true,
            depth = -1, indexInParent = -1, drawingOrder = 0,
            childCount = windows.size,
            boundsInScreen = fullScreenBounds, boundsInParent = fullScreenBounds,
            children = windowSnapshots,
        )
    }

    /**
     * Recursively extract a node and its subtree into a [NodeSnapshot].
     * Obeys [MAX_DUMP_DEPTH] and [MAX_DUMP_NODES] limits.
     */
    private fun extractNodeSnapshot(
        node: AccessibilityNodeInfo,
        depth: Int = 0,
        indexInParent: Int = -1,
        nodeCount: IntArray = intArrayOf(0),
    ): NodeSnapshot {
        nodeCount[0]++
        val screenRect = android.graphics.Rect()
        node.getBoundsInScreen(screenRect)
        val parentRect = android.graphics.Rect()
        node.getBoundsInParent(parentRect)

        val childSnapshots = mutableListOf<NodeSnapshot>()
        if (depth < MAX_DUMP_DEPTH && nodeCount[0] < MAX_DUMP_NODES) {
            for (i in 0 until node.childCount) {
                if (nodeCount[0] >= MAX_DUMP_NODES) break
                val child = node.getChild(i) ?: continue
                childSnapshots.add(extractNodeSnapshot(child, depth + 1, i, nodeCount))
                child.recycle()
            }
        }

        return NodeSnapshot(
            text = node.text?.toString(),
            desc = node.contentDescription?.toString(),
            id = node.viewIdResourceName,
            className = node.className?.toString(),
            packageName = node.packageName?.toString(),
            clickable = node.isClickable,
            longClickable = node.isLongClickable,
            scrollable = node.isScrollable,
            enabled = node.isEnabled,
            checked = node.isChecked,
            focused = node.isFocused,
            selected = node.isSelected,
            editable = node.isEditable,
            visibleToUser = node.isVisibleToUser,
            depth = depth,
            indexInParent = indexInParent,
            drawingOrder = node.drawingOrder,
            childCount = node.childCount,
            boundsInScreen = screenRect,
            boundsInParent = parentRect,
            children = childSnapshots,
        )
    }

    /**
     * Convert a [NodeSnapshot] tree directly to a JSON string using StringBuilder,
     * bypassing the intermediate JSONObject layer to reduce peak memory usage.
     * The snapshot tree can be GC'd as each node is serialized.
     */
    private fun snapshotToJsonString(snapshot: NodeSnapshot): String {
        val sb = StringBuilder(8192)
        writeNodeJson(sb, snapshot)
        return sb.toString()
    }

    /** Recursively write a [NodeSnapshot] as JSON into the given [StringBuilder]. */
    private fun writeNodeJson(sb: StringBuilder, node: NodeSnapshot) {
        sb.append('{')
        appendJsonString(sb, "text", node.text); sb.append(',')
        appendJsonString(sb, "desc", node.desc); sb.append(',')
        appendJsonString(sb, "id", node.id); sb.append(',')
        appendJsonString(sb, "className", node.className); sb.append(',')
        appendJsonString(sb, "packageName", node.packageName); sb.append(',')
        appendJsonBool(sb, "clickable", node.clickable); sb.append(',')
        appendJsonBool(sb, "longClickable", node.longClickable); sb.append(',')
        appendJsonBool(sb, "scrollable", node.scrollable); sb.append(',')
        appendJsonBool(sb, "enabled", node.enabled); sb.append(',')
        appendJsonBool(sb, "checked", node.checked); sb.append(',')
        appendJsonBool(sb, "focused", node.focused); sb.append(',')
        appendJsonBool(sb, "selected", node.selected); sb.append(',')
        appendJsonBool(sb, "editable", node.editable); sb.append(',')
        appendJsonBool(sb, "visibleToUser", node.visibleToUser); sb.append(',')
        appendJsonInt(sb, "depth", node.depth); sb.append(',')
        appendJsonInt(sb, "indexInParent", node.indexInParent); sb.append(',')
        appendJsonInt(sb, "drawingOrder", node.drawingOrder); sb.append(',')
        appendJsonInt(sb, "childCount", node.childCount); sb.append(',')
        appendJsonBounds(sb, "bounds", node.boundsInScreen); sb.append(',')
        appendJsonBounds(sb, "boundsInParent", node.boundsInParent)

        // Window metadata (only present for window root snapshots)
        if (node.windowType != null) {
            sb.append(',')
            appendJsonString(sb, "windowType", node.windowType); sb.append(',')
            appendJsonInt(sb, "windowLayer", node.windowLayer ?: 0); sb.append(',')
            appendJsonString(sb, "windowTitle", node.windowTitle)
        }

        if (node.children.isNotEmpty()) {
            sb.append(",\"children\":[")
            for (i in node.children.indices) {
                if (i > 0) sb.append(',')
                writeNodeJson(sb, node.children[i])
            }
            sb.append(']')
        }

        sb.append('}')
    }

    // ── StringBuilder JSON helpers ──

    private fun appendJsonString(sb: StringBuilder, key: String, value: String?) {
        sb.append('"').append(key).append("\":")
        if (value == null) {
            sb.append("null")
        } else {
            sb.append('"').append(escapeJsonString(value)).append('"')
        }
    }

    private fun appendJsonBool(sb: StringBuilder, key: String, value: Boolean) {
        sb.append('"').append(key).append("\":").append(value)
    }

    private fun appendJsonInt(sb: StringBuilder, key: String, value: Int) {
        sb.append('"').append(key).append("\":").append(value)
    }

    private fun appendJsonBounds(sb: StringBuilder, key: String, rect: android.graphics.Rect) {
        sb.append('"').append(key).append("\":{")
        sb.append("\"left\":").append(rect.left).append(',')
        sb.append("\"top\":").append(rect.top).append(',')
        sb.append("\"right\":").append(rect.right).append(',')
        sb.append("\"bottom\":").append(rect.bottom)
        sb.append('}')
    }

    /** Escape special characters in a JSON string value. */
    private fun escapeJsonString(s: String): String {
        var hasSpecial = false
        for (c in s) {
            if (c == '"' || c == '\\' || c == '\n' || c == '\r' || c == '\t' || c < ' ') {
                hasSpecial = true
                break
            }
        }
        if (!hasSpecial) return s
        val sb = StringBuilder(s.length + 16)
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append("\\u").append(String.format("%04x", c.code)) else sb.append(c)
            }
        }
        return sb.toString()
    }

    // ── Lifecycle ──

    /** Release all pooled nodes, stop background cleanup, and cancel the coroutine scope. */
    fun shutdown() {
        nodePool.releaseAll()
        nodePool.stopPeriodicCleanup()
        scope.cancel()
    }

    fun releaseNode(handle: Long) = nodePool.release(handle)
    fun releaseAllNodes() = nodePool.releaseAll()

    // ── Internal ──

    private fun findAncestor(node: UiObject, predicate: (UiObject) -> Boolean): UiObject? {
        var current = node.parent()
        repeat(20) {
            current ?: return null
            if (predicate(current)) return current
            val next = current.parent()
            current.recycle()
            current = next
        }
        current?.recycle()
        return null
    }

    private fun findClickableAncestor(node: UiObject): UiObject? =
        findAncestor(node) { it.isClickable }

    private fun findLongClickableAncestor(node: UiObject): UiObject? =
        findAncestor(node) { it.isLongClickable }

    /**
     * Limits for tree dump to prevent excessive traversal.
     */
    private companion object {
        const val MAX_DUMP_DEPTH = 50
        const val MAX_DUMP_NODES = 5000
    }

    // ── Node Serialization (single source of truth for field names/order) ──

    private fun boundsToJson(rect: android.graphics.Rect): JSONObject = JSONObject().apply {
        put("left", rect.left); put("top", rect.top)
        put("right", rect.right); put("bottom", rect.bottom)
    }

    /**
     * Shared node serialization. All JSON field names and order are defined once here.
     * Callers supply property values via the [props] accessor to decouple from the
     * concrete node type (AccessibilityNodeInfo vs UiObject vs virtual root defaults).
     */
    private fun buildNodeJson(props: NodeProps): JSONObject = JSONObject().apply {
        put("text", props.text ?: JSONObject.NULL)
        put("desc", props.desc ?: JSONObject.NULL)
        put("id", props.id ?: JSONObject.NULL)
        put("className", props.className ?: JSONObject.NULL)
        put("packageName", props.packageName ?: JSONObject.NULL)
        put("clickable", props.clickable)
        put("longClickable", props.longClickable)
        put("scrollable", props.scrollable)
        put("enabled", props.enabled)
        put("checked", props.checked)
        put("focused", props.focused)
        put("selected", props.selected)
        put("editable", props.editable)
        put("visibleToUser", props.visibleToUser)
        put("depth", props.depth)
        if (props.indexInParent != null) put("indexInParent", props.indexInParent)
        if (props.drawingOrder != null) put("drawingOrder", props.drawingOrder)
        put("childCount", props.childCount)
        put("bounds", boundsToJson(props.bounds))
        put("boundsInParent", boundsToJson(props.boundsInParent))
    }

    /**
     * Value holder for node properties, used by [buildNodeJson] for single-node queries.
     *
     * [NodeSnapshot] has overlapping fields but serves a different purpose: it captures
     * the full tree structure (with children, window metadata) for dumpUiTree serialization
     * via StringBuilder. NodeProps is used only for individual node property queries
     * (getNodeInfo) where a JSONObject is needed for the response.
     */
    private data class NodeProps(
        val text: String?,
        val desc: String?,
        val id: String?,
        val className: String?,
        val packageName: String?,
        val clickable: Boolean,
        val longClickable: Boolean,
        val scrollable: Boolean,
        val enabled: Boolean,
        val checked: Boolean,
        val focused: Boolean,
        val selected: Boolean,
        val editable: Boolean,
        val visibleToUser: Boolean,
        val depth: Int,
        val indexInParent: Int? = null,
        val drawingOrder: Int? = null,
        val childCount: Int,
        val bounds: android.graphics.Rect,
        val boundsInParent: android.graphics.Rect,
    )

    /**
     * Serialize a [UiObject] to a JSONObject with all standard node properties.
     * Used by [getNodeInfo] for single-node property queries.
     *
     * Note: UiObject does not expose drawingOrder or indexInParent,
     * so these fields are omitted (consistent with the original getNodeInfo contract).
     */
    private fun uiObjectToJson(node: UiObject, depth: Int): JSONObject =
        buildNodeJson(NodeProps(
            text = node.text?.toString(),
            desc = node.contentDescription?.toString(),
            id = node.viewIdResourceName,
            className = node.className?.toString(),
            packageName = node.packageName?.toString(),
            clickable = node.isClickable,
            longClickable = node.isLongClickable,
            scrollable = node.isScrollable,
            enabled = node.isEnabled,
            checked = node.isChecked,
            focused = node.isFocused,
            selected = node.isSelected,
            editable = node.isEditable,
            visibleToUser = node.isVisibleToUser,
            depth = depth,
            childCount = node.childCount,
            bounds = node.boundsInScreen(),
            boundsInParent = node.boundsInParent(),
        ))

    private fun getRoot(): AccessibilityNodeInfo? =
        serviceProvider.get()?.rootInActiveWindow

    /** Obtain a gesture automator for the current accessibility service. */
    private fun getGestureAutomator(): com.autodroid.automator.GlobalActionAutomator? {
        val service = serviceProvider.get() ?: return null
        return gestureFactory(service)
    }

    private suspend fun performGlobalAction(action: Int): Boolean =
        withContext(Dispatchers.Main) {
            serviceProvider.get()?.performGlobalAction(action) ?: false
        }
}

/**
 * Pool for managing AccessibilityNodeInfo handles.
 * JS side references nodes by integer handles, not direct objects.
 *
 * Entries expire after [ttlMs] milliseconds. When the pool reaches [maxSize],
 * the oldest entry is evicted to make room for a new one.
 */
internal class NodePool(
    private val maxSize: Int = 500,
    private val ttlMs: Long = 60_000,
    private val cleanupIntervalMs: Long = 30_000,
) {
    private data class Entry(val node: UiObject, val createdAt: Long = System.currentTimeMillis())

    private val counter = AtomicLong(0)
    private val pool = HashMap<Long, Entry>()
    private val lock = Any()
    private var cleanupJob: Job? = null

    val size: Int get() = synchronized(lock) { pool.size }

    /**
     * Start a coroutine that periodically evicts expired entries.
     * Prevents stale node accumulation when no new registrations occur.
     */
    fun startPeriodicCleanup(scope: CoroutineScope) {
        cleanupJob?.cancel()
        cleanupJob = scope.launch {
            while (true) {
                delay(cleanupIntervalMs)
                synchronized(lock) { evictExpired() }
            }
        }
    }

    /** Stop the periodic cleanup coroutine. */
    fun stopPeriodicCleanup() {
        cleanupJob?.cancel()
        cleanupJob = null
    }

    fun register(node: UiObject): Long = synchronized(lock) {
        evictExpired()
        if (pool.size >= maxSize) evictOldest()
        val handle = counter.incrementAndGet()
        pool[handle] = Entry(node)
        handle
    }

    fun get(handle: Long): UiObject? = synchronized(lock) {
        val entry = pool[handle] ?: return null
        if (System.currentTimeMillis() - entry.createdAt > ttlMs) {
            pool.remove(handle)?.node?.recycle()
            return null
        }
        // Refresh timestamp to prevent eviction while the node is in active use
        pool[handle] = entry.copy(createdAt = System.currentTimeMillis())
        entry.node
    }

    fun release(handle: Long) = synchronized(lock) {
        pool.remove(handle)?.node?.recycle()
    }

    fun releaseAll() = synchronized(lock) {
        pool.values.forEach { it.node.recycle() }
        pool.clear()
    }

    private fun evictExpired() {
        val now = System.currentTimeMillis()
        val iter = pool.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (now - entry.value.createdAt > ttlMs) {
                entry.value.node.recycle()
                iter.remove()
            }
        }
    }

    private fun evictOldest() {
        val oldest = pool.entries.minByOrNull { it.value.createdAt } ?: return
        pool.remove(oldest.key)?.node?.recycle()
    }
}
