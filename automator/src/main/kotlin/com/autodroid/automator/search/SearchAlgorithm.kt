package com.autodroid.automator.search

import com.autodroid.automator.UiObject
import com.autodroid.automator.filter.Filter
import java.util.ArrayDeque
import java.util.LinkedList

/** Interface for tree search algorithms. */
interface SearchAlgorithm {
    fun search(root: UiObject, filter: Filter, limit: Int): List<UiObject>
}

/** Depth-first search using a stack. */
object DFS : SearchAlgorithm {
    override fun search(root: UiObject, filter: Filter, limit: Int): List<UiObject> {
        val result = mutableListOf<UiObject>()
        // Stack stores (node, depth) pairs to track traversal depth for DepthFilter
        val stack = LinkedList<Pair<UiObject, Int>>()
        stack.push(root to 0)

        while (stack.isNotEmpty() && result.size < limit) {
            val (node, depth) = stack.pop()

            // Push children in reverse order for left-to-right traversal
            for (i in node.childCount - 1 downTo 0) {
                node.child(i)?.let { stack.push(it to depth + 1) }
            }

            if (filter.filter(node, depth)) {
                result.add(node)
            } else if (node !== root) {
                node.recycle()
            }
        }

        // Clean up remaining stack
        stack.forEach { (node, _) -> if (node !== root) node.recycle() }

        return result
    }
}

/** Breadth-first search using a queue. */
object BFS : SearchAlgorithm {
    override fun search(root: UiObject, filter: Filter, limit: Int): List<UiObject> {
        val result = mutableListOf<UiObject>()
        // Queue stores (node, depth) pairs to track traversal depth for DepthFilter
        val queue = ArrayDeque<Pair<UiObject, Int>>()
        queue.add(root to 0)

        while (queue.isNotEmpty() && result.size < limit) {
            val (node, depth) = queue.removeFirst()

            for (i in 0 until node.childCount) {
                node.child(i)?.let { queue.add(it to depth + 1) }
            }

            if (filter.filter(node, depth)) {
                result.add(node)
            } else if (node !== root) {
                node.recycle()
            }
        }

        // Clean up remaining queue
        queue.forEach { (node, _) -> if (node !== root) node.recycle() }

        return result
    }
}
