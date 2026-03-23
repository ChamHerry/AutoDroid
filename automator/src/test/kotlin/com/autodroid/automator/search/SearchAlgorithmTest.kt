package com.autodroid.automator.search

import com.autodroid.automator.UiObject
import com.autodroid.automator.filter.Filter
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SearchAlgorithmTest {

    /**
     * Build a mock UiObject tree node.
     * @param name used as text property for identification
     * @param children child nodes
     */
    private fun mockNode(name: String, vararg children: UiObject): UiObject = mockk(name) {
        every { text } returns name
        every { childCount } returns children.size
        children.forEachIndexed { index, child ->
            every { child(index) } returns child
        }
        every { recycle() } returns Unit
    }

    private fun matchAll() = Filter { true }
    private fun matchNone() = Filter { false }
    private fun matchByText(target: String) = Filter { it.text?.toString() == target }

    /**
     * Tree structure used in tests:
     *        root
     *       /    \
     *      A      B
     *     / \      \
     *    C   D      E
     */
    private fun buildTree(): Map<String, UiObject> {
        val c = mockNode("C")
        val d = mockNode("D")
        val e = mockNode("E")
        val a = mockNode("A", c, d)
        val b = mockNode("B", e)
        val root = mockNode("root", a, b)
        return mapOf("root" to root, "A" to a, "B" to b, "C" to c, "D" to d, "E" to e)
    }

    @Nested
    inner class DFSTest {
        @Test
        fun `finds all matching nodes in DFS order`() {
            val tree = buildTree()
            val result = DFS.search(tree["root"]!!, matchAll(), Int.MAX_VALUE)
            val names = result.map { it.text.toString() }
            assertEquals(listOf("root", "A", "C", "D", "B", "E"), names)
        }

        @Test
        fun `respects limit`() {
            val tree = buildTree()
            val result = DFS.search(tree["root"]!!, matchAll(), 3)
            assertEquals(3, result.size)
        }

        @Test
        fun `filters nodes correctly`() {
            val tree = buildTree()
            val result = DFS.search(tree["root"]!!, matchByText("C"), Int.MAX_VALUE)
            assertEquals(1, result.size)
            assertEquals("C", result[0].text.toString())
        }

        @Test
        fun `returns empty list when no match`() {
            val tree = buildTree()
            val result = DFS.search(tree["root"]!!, matchByText("Z"), Int.MAX_VALUE)
            assertTrue(result.isEmpty())
        }

        @Test
        fun `recycles non-matching non-root nodes`() {
            val tree = buildTree()
            DFS.search(tree["root"]!!, matchByText("C"), Int.MAX_VALUE)
            // Non-matching, non-root nodes should be recycled
            verify { tree["A"]!!.recycle() }
            verify { tree["D"]!!.recycle() }
            verify { tree["B"]!!.recycle() }
            verify { tree["E"]!!.recycle() }
        }

        @Test
        fun `does not recycle root even if not matched`() {
            val tree = buildTree()
            DFS.search(tree["root"]!!, matchNone(), Int.MAX_VALUE)
            verify(exactly = 0) { tree["root"]!!.recycle() }
        }
    }

    @Nested
    inner class BFSTest {
        @Test
        fun `finds all matching nodes in BFS order`() {
            val tree = buildTree()
            val result = BFS.search(tree["root"]!!, matchAll(), Int.MAX_VALUE)
            val names = result.map { it.text.toString() }
            assertEquals(listOf("root", "A", "B", "C", "D", "E"), names)
        }

        @Test
        fun `respects limit`() {
            val tree = buildTree()
            val result = BFS.search(tree["root"]!!, matchAll(), 2)
            assertEquals(2, result.size)
        }

        @Test
        fun `filters nodes correctly`() {
            val tree = buildTree()
            val result = BFS.search(tree["root"]!!, matchByText("E"), Int.MAX_VALUE)
            assertEquals(1, result.size)
            assertEquals("E", result[0].text.toString())
        }

        @Test
        fun `recycles non-matching non-root nodes`() {
            val tree = buildTree()
            BFS.search(tree["root"]!!, matchByText("E"), Int.MAX_VALUE)
            verify { tree["A"]!!.recycle() }
            verify { tree["B"]!!.recycle() }
            verify { tree["C"]!!.recycle() }
            verify { tree["D"]!!.recycle() }
        }

        @Test
        fun `does not recycle root even if not matched`() {
            val tree = buildTree()
            BFS.search(tree["root"]!!, matchNone(), Int.MAX_VALUE)
            verify(exactly = 0) { tree["root"]!!.recycle() }
        }
    }
}
