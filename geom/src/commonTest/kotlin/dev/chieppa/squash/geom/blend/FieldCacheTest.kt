package dev.chieppa.squash.geom.blend

import dev.chieppa.squash.geom.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** A field asked for twice under one key is fitted once, a field that does not fit is kept as none, and keys stay apart. */
class FieldCacheTest {
    @Test
    fun aKeyIsFittedOnce() {
        val cache = FieldCache()
        var fits = 0
        val field = HermiteField.fit(listOf(Vec3(0f, 0f, 1f), Vec3(0f, 0f, -1f)), listOf(Vec3(0f, 0f, 1f), Vec3(0f, 0f, -1f)))
        val first = cache.of("a") { fits++; field }
        assertSame(first, cache.of("a") { fits++; field })
        assertNull(cache.of("b") { fits++; null })
        assertNull(cache.of("b") { fits++; field })
        assertEquals(2, fits)
    }
}
