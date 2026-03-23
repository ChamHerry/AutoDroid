package com.autodroid.automator

import com.autodroid.automator.filter.TextFilter
import com.autodroid.automator.filter.BooleanFilter
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class UiGlobalSelectorTest {

    private fun mockNode(
        text: String? = null,
        isClickable: Boolean = false,
        children: List<UiObject> = emptyList(),
    ): UiObject = mockk {
        every { this@mockk.text } returns text
        every { this@mockk.isClickable } returns isClickable
        every { childCount } returns children.size
        children.forEachIndexed { i, c -> every { child(i) } returns c }
        every { recycle() } returns Unit
    }

    @Test
    fun `findOf returns all matching nodes`() {
        val target1 = mockNode(text = "OK", isClickable = true)
        val target2 = mockNode(text = "OK", isClickable = true)
        val other = mockNode(text = "Cancel", isClickable = true)
        val root = mockNode(children = listOf(target1, other, target2))

        val selector = UiGlobalSelector()
            .addFilter(TextFilter(TextFilter.Mode.EQUALS, "OK"))

        val result = selector.findOf(root)
        assertEquals(2, result.size)
    }

    @Test
    fun `findOneOf returns first match`() {
        val target = mockNode(text = "OK")
        val root = mockNode(children = listOf(target))

        val selector = UiGlobalSelector()
            .addFilter(TextFilter(TextFilter.Mode.EQUALS, "OK"))

        val result = selector.findOneOf(root)
        assertNotNull(result)
        assertEquals("OK", result!!.text.toString())
    }

    @Test
    fun `findOneOf returns null when no match`() {
        val root = mockNode(text = "Root", children = emptyList())

        val selector = UiGlobalSelector()
            .addFilter(TextFilter(TextFilter.Mode.EQUALS, "NotExist"))

        assertNull(selector.findOneOf(root))
    }

    @Test
    fun `multiple filters use AND logic`() {
        val match = mockNode(text = "OK", isClickable = true)
        val textOnly = mockNode(text = "OK", isClickable = false)
        val root = mockNode(children = listOf(match, textOnly))

        val selector = UiGlobalSelector()
            .addFilter(TextFilter(TextFilter.Mode.EQUALS, "OK"))
            .addFilter(BooleanFilter(BooleanFilter.Property.CLICKABLE, true))

        val result = selector.findOf(root)
        assertEquals(1, result.size)
    }

    @Test
    fun `algorithm switches to BFS`() {
        val child = mockNode(text = "A")
        val root = mockNode(text = "root", children = listOf(child))

        val selector = UiGlobalSelector()
            .algorithm("BFS")
            .addFilter(TextFilter(TextFilter.Mode.EQUALS, "A"))

        val result = selector.findOf(root)
        assertEquals(1, result.size)
    }

    @Test
    fun `findOf respects max parameter`() {
        val children = (1..5).map { mockNode(text = "item") }
        val root = mockNode(children = children)

        val selector = UiGlobalSelector()
            .addFilter(TextFilter(TextFilter.Mode.EQUALS, "item"))

        val result = selector.findOf(root, max = 2)
        assertEquals(2, result.size)
    }
}
