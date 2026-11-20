package dev.chieppa.squash.character.hair.fit

import dev.chieppa.squash.document.MOST_OUTLINE_POINTS
import dev.chieppa.squash.geom.Vec2
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

/** How far, in pixels, ink lines between locks are closed over. */
private const val CLOSE_RADIUS = 2

/** How far a simplified outline may stray from the traced one, in pixels, at first. */
private const val SIMPLIFY = 1.5f

/** Holes in the hair smaller than this share of the image are shine and streaks, filled. */
private const val HOLE_SHARE = 0.02f

/** Pieces of hair this share of the largest or more are candidates for the hair over the head, the one reaching highest. */
private const val PIECE_SHARE = 0.25f

/** An image traced for hair: [width] by [height] pixels, [rgb] each one's colour as 0xRRGGBB, rows from the top. */
class TraceImage(val width: Int, val height: Int, val rgb: (Int, Int) -> Int)

/**
 * The outline of the hair in [image]: the pixels within [tolerance] in Lab of one of the [keys] (0xRRGGBB), the ink between locks closed
 * over, the piece reaching highest among the larger ones, its small holes filled, its boundary traced and simplified, and each of its
 * points carried by [place] from the image's pixels into a view's flat frame. Null when no hair is found.
 */
fun traceHairOutline(image: TraceImage, keys: List<Int>, tolerance: Float, place: (Float, Float) -> Vec2): List<Vec2>? {
    val w = image.width
    val h = image.height
    if (w < 3 || h < 3 || keys.isEmpty()) return null
    val labs = keys.map(::labOf)
    var mask = BooleanArray(w * h) { k ->
        val lab = labOf(image.rgb(k % w, k / w))
        labs.any { distance(it, lab) <= tolerance }
    }
    mask = eroded(dilated(mask, w, h, CLOSE_RADIUS), w, h, CLOSE_RADIUS)
    mask = highestPiece(mask, w, h) ?: return null
    mask = holesFilled(mask, w, h, (w * h * HOLE_SHARE).toInt())
    val traced = boundary(mask, w, h)
    if (traced.size < 3) return null
    var tolerancePx = SIMPLIFY
    var simple = simplifiedClosed(traced, tolerancePx)
    while (simple.size > MOST_OUTLINE_POINTS) {
        tolerancePx *= 1.5f
        simple = simplifiedClosed(traced, tolerancePx)
    }
    return simple.map { (x, y) -> place(x + 0.5f, y + 0.5f) }
}

private fun dilated(mask: BooleanArray, w: Int, h: Int, r: Int): BooleanArray = slid(slid(mask, w, h, r, horizontal = true, any = true), w, h, r, horizontal = false, any = true)

private fun eroded(mask: BooleanArray, w: Int, h: Int, r: Int): BooleanArray = slid(slid(mask, w, h, r, horizontal = true, any = false), w, h, r, horizontal = false, any = false)

/** Each pixel set where [any] pixel within [r] along a row or column is set, or where all are when not [any]. */
private fun slid(mask: BooleanArray, w: Int, h: Int, r: Int, horizontal: Boolean, any: Boolean): BooleanArray = BooleanArray(w * h) { k ->
    val x = k % w
    val y = k / w
    var result = !any
    for (d in -r..r) {
        val xx = if (horizontal) x + d else x
        val yy = if (horizontal) y else y + d
        val v = xx in 0 until w && yy in 0 until h && mask[yy * w + xx]
        if (any && v) {
            result = true
            break
        }
        if (!any && !v) {
            result = false
            break
        }
    }
    result
}

/** The 8-connected piece of [mask] reaching highest among those at least [PIECE_SHARE] of the largest, or null when it holds nothing. */
private fun highestPiece(mask: BooleanArray, w: Int, h: Int): BooleanArray? {
    val labels = IntArray(w * h)
    val areas = ArrayList<Int>()
    val tops = ArrayList<Int>()
    val stack = IntArray(w * h)
    for (start in mask.indices) {
        if (!mask[start] || labels[start] != 0) continue
        val label = areas.size + 1
        var top = start / w
        var area = 0
        var n = 0
        stack[n++] = start
        labels[start] = label
        while (n > 0) {
            val k = stack[--n]
            area++
            val x = k % w
            val y = k / w
            if (y < top) top = y
            for (dy in -1..1) for (dx in -1..1) {
                val xx = x + dx
                val yy = y + dy
                if (xx !in 0 until w || yy !in 0 until h) continue
                val kk = yy * w + xx
                if (mask[kk] && labels[kk] == 0) {
                    labels[kk] = label
                    stack[n++] = kk
                }
            }
        }
        areas += area
        tops += top
    }
    if (areas.isEmpty()) return null
    val largest = areas.max()
    val chosen = areas.indices.filter { areas[it] >= largest * PIECE_SHARE }.minBy { tops[it] } + 1
    return BooleanArray(w * h) { labels[it] == chosen }
}

