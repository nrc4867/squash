package dev.chieppa.squash.geom.collections

/** A growable list of floats held in one array, with no boxing. */
class FloatList(capacity: Int = 64) {
    private var data = FloatArray(maxOf(capacity, 4))

    var size = 0
        private set

    fun add(value: Float) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = value
    }

    operator fun get(i: Int): Float = data[i]

    fun toArray(): FloatArray = data.copyOf(size)
}
