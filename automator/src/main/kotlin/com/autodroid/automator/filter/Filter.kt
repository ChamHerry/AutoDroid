package com.autodroid.automator.filter

import com.autodroid.automator.UiObject

/** Base filter interface for matching accessibility nodes. */
fun interface Filter {
    fun filter(node: UiObject): Boolean

    /**
     * Overridable version that receives the traversal depth from the search algorithm.
     * Default delegates to [filter] for backward compatibility — existing Filter
     * implementations that do not care about depth need no changes.
     */
    fun filter(node: UiObject, depth: Int): Boolean = filter(node)
}

/** Composite filter: AND logic over multiple filters. */
class Selector : Filter {
    private val filters = mutableListOf<Filter>()

    fun addFilter(filter: Filter) { filters.add(filter) }

    override fun filter(node: UiObject): Boolean = filters.all { it.filter(node) }

    override fun filter(node: UiObject, depth: Int): Boolean = filters.all { it.filter(node, depth) }
}

/** Filter by text property. */
class TextFilter(private val mode: Mode, private val value: String) : Filter {
    enum class Mode { EQUALS, CONTAINS, STARTS_WITH, ENDS_WITH }

    override fun filter(node: UiObject): Boolean {
        val text = node.text?.toString() ?: return false
        return when (mode) {
            Mode.EQUALS -> text == value
            Mode.CONTAINS -> text.contains(value)
            Mode.STARTS_WITH -> text.startsWith(value)
            Mode.ENDS_WITH -> text.endsWith(value)
        }
    }
}

/** Filter by content description. */
class DescFilter(private val mode: Mode, private val value: String) : Filter {
    enum class Mode { EQUALS, CONTAINS }

    override fun filter(node: UiObject): Boolean {
        val desc = node.contentDescription?.toString() ?: return false
        return when (mode) {
            Mode.EQUALS -> desc == value
            Mode.CONTAINS -> desc.contains(value)
        }
    }
}

/** Filter by view ID resource name. */
class IdFilter(private val id: String) : Filter {
    override fun filter(node: UiObject): Boolean =
        node.viewIdResourceName?.contains(id) == true
}

/** Filter by class name. */
class ClassNameFilter(private val className: String) : Filter {
    override fun filter(node: UiObject): Boolean =
        node.className?.toString() == className
}

/** Filter by package name. */
class PackageNameFilter(private val packageName: String) : Filter {
    override fun filter(node: UiObject): Boolean =
        node.packageName?.toString() == packageName
}

/** Filter by boolean properties. */
class BooleanFilter(private val property: Property, private val value: Boolean) : Filter {
    enum class Property { CLICKABLE, SCROLLABLE, ENABLED, FOCUSABLE, CHECKABLE, SELECTED, EDITABLE }

    override fun filter(node: UiObject): Boolean = when (property) {
        Property.CLICKABLE -> node.isClickable == value
        Property.SCROLLABLE -> node.isScrollable == value
        Property.ENABLED -> node.isEnabled == value
        Property.FOCUSABLE -> node.isFocusable == value
        Property.CHECKABLE -> node.isCheckable == value
        Property.SELECTED -> node.isSelected == value
        Property.EDITABLE -> node.isEditable == value
    }
}

/** Filter by regex pattern. */
class RegexFilter(private val property: Property, pattern: String) : Filter {
    enum class Property { TEXT, DESC, ID, CLASS_NAME }

    private val regex = Regex(pattern)

    override fun filter(node: UiObject): Boolean {
        val value = when (property) {
            Property.TEXT -> node.text?.toString()
            Property.DESC -> node.contentDescription?.toString()
            Property.ID -> node.viewIdResourceName
            Property.CLASS_NAME -> node.className?.toString()
        } ?: return false
        return regex.matches(value)
    }
}

/**
 * Filter by node depth in tree.
 *
 * When used inside a [SearchAlgorithm] (DFS/BFS), depth is passed by the algorithm —
 * no Binder IPC needed. The single-arg [filter] overload is kept as a fallback that
 * walks parent pointers (O(D) IPC per call) for callers that don't provide depth.
 */
class DepthFilter(private val depth: Int) : Filter {
    /** Fallback: walks parent pointers. Prefer [filter(node, depth)] from search algorithms. */
    override fun filter(node: UiObject): Boolean {
        var d = 0
        var current = node.parent()
        while (current != null) {
            d++
            val next = current.parent()
            current.recycle()
            current = next
        }
        return d == depth
    }

    /** O(1) — uses the depth counter maintained by the search algorithm. */
    override fun filter(node: UiObject, depth: Int): Boolean = depth == this.depth
}
