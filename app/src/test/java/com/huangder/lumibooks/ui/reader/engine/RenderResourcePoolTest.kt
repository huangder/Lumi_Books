package com.huangder.lumibooks.ui.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderResourcePoolTest {
    private class Resource(val id: Int)

    @Test
    fun retiredResourceWaitsForLastLease() {
        val disposed = mutableListOf<Int>()
        val registry = RenderResourcePool<Resource> { disposed += it.id }
        val resource = registry.track(Resource(7))
        val first = registry.acquire(resource)!!
        val second = registry.acquire(resource)!!

        registry.retire(resource)
        assertTrue(disposed.isEmpty())
        first.close()
        assertTrue(disposed.isEmpty())
        second.close()

        assertEquals(listOf(7), disposed)
    }

    @Test
    fun leasedResourceCannotBeReusedUntilReleased() {
        val registry = RenderResourcePool<Resource> { }
        val resource = registry.track(Resource(9))
        assertTrue(registry.canReuse(resource))
        val lease = registry.acquire(resource)!!
        assertFalse(registry.canReuse(resource))
        lease.close()
        assertTrue(registry.canReuse(resource))
    }

    @Test
    fun closingLeaseTwiceDisposesOnlyOnce() {
        var disposals = 0
        val registry = RenderResourcePool<Resource> { disposals++ }
        val resource = registry.track(Resource(11))
        val lease = registry.acquire(resource)!!
        registry.retire(resource)

        lease.close()
        lease.close()

        assertEquals(1, disposals)
    }

    @Test
    fun retiringSameResourceTwiceDisposesOnlyOnce() {
        var disposals = 0
        val registry = RenderResourcePool<Resource> { disposals++ }
        val resource = registry.track(Resource(12))

        registry.retire(resource)
        registry.retire(resource)

        assertEquals(1, disposals)
    }

    @Test
    fun destroyDefersLeasedResourceUntilRelease() {
        var disposals = 0
        val registry = RenderResourcePool<Resource> { disposals++ }
        val resource = registry.track(Resource(13))
        val lease = registry.acquire(resource)!!

        registry.destroy()
        assertEquals(0, disposals)
        lease.close()

        assertEquals(1, disposals)
    }
}