/** [mask] with each hole smaller than [most] pixels filled: the pieces of what it leaves that touch no edge of the image. */
private fun holesFilled(mask: BooleanArray, w: Int, h: Int, most: Int): BooleanArray {
    val out = mask.copyOf()
    val seen = BooleanArray(w * h)
    val stack = IntArray(w * h)
    for (start in mask.indices) {
        if (mask[start] || seen[start]) continue
        val piece = ArrayList<Int>()
        var edge = false
        var n = 0
        stack[n++] = start
        seen[start] = true
        while (n > 0) {
            val k = stack[--n]
            piece += k
            val x = k % w
            val y = k / w
            if (x == 0 || y == 0 || x == w - 1 || y == h - 1) edge = true
            for ((dx, dy) in FOUR) {
                val xx = x + dx
                val yy = y + dy
                if (xx !in 0 until w || yy !in 0 until h) continue
                val kk = yy * w + xx
                if (!mask[kk] && !seen[kk]) {
                    seen[kk] = true
                    stack[n++] = kk
                }
            }
        }
        if (!edge && piece.size < most) piece.forEach { out[it] = true }
    }
    return out
}

/** The outer boundary of [mask]'s only piece, pixel by pixel round it, by following its edge with the pixels round each in turn. */
private fun boundary(mask: BooleanArray, w: Int, h: Int): List<Pair<Float, Float>> {
    val start = mask.indexOfFirst { it }
    if (start < 0) return emptyList()
    val sx = start % w
    val sy = start / w
    fun inside(x: Int, y: Int) = x in 0 until w && y in 0 until h && mask[y * w + x]
    val out = arrayListOf(sx.toFloat() to sy.toFloat())
    var x = sx
    var y = sy
    var from = 0
    var left = 4 * mask.count { it } + 8
    while (left-- > 0) {
        var moved = false
        for (k in 0 until 8) {
            val dir = (from + k) % 8
            val nx = x + AROUND[dir].first
            val ny = y + AROUND[dir].second
            if (!inside(nx, ny)) continue
            x = nx
            y = ny
            from = (dir + 6) % 8
            moved = true
            break
        }
        if (!moved || (x == sx && y == sy)) break
        out += x.toFloat() to y.toFloat()
    }
    return out
}

/** Fewer points round the closed loop [points], none dropped further than [tolerance] from it: split at its point furthest from its first, each half simplified. */
private fun simplifiedClosed(points: List<Pair<Float, Float>>, tolerance: Float): List<Pair<Float, Float>> {
    if (points.size < 4) return points
    val first = points[0]
    val far = points.indices.maxBy { dist(points[it], first) }
    val a = simplifiedOpen(points.subList(0, far + 1), tolerance)
    val b = simplifiedOpen(points.subList(far, points.size) + listOf(first), tolerance)
    return a.dropLast(1) + b.dropLast(1)
}

private fun simplifiedOpen(points: List<Pair<Float, Float>>, tolerance: Float): List<Pair<Float, Float>> {
    if (points.size < 3) return points
    val a = points.first()
    val b = points.last()
    var worst = 0f
    var at = 0
    for (i in 1 until points.size - 1) {
        val d = toSegment(Vec2(points[i].first, points[i].second), Vec2(a.first, a.second), Vec2(b.first, b.second))
        if (d > worst) {
            worst = d
            at = i
        }
    }
    if (worst <= tolerance) return listOf(a, b)
    return simplifiedOpen(points.subList(0, at + 1), tolerance).dropLast(1) + simplifiedOpen(points.subList(at, points.size), tolerance)
}

private fun dist(a: Pair<Float, Float>, b: Pair<Float, Float>): Float {
    val dx = a.first - b.first
    val dy = a.second - b.second
    return sqrt(dx * dx + dy * dy)
}

/** A colour, 0xRRGGBB, in CIE Lab under D65. */
private fun labOf(rgb: Int): FloatArray {
    fun linear(c: Int): Float {
        val v = c / 255f
        return if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
    }
    val r = linear((rgb shr 16) and 0xFF)
    val g = linear((rgb shr 8) and 0xFF)
    val b = linear(rgb and 0xFF)
    val x = (0.4124f * r + 0.3576f * g + 0.1805f * b) / 0.95047f
    val y = 0.2126f * r + 0.7152f * g + 0.0722f * b
    val z = (0.0193f * r + 0.1192f * g + 0.9505f * b) / 1.08883f
    fun f(t: Float) = if (t > 0.008856f) cbrt(t) else 7.787f * t + 16f / 116f
    val fx = f(x)
    val fy = f(y)
    val fz = f(z)
    return floatArrayOf(116f * fy - 16f, 500f * (fx - fy), 200f * (fy - fz))
}

private fun distance(a: FloatArray, b: FloatArray): Float {
    val l = a[0] - b[0]
    val p = a[1] - b[1]
    val q = a[2] - b[2]
    return sqrt(l * l + p * p + q * q)
}

/** The Moore neighbourhood, clockwise from the left on an image whose rows run down. */
private val AROUND = listOf(-1 to 0, -1 to -1, 0 to -1, 1 to -1, 1 to 0, 1 to 1, 0 to 1, -1 to 1)

private val FOUR = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
