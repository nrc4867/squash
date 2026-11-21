package dev.chieppa.squash.character.hair.fit

/** How many times a pattern search halves its steps before it stops. */
private const val HALVINGS = 6

/**
 * The values within [lower] to [upper] that [cost] finds least, searched from [start] by compass steps: each value in turn is tried a
 * step either way and kept where that costs less; when no step helps, every step is halved, [HALVINGS] times, and the search stops at
 * [budget] costings. Deterministic: the same start and cost give the same values.
 */
fun patternSearch(start: FloatArray, steps: FloatArray, lower: FloatArray, upper: FloatArray, budget: Int, cost: (FloatArray) -> Float): Pair<FloatArray, Float> {
    val x = FloatArray(start.size) { start[it].coerceIn(lower[it], upper[it]) }
    val step = steps.copyOf()
    var best = cost(x)
    var spent = 1
    var halved = 0
    while (halved <= HALVINGS && spent < budget) {
        var moved = false
        for (k in x.indices) {
            if (step[k] == 0f) continue
            for (sign in SIGNS) {
                if (spent >= budget) break
                val was = x[k]
                val next = (was + sign * step[k]).coerceIn(lower[k], upper[k])
                if (next == was) continue
                x[k] = next
                val c = cost(x)
                spent++
                if (c < best) {
                    best = c
                    moved = true
                    break
                }
                x[k] = was
            }
        }
        if (!moved) {
            for (k in step.indices) step[k] *= 0.5f
            halved++
        }
    }
    return x to best
}

private val SIGNS = floatArrayOf(1f, -1f)
