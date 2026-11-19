package dev.chieppa.squash.geom.field

import dev.chieppa.squash.geom.Vec3
import kotlin.math.ceil

/**
 * This run with rings added between any two lying further apart than [finest] in u, evenly between them, each with its middle, axis, up,
 * radius in each direction and v eased from the two either side, as the run reads between them; itself where no two lie that far apart.
 */
fun SweptSolid.refined(finest: Float): SweptSolid {
    val count = us.size
    val splits = IntArray(count - 1) { i -> maxOf(1, ceil((us[i + 1] - us[i]) / finest).toInt()) }
    if (splits.all { it == 1 }) return this
    val middles = ArrayList<Vec3>()
    val tangents = ArrayList<Vec3>()
    val ups = ArrayList<Vec3>()
    val tables = ArrayList<RingTable>()
    val us = ArrayList<Float>()
    for (i in 0 until count) {
        middles += this.middles[i]
        tangents += this.tangents[i]
        ups += this.ups[i]
        tables += this.tables[i]
        us += this.us[i]
        if (i == count - 1) break
        for (n in 1 until splits[i]) {
            val t = n.toFloat() / splits[i]
            val along = (this.tangents[i] * (1f - t) + this.tangents[i + 1] * t).let { if (it.length > 0f) it.normalized() else this.tangents[i] }
            val up = (this.ups[i] * (1f - t) + this.ups[i + 1] * t).let { it - along * (it dot along) }.let { if (it.length > 0f) it.normalized() else this.ups[i] }
            middles += this.middles[i] * (1f - t) + this.middles[i + 1] * t
            tangents += along
            ups += up
            tables += easedTable(this.tables[i], this.tables[i + 1], t)
            us += this.us[i] + (this.us[i + 1] - this.us[i]) * t
        }
    }
    return sweptOf(middles.toTypedArray(), tangents.toTypedArray(), ups.toTypedArray(), tables.toTypedArray(), us.toFloatArray())
}

/**
 * This run with the radius of ring i in direction k round set to [radii] at i times [RunRings.ANGLES] plus k, each at least 0; its rings'
 * frames, u and v kept, so a point reads the same place on it. A ring closed to a point stays one.
 */
fun SweptSolid.withRadii(radii: FloatArray): SweptSolid {
    require(radii.size == us.size * RING_ANGLES) { "A run of ${us.size} rings takes ${us.size * RING_ANGLES} radii, not ${radii.size}" }
    val tables = Array(us.size) { i ->
        val held = this.tables[i]
        if (held.vs == null) held else RingTable(FloatArray(RING_ANGLES) { k -> maxOf(radii[i * RING_ANGLES + k], 0f) }, held.vs)
    }
    return sweptOf(middles, tangents, ups, tables, us.copyOf())
}

/** The table [t] of the way from [a] to [b]: each radius eased, each v eased round the short way; a ring closed to a point takes the other's v. */
private fun easedTable(a: RingTable, b: RingTable, t: Float): RingTable {
    val radii = FloatArray(RING_ANGLES) { a.radii[it] * (1f - t) + b.radii[it] * t }
    val vs = when {
        a.vs == null -> b.vs?.copyOf()
        b.vs == null -> a.vs.copyOf()
        else -> FloatArray(RING_ANGLES) { RingTables.wrapped(a.vs[it] + RingTables.aroundDifference(a.vs[it], b.vs[it]) * t) }
    }
    return RingTable(radii, vs)
}

/** The run through rings with these frames, tables and u, its box round each ring's middle by its widest radius. */
private fun sweptOf(middles: Array<Vec3>, tangents: Array<Vec3>, ups: Array<Vec3>, tables: Array<RingTable>, us: FloatArray): SweptSolid {
    var low = Vec3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
    var high = Vec3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
    middles.forEachIndexed { i, c ->
        val reach = tables[i].radii.max()
        low = Vec3(minOf(low.x, c.x - reach), minOf(low.y, c.y - reach), minOf(low.z, c.z - reach))
        high = Vec3(maxOf(high.x, c.x + reach), maxOf(high.y, c.y + reach), maxOf(high.z, c.z + reach))
    }
    return SweptSolid(middles, tangents, ups, tables, us, low, high)
}
