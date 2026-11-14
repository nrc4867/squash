package dev.chieppa.squash.geom.collections

/** Below this share of its slots filled, the map grows. */
private const val LOAD = 0.5f

/** A map from longs to non-negative ints by open addressing, with no boxing; [get] gives -1 for a key it does not hold. */
class LongIntMap(expected: Int = 16) {
    private var keys = LongArray(slotsFor(expected))
    private var values = IntArray(keys.size) { -1 }
    private var mask = keys.size - 1

    var size = 0
        private set

    fun get(key: Long): Int {
        var i = slot(key)
        while (true) {
            val v = values[i]
            if (v < 0) return -1
            if (keys[i] == key) return v
            i = (i + 1) and mask
        }
    }

    /** Sets [key] to [value], a non-negative int. */
    fun put(key: Long, value: Int) {
        if (size + 1 > keys.size * LOAD) grow()
        var i = slot(key)
        while (values[i] >= 0 && keys[i] != key) i = (i + 1) and mask
        if (values[i] < 0) size++
        keys[i] = key
        values[i] = value
    }

    private fun slot(key: Long): Int {
        val h = key * -0x61c8864680b583ebL
        return (h xor (h ushr 29)).toInt() and mask
    }

    private fun grow() {
        val oldKeys = keys
        val oldValues = values
        keys = LongArray(oldKeys.size * 2)
        values = IntArray(keys.size) { -1 }
        mask = keys.size - 1
        size = 0
        for (i in oldKeys.indices) if (oldValues[i] >= 0) put(oldKeys[i], oldValues[i])
    }

    private companion object {
        fun slotsFor(expected: Int): Int {
            var n = 16
            while (n * LOAD < expected) n *= 2
            return n
        }
    }
}
