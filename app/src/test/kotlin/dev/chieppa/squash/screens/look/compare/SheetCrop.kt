package dev.chieppa.squash.screens.look.compare

/** A gap narrower than this share of the sheet's width or height, in background, does not part two views. */
private const val GAP_SHARE = 0.01f

/** A piece with fewer covered pixels than this share of the largest piece's is a label or a speck, not a view. */
private const val SMALLEST_SHARE = 0.08f

/**
 * The views of a character sheet: [silhouette] split where whole columns or whole rows of background part what it covers, again inside
 * each piece, until no gap parts a piece, by gaps at least [GAP_SHARE] of the sheet wide; pieces much smaller than the largest dropped.
 * Each view is the box round what it covers, ordered by rows from the top and left to right within a row.
 */
fun sheetViews(silhouette: Silhouette): List<PixelBox> {
    val gap = maxOf(2, (maxOf(silhouette.width, silhouette.height) * GAP_SHARE).toInt())
    val pieces = mutableListOf<Pair<PixelBox, Int>>()
    fun split(box: PixelBox, byColumns: Boolean, tried: Boolean) {
        val runs = runs(silhouette, box, byColumns, gap)
        when {
            runs.isEmpty() -> return
            runs.size > 1 -> runs.forEach { split(it, !byColumns, false) }
            !tried -> split(runs[0], !byColumns, true)
            else -> pieces += runs[0] to covered(silhouette, runs[0])
        }
    }
    split(PixelBox(0, 0, silhouette.width, silhouette.height), byColumns = true, tried = false)
    val largest = pieces.maxOfOrNull { it.second } ?: return emptyList()
    val kept = pieces.filter { it.second >= largest * SMALLEST_SHARE }.map { it.first }
    return kept.sortedWith(compareBy<PixelBox> { box -> kept.count { it.bottom <= box.top } }.thenBy { it.left })
}

/** The covered runs across [box] along columns or rows, each trimmed to what it covers the other way, parted by gaps at least [gap] long. */
private fun runs(s: Silhouette, box: PixelBox, byColumns: Boolean, gap: Int): List<PixelBox> {
    val along = if (byColumns) box.left until box.right else box.top until box.bottom
    val filled = along.map { i -> if (byColumns) (box.top until box.bottom).any { s[i, it] } else (box.left until box.right).any { s[it, i] } }
    val runs = mutableListOf<IntRange>()
    var start = -1
    var lastFilled = -1
    for ((k, i) in along.withIndex()) {
        if (!filled[k]) continue
        if (start < 0) start = i
        else if (i - lastFilled - 1 >= gap) {
            runs += start..lastFilled
            start = i
        }
        lastFilled = i
    }
    if (start >= 0) runs += start..lastFilled
    return runs.map { r ->
        val part = if (byColumns) PixelBox(r.first, box.top, r.last + 1, box.bottom) else PixelBox(box.left, r.first, box.right, r.last + 1)
        trimmed(s, part)
    }
}

/** [box] shrunk to what it covers. */
private fun trimmed(s: Silhouette, box: PixelBox): PixelBox {
    var left = box.right
    var top = box.bottom
    var right = box.left - 1
    var bottom = box.top - 1
    for (y in box.top until box.bottom) for (x in box.left until box.right) {
        if (!s[x, y]) continue
        left = minOf(left, x)
        right = maxOf(right, x)
        top = minOf(top, y)
        bottom = maxOf(bottom, y)
    }
    return PixelBox(left, top, right + 1, bottom + 1)
}

private fun covered(s: Silhouette, box: PixelBox): Int {
    var count = 0
    for (y in box.top until box.bottom) for (x in box.left until box.right) if (s[x, y]) count++
    return count
}
