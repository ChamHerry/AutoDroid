package com.autodroid.adapter

import com.autodroid.automator.UiObject
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NodePoolTest {

    private fun mockNode(): UiObject = mockk<UiObject>(relaxed = true)

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

    @Test
    fun `releaseAll clears and recycles all`() {
        val pool = NodePool(maxSize = 100, ttlMs = 60_000)
        val nodes = (1..3).map { mockNode() }
        nodes.forEach { pool.register(it) }
        pool.releaseAll()
        assertEquals(0, pool.size)
        nodes.forEach { verify { it.recycle() } }
    }
}
