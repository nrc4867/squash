package dev.chieppa.squash.geom.blend

import kotlin.math.abs

/** Below this a pivot counts as zero and the system as singular. */
private const val SINGULAR = 1e-14

/**
 * The solution x of a x = [b], [a] being [n] by [n] in rows, by Gaussian elimination with partial pivoting, or null when [a] is
 * singular. Both arrays are worked on in place.
 */
internal fun solveDense(a: DoubleArray, b: DoubleArray, n: Int): DoubleArray? {
    for (column in 0 until n) {
        var pivot = column
        for (row in column + 1 until n) if (abs(a[row * n + column]) > abs(a[pivot * n + column])) pivot = row
        if (abs(a[pivot * n + column]) < SINGULAR) return null
        if (pivot != column) {
            for (k in 0 until n) {
                val swap = a[column * n + k]
                a[column * n + k] = a[pivot * n + k]
                a[pivot * n + k] = swap
            }
            val swap = b[column]
            b[column] = b[pivot]
            b[pivot] = swap
        }
        val diagonal = a[column * n + column]
        for (row in column + 1 until n) {
            val factor = a[row * n + column] / diagonal
            if (factor == 0.0) continue
            for (k in column until n) a[row * n + k] -= factor * a[column * n + k]
            b[row] -= factor * b[column]
        }
    }
    val x = DoubleArray(n)
    for (row in n - 1 downTo 0) {
        var sum = b[row]
        for (k in row + 1 until n) sum -= a[row * n + k] * x[k]
        x[row] = sum / a[row * n + row]
    }
    return x
}
