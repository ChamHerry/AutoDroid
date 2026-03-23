package com.autodroid.automator

import com.autodroid.automator.filter.Filter
import com.autodroid.automator.filter.Selector
import com.autodroid.automator.search.BFS
import com.autodroid.automator.search.DFS
import com.autodroid.automator.search.SearchAlgorithm

/**
 * Global UI selector with chain-builder pattern.
 * Composes multiple filters and uses DFS/BFS to search the accessibility node tree.
 */
class UiGlobalSelector {

    private val selector = Selector()
    private var algorithm: SearchAlgorithm = DFS

    // ── Filter Configuration ──

    fun addFilter(filter: Filter): UiGlobalSelector {
        selector.addFilter(filter)
        return this
    }

    fun algorithm(name: String): UiGlobalSelector {
        algorithm = when (name.uppercase()) {
            "BFS" -> BFS
            else -> DFS
        }
        return this
    }

    // ── Search Operations ──

    fun findOf(root: UiObject, max: Int = Int.MAX_VALUE): List<UiObject> {
        return algorithm.search(root, selector, max)
    }

    fun findOneOf(root: UiObject): UiObject? {
        val results = algorithm.search(root, selector, 1)
        return results.firstOrNull()
    }
}
