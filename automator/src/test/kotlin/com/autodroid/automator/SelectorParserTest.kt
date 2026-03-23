package com.autodroid.automator

import com.autodroid.automator.filter.*
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SelectorParserTest {

    private fun mockNode(
        text: String? = null,
        contentDescription: String? = null,
        viewIdResourceName: String? = null,
        className: String? = null,
        packageName: String? = null,
        isClickable: Boolean = false,
        isScrollable: Boolean = false,
        isEnabled: Boolean = true,
        children: List<UiObject> = emptyList(),
    ): UiObject = mockk {
        every { this@mockk.text } returns text
        every { this@mockk.contentDescription } returns contentDescription
        every { this@mockk.viewIdResourceName } returns viewIdResourceName
        every { this@mockk.className } returns className
        every { this@mockk.packageName } returns packageName
        every { this@mockk.isClickable } returns isClickable
        every { this@mockk.isScrollable } returns isScrollable
        every { this@mockk.isEnabled } returns isEnabled
        every { childCount } returns children.size
        children.forEachIndexed { i, c -> every { child(i) } returns c }
        every { recycle() } returns Unit
    }

    @Test
    fun `parses text filter`() {
        val selector = SelectorParser.parse("""{"text": "Login"}""")
        val match = mockNode(text = "Login")
        val noMatch = mockNode(text = "Submit")
        val root = mockNode(children = listOf(match, noMatch))

        val result = selector.findOf(root)
        assertEquals(1, result.size)
        assertEquals("Login", result[0].text.toString())
    }

    @Test
    fun `parses textContains filter`() {
        val selector = SelectorParser.parse("""{"textContains": "gin"}""")
        val match = mockNode(text = "Login")
        val root = mockNode(children = listOf(match))

        assertEquals(1, selector.findOf(root).size)
    }

    @Test
    fun `parses textStartsWith filter`() {
        val selector = SelectorParser.parse("""{"textStartsWith": "Log"}""")
        val match = mockNode(text = "Login")
        val noMatch = mockNode(text = "Submit")
        val root = mockNode(children = listOf(match, noMatch))

        assertEquals(1, selector.findOf(root).size)
    }

    @Test
    fun `parses textMatches regex filter`() {
        val selector = SelectorParser.parse("""{"textMatches": "\\d+"}""")
        val match = mockNode(text = "123")
        val noMatch = mockNode(text = "abc")
        val root = mockNode(children = listOf(match, noMatch))

        assertEquals(1, selector.findOf(root).size)
    }

    @Test
    fun `parses id filter`() {
        val selector = SelectorParser.parse("""{"id": "btn_login"}""")
        val match = mockNode(viewIdResourceName = "com.example:id/btn_login")
        val root = mockNode(children = listOf(match))

        assertEquals(1, selector.findOf(root).size)
    }

    @Test
    fun `parses className filter`() {
        val selector = SelectorParser.parse("""{"className": "android.widget.Button"}""")
        val match = mockNode(className = "android.widget.Button")
        val noMatch = mockNode(className = "android.widget.TextView")
        val root = mockNode(children = listOf(match, noMatch))

        assertEquals(1, selector.findOf(root).size)
    }

    @Test
    fun `parses desc filter`() {
        val selector = SelectorParser.parse("""{"desc": "Close"}""")
        val match = mockNode(contentDescription = "Close")
        val noMatch = mockNode(contentDescription = "Open")
        val root = mockNode(children = listOf(match, noMatch))

        assertEquals(1, selector.findOf(root).size)
    }

    @Test
    fun `parses descContains filter`() {
        val selector = SelectorParser.parse("""{"descContains": "Clo"}""")
        val match = mockNode(contentDescription = "Close")
        val root = mockNode(children = listOf(match))

        assertEquals(1, selector.findOf(root).size)
    }

    @Test
    fun `parses packageName filter`() {
        val selector = SelectorParser.parse("""{"packageName": "com.example"}""")
        val match = mockNode(packageName = "com.example")
        val root = mockNode(children = listOf(match))

        assertEquals(1, selector.findOf(root).size)
    }

    @Test
    fun `parses boolean filters`() {
        val selector = SelectorParser.parse("""{"clickable": true, "scrollable": false}""")
        val match = mockNode(isClickable = true, isScrollable = false)
        val noMatch = mockNode(isClickable = false, isScrollable = false)
        val root = mockNode(children = listOf(match, noMatch))

        assertEquals(1, selector.findOf(root).size)
    }

    @Test
    fun `parses multiple filters as AND`() {
        val selector = SelectorParser.parse("""{"text": "OK", "clickable": true}""")
        val match = mockNode(text = "OK", isClickable = true)
        val textOnly = mockNode(text = "OK", isClickable = false)
        val root = mockNode(children = listOf(match, textOnly))

        assertEquals(1, selector.findOf(root).size)
    }

    @Test
    fun `parses algorithm option`() {
        val selector = SelectorParser.parse("""{"text": "A", "algorithm": "BFS"}""")
        val child = mockNode(text = "A")
        val root = mockNode(children = listOf(child))

        assertEquals(1, selector.findOf(root).size)
    }

    @Test
    fun `empty JSON produces selector that matches everything`() {
        val selector = SelectorParser.parse("""{}""")
        val node = mockNode(text = "anything")
        val root = mockNode(children = listOf(node))

        // root + child both match
        assertEquals(2, selector.findOf(root).size)
    }
}
