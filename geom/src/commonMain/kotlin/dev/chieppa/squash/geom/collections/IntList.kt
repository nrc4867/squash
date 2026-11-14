package dev.chieppa.squash.geom.collections

/** A growable list of ints held in one array, with no boxing. */
class IntList(capacity: Int = 64) {
    private var data = IntArray(maxOf(capacity, 4))

    var size = 0
        private set

    fun add(value: Int) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = value
    }

    operator fun get(i: Int): Int = data[i]

    fun clear() {
        size = 0
    }

    fun toArray(): IntArray = data.copyOf(size)
}
