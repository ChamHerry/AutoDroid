package com.autodroid.automator.filter

import com.autodroid.automator.UiObject
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class FilterTest {

    private fun mockNode(
        text: CharSequence? = null,
        contentDescription: CharSequence? = null,
        viewIdResourceName: String? = null,
        className: CharSequence? = null,
        packageName: CharSequence? = null,
        isClickable: Boolean = false,
        isScrollable: Boolean = false,
        isEnabled: Boolean = true,
        isFocusable: Boolean = false,
        isCheckable: Boolean = false,
        isSelected: Boolean = false,
        isEditable: Boolean = false,
    ): UiObject = mockk(relaxUnitFun = true) {
        every { this@mockk.text } returns text
        every { this@mockk.contentDescription } returns contentDescription
        every { this@mockk.viewIdResourceName } returns viewIdResourceName
        every { this@mockk.className } returns className
        every { this@mockk.packageName } returns packageName
        every { this@mockk.isClickable } returns isClickable
        every { this@mockk.isScrollable } returns isScrollable
        every { this@mockk.isEnabled } returns isEnabled
        every { this@mockk.isFocusable } returns isFocusable
        every { this@mockk.isCheckable } returns isCheckable
        every { this@mockk.isSelected } returns isSelected
        every { this@mockk.isEditable } returns isEditable
    }

    @Nested
    inner class TextFilterTest {
        @Test
        fun `EQUALS matches exact text`() {
            val filter = TextFilter(TextFilter.Mode.EQUALS, "Login")
            assertTrue(filter.filter(mockNode(text = "Login")))
            assertFalse(filter.filter(mockNode(text = "Login Button")))
        }

        @Test
        fun `CONTAINS matches substring`() {
            val filter = TextFilter(TextFilter.Mode.CONTAINS, "gin")
            assertTrue(filter.filter(mockNode(text = "Login")))
            assertFalse(filter.filter(mockNode(text = "Submit")))
        }

        @Test
        fun `STARTS_WITH matches prefix`() {
            val filter = TextFilter(TextFilter.Mode.STARTS_WITH, "Log")
            assertTrue(filter.filter(mockNode(text = "Login")))
            assertFalse(filter.filter(mockNode(text = "Submit")))
        }

        @Test
        fun `ENDS_WITH matches suffix`() {
            val filter = TextFilter(TextFilter.Mode.ENDS_WITH, "in")
            assertTrue(filter.filter(mockNode(text = "Login")))
            assertFalse(filter.filter(mockNode(text = "Submit")))
        }

        @Test
        fun `returns false when text is null`() {
            val filter = TextFilter(TextFilter.Mode.EQUALS, "Login")
            assertFalse(filter.filter(mockNode(text = null)))
        }
    }

    @Nested
    inner class DescFilterTest {
        @Test
        fun `EQUALS matches exact description`() {
            val filter = DescFilter(DescFilter.Mode.EQUALS, "Close")
            assertTrue(filter.filter(mockNode(contentDescription = "Close")))
            assertFalse(filter.filter(mockNode(contentDescription = "Close button")))
        }

        @Test
        fun `CONTAINS matches substring`() {
            val filter = DescFilter(DescFilter.Mode.CONTAINS, "Close")
            assertTrue(filter.filter(mockNode(contentDescription = "Close button")))
            assertFalse(filter.filter(mockNode(contentDescription = "Open")))
        }

        @Test
        fun `returns false when description is null`() {
            val filter = DescFilter(DescFilter.Mode.EQUALS, "Close")
            assertFalse(filter.filter(mockNode(contentDescription = null)))
        }
    }

    @Nested
    inner class IdFilterTest {
        @Test
        fun `matches when id contains value`() {
            val filter = IdFilter("btn_login")
            assertTrue(filter.filter(mockNode(viewIdResourceName = "com.example:id/btn_login")))
        }

        @Test
        fun `returns false when id is null`() {
            val filter = IdFilter("btn_login")
            assertFalse(filter.filter(mockNode(viewIdResourceName = null)))
        }

        @Test
        fun `returns false when id does not contain value`() {
            val filter = IdFilter("btn_login")
            assertFalse(filter.filter(mockNode(viewIdResourceName = "com.example:id/btn_submit")))
        }
    }

    @Nested
    inner class ClassNameFilterTest {
        @Test
        fun `matches exact class name`() {
            val filter = ClassNameFilter("android.widget.Button")
            assertTrue(filter.filter(mockNode(className = "android.widget.Button")))
            assertFalse(filter.filter(mockNode(className = "android.widget.TextView")))
        }

        @Test
        fun `returns false when className is null`() {
            val filter = ClassNameFilter("android.widget.Button")
            assertFalse(filter.filter(mockNode(className = null)))
        }
    }

    @Nested
    inner class PackageNameFilterTest {
        @Test
        fun `matches exact package name`() {
            val filter = PackageNameFilter("com.example.app")
            assertTrue(filter.filter(mockNode(packageName = "com.example.app")))
            assertFalse(filter.filter(mockNode(packageName = "com.other.app")))
        }

        @Test
        fun `returns false when packageName is null`() {
            val filter = PackageNameFilter("com.example.app")
            assertFalse(filter.filter(mockNode(packageName = null)))
        }
    }

    @Nested
    inner class BooleanFilterTest {
        @Test
        fun `CLICKABLE filter matches`() {
            val filter = BooleanFilter(BooleanFilter.Property.CLICKABLE, true)
            assertTrue(filter.filter(mockNode(isClickable = true)))
            assertFalse(filter.filter(mockNode(isClickable = false)))
        }

        @Test
        fun `SCROLLABLE filter matches`() {
            val filter = BooleanFilter(BooleanFilter.Property.SCROLLABLE, true)
            assertTrue(filter.filter(mockNode(isScrollable = true)))
            assertFalse(filter.filter(mockNode(isScrollable = false)))
        }

        @Test
        fun `ENABLED filter matches`() {
            val filter = BooleanFilter(BooleanFilter.Property.ENABLED, false)
            assertTrue(filter.filter(mockNode(isEnabled = false)))
            assertFalse(filter.filter(mockNode(isEnabled = true)))
        }

        @Test
        fun `FOCUSABLE filter matches`() {
            val filter = BooleanFilter(BooleanFilter.Property.FOCUSABLE, true)
            assertTrue(filter.filter(mockNode(isFocusable = true)))
        }

        @Test
        fun `CHECKABLE filter matches`() {
            val filter = BooleanFilter(BooleanFilter.Property.CHECKABLE, true)
            assertTrue(filter.filter(mockNode(isCheckable = true)))
        }

        @Test
        fun `SELECTED filter matches`() {
            val filter = BooleanFilter(BooleanFilter.Property.SELECTED, true)
            assertTrue(filter.filter(mockNode(isSelected = true)))
        }

        @Test
        fun `EDITABLE filter matches`() {
            val filter = BooleanFilter(BooleanFilter.Property.EDITABLE, true)
            assertTrue(filter.filter(mockNode(isEditable = true)))
        }
    }

    @Nested
    inner class RegexFilterTest {
        @Test
        fun `TEXT regex matches`() {
            val filter = RegexFilter(RegexFilter.Property.TEXT, "\\d{3}-\\d{4}")
            assertTrue(filter.filter(mockNode(text = "123-4567")))
            assertFalse(filter.filter(mockNode(text = "abc")))
        }

        @Test
        fun `DESC regex matches`() {
            val filter = RegexFilter(RegexFilter.Property.DESC, "Item \\d+")
            assertTrue(filter.filter(mockNode(contentDescription = "Item 42")))
            assertFalse(filter.filter(mockNode(contentDescription = "Thing 42")))
        }

        @Test
        fun `ID regex matches`() {
            val filter = RegexFilter(RegexFilter.Property.ID, "btn_\\w+")
            assertTrue(filter.filter(mockNode(viewIdResourceName = "btn_login")))
            assertFalse(filter.filter(mockNode(viewIdResourceName = "txt_name")))
        }

        @Test
        fun `CLASS_NAME regex matches`() {
            val filter = RegexFilter(RegexFilter.Property.CLASS_NAME, "android\\.widget\\..*")
            assertTrue(filter.filter(mockNode(className = "android.widget.Button")))
            assertFalse(filter.filter(mockNode(className = "android.view.View")))
        }

        @Test
        fun `returns false when property is null`() {
            val filter = RegexFilter(RegexFilter.Property.TEXT, ".*")
            assertFalse(filter.filter(mockNode(text = null)))
        }
    }

    @Nested
    inner class DepthFilterTest {
        @Test
        fun `matches node at correct depth via parent traversal fallback`() {
            val grandparent = mockNode()
            val parent = mockNode()
            val child = mockNode()

            every { child.parent() } returns parent
            every { parent.parent() } returns grandparent
            every { grandparent.parent() } returns null

            val filter = DepthFilter(2)
            assertTrue(filter.filter(child))
        }

        @Test
        fun `does not match node at wrong depth via parent traversal fallback`() {
            val node = mockNode()
            every { node.parent() } returns null

            val filter = DepthFilter(2)
            assertFalse(filter.filter(node))
        }

        @Test
        fun `depth 0 matches root node via parent traversal fallback`() {
            val node = mockNode()
            every { node.parent() } returns null

            val filter = DepthFilter(0)
            assertTrue(filter.filter(node))
        }

        @Test
        fun `matches via depth parameter - O(1) path`() {
            val node = mockNode()
            val filter = DepthFilter(3)
            assertTrue(filter.filter(node, 3))
        }

        @Test
        fun `does not match via depth parameter when depth differs`() {
            val node = mockNode()
            val filter = DepthFilter(3)
            assertFalse(filter.filter(node, 0))
            assertFalse(filter.filter(node, 5))
        }

        @Test
        fun `depth 0 matches via depth parameter`() {
            val node = mockNode()
            val filter = DepthFilter(0)
            assertTrue(filter.filter(node, 0))
            assertFalse(filter.filter(node, 1))
        }
    }

    @Nested
    inner class SelectorTest {
        @Test
        fun `empty selector matches any node`() {
            val selector = Selector()
            assertTrue(selector.filter(mockNode()))
        }

        @Test
        fun `AND logic - all filters must match`() {
            val selector = Selector()
            selector.addFilter(TextFilter(TextFilter.Mode.EQUALS, "Login"))
            selector.addFilter(BooleanFilter(BooleanFilter.Property.CLICKABLE, true))

            assertTrue(selector.filter(mockNode(text = "Login", isClickable = true)))
            assertFalse(selector.filter(mockNode(text = "Login", isClickable = false)))
            assertFalse(selector.filter(mockNode(text = "Submit", isClickable = true)))
        }

        @Test
        fun `propagates depth to child filters`() {
            val selector = Selector()
            selector.addFilter(TextFilter(TextFilter.Mode.EQUALS, "Item"))
            selector.addFilter(DepthFilter(2))

            val node = mockNode(text = "Item")
            // Correct depth -> matches
            assertTrue(selector.filter(node, 2))
            // Wrong depth -> does not match even though text matches
            assertFalse(selector.filter(node, 1))
        }

        @Test
        fun `non-depth filters work via depth-aware path`() {
            val selector = Selector()
            selector.addFilter(TextFilter(TextFilter.Mode.EQUALS, "OK"))

            val node = mockNode(text = "OK")
            // TextFilter ignores depth param, should still match
            assertTrue(selector.filter(node, 5))
        }
    }
}
