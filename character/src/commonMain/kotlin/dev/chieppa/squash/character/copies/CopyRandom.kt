package dev.chieppa.squash.character.copies

/** A small seeded source of numbers, the same for a seed on every platform and every run: a SplitMix64 sequence. */
internal class CopyRandom(seed: Int) {
    private var state: Long = seed.toLong() * GOLDEN + OFFSET

    /** The next number from 0 up to, not including, 1. */
    fun next(): Float {
        state += GOLDEN
        var z = state
        z = (z xor (z ushr 30)) * MIX_A
        z = (z xor (z ushr 27)) * MIX_B
        z = z xor (z ushr 31)
        return (z ushr 40).toFloat() / (1L shl 24).toFloat()
    }

    /** The next number from -1 to 1. */
    fun either(): Float = next() * 2f - 1f

    private companion object {
        const val GOLDEN = -0x61c8864680b583ebL
        const val MIX_A = -0x40a7b892e31b1a47L
        const val MIX_B = -0x6b2fb644ecceee15L
        const val OFFSET = 0x2545F4914F6CDD1DL
    }
}
