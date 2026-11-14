package dev.chieppa.squash.geom.surface

import dev.chieppa.squash.geom.Vec3

/**
 * Whether [before], this block as last meshed, placed its vertices exactly as this one would: the same joined field at every grid point,
 * and every member either held by both or able to change the union and hold a share in neither.
 */
internal fun SurfaceBlock.placesAs(before: SurfaceBlock): Boolean {
    if (!corners.contentEquals(before.corners) || !edgeSheets.contentEquals(before.edgeSheets)) return false
    val beforeKeys = before.keys.toSet()
    val nowKeys = keys.toSet()
    for (s in keys.indices) if (keys[s] !in beforeKeys && (relevance[s] or shareRelevance[s]) != 0L) return false
    for (s in before.keys.indices) if (before.keys[s] !in nowKeys && (before.relevance[s] or before.shareRelevance[s]) != 0L) return false
    for (s in keys.indices) {
        val b = before.keys.indexOf(keys[s])
        if (b >= 0 && (relevance[s] != before.relevance[b] || shareRelevance[s] != before.shareRelevance[b])) return false
    }
    return true
}

/**
 * Takes [before]'s placed vertices, which [placesAs] says this block places alike, its members' slots matched by key, and its quads,
 * which the same grid point signs and sheets make the same.
 */
internal fun SurfaceBlock.takePlacement(before: SurfaceBlock) {
    quads = before.quads
    quadCount = before.quadCount
    positions = before.positions
    normals = before.normals
    starts = before.starts
    val slot = IntArray(before.keys.size) { keys.indexOf(before.keys[it]) }
    coordinates = before.coordinates.withSlots(slot)
}

/**
 * Takes vertex [v]'s placing from [before], this block as last meshed, where that placing is the one this block would make from
 * [start]: the same sheets in its cell, the same field at the cell's corners, the same start, and the same members read for it;
 * false, taking nothing, otherwise.
 */
internal fun SurfaceBlock.takeVertex(v: Int, start: Vec3, before: SurfaceBlock): Boolean {
    val cell = vertexCell[v]
    val oldFirst = before.cellFirst[cell]
    if (oldFirst < 0) return false
    for (e in 0 until CellTables.EDGES) if (edgeSheets[cell * CellTables.EDGES + e] != before.edgeSheets[cell * CellTables.EDGES + e]) return false
    val ci = cell % BLOCK
    val cj = (cell / BLOCK) % BLOCK
    val ck = cell / (BLOCK * BLOCK)
    for (c in 0 until CellTables.CORNERS) {
        val corner = BlockGrid.corner(ci + CellTables.cornerX(c), cj + CellTables.cornerY(c), ck + CellTables.cornerZ(c))
        if (corners[corner] != before.corners[corner]) return false
    }
    val old = oldFirst + (v - cellFirst[cell])
    if (before.starts[old * 3] != start.x || before.starts[old * 3 + 1] != start.y || before.starts[old * 3 + 2] != start.z) return false
    val read = VertexPlacement.readFor(this, cell)
    val held = VertexPlacement.heldFor(this, cell, read)
    val oldRead = VertexPlacement.readFor(before, cell)
    val oldHeld = VertexPlacement.heldFor(before, cell, oldRead)
    val slot = IntArray(before.keys.size) { keys.indexOf(before.keys[it]) }
    for (s in before.keys.indices) {
        val now = slot[s]
        if (oldHeld[s] && (now < 0 || !held[now] || read[now] != oldRead[s])) return false
    }
    for (s in keys.indices) if (held[s] && before.keys.indexOf(keys[s]).let { it < 0 || !oldHeld[it] }) return false
    for (c in 0 until 3) {
        positions[v * 3 + c] = before.positions[old * 3 + c]
        normals[v * 3 + c] = before.normals[old * 3 + c]
        starts[v * 3 + c] = before.starts[old * 3 + c]
    }
    coordinates.takeVertex(v, before.coordinates, old, slot)
    return true
}
