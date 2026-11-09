package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.ShellInside
import dev.chieppa.squash.geom.Vec3
import kotlin.math.floor

/**
 * What hides faces of a placed part under a garment, kept so a pose decides again: the mesh's [indices] with nothing left out, the
 * vertices hidden wherever the part goes ([fixed], read from u and v), the shells that hide what of this part they cover ([inside], by
 * [ShellInside.covers]), the parts that hide whatever of this part lies inside them ([whole], as a hole is cut), and which vertices
 * are hidden as built ([flags]). A face whose corners are all hidden is left out.
 */
class Hiding(val indices: IntArray, val fixed: BooleanArray, val inside: Set<PartId>, val whole: Set<PartId>, val flags: BooleanArray) {
    /** Every part whose place decides what is hidden. */
    val by: Set<PartId> get() = inside + whole
}

/** Something that hides faces of the parts under it: a shell's inside, or a stretch of one part's own surface by u and v. */
internal sealed interface Hider {
    /** What [part] covers, read by [inside]; with [whole], whatever lies inside it, out of sight or not. */
    class Inside(val part: PartId, val inside: ShellInside, val whole: Boolean = false) : Hider {
        fun hides(point: Vec3): Boolean = if (whole) point in inside else inside.covers(point)
    }

    /** [part]'s own surface from [from] to [to] in u and [fromV] to [toV] round it. */
    class Over(val part: PartId, val from: Float, val to: Float, val fromV: Float, val toV: Float) : Hider {
        fun covers(u: Float, v: Float): Boolean = u in from..to && aroundIn(v, fromV, toV)
    }
}

/** Whether [v] lies from [from] to [to] round a part, reading v round. */
internal fun aroundIn(v: Float, from: Float, to: Float): Boolean {
    val ahead = v - from - floor(v - from)
    return ahead <= to - from
}

/** The inside test of [part]'s swept surface as it stands, in body space, or null when it has none. */
internal fun insideOf(part: PlacedPart): ShellInside? = part.inside

/** What [shell], a garment's branch or cuff, hides: whatever lies inside it the whole way round, else its own part's surface under it. */
internal fun hiderOf(shell: PlacedPart): Hider? {
    val covering = requireNotNull(shell.covering)
    if (!covering.allRound) return Hider.Over(covering.part, covering.from, covering.to, covering.fromV, covering.toV)
    val id = shell.id ?: return null
    return insideOf(shell)?.let { Hider.Inside(id, it) }
}

/** [part], a placed part, as the hider of what it covers, or with [whole] of whatever lies inside it; null when it has no swept surface. */
internal fun insideHider(part: PlacedPart, whole: Boolean = false): Hider? {
    val id = part.id ?: return null
    return insideOf(part)?.let { Hider.Inside(id, it, whole) }
}

/**
 * This part with what [hiders] hide of it left out of its mesh as well as what was hidden before, the grid and every vertex kept so
 * anchors still land; itself when nothing more is hidden.
 */
internal fun PlacedPart.hiddenBy(hiders: List<Hider>): PlacedPart {
    val id = id ?: return this
    val over = hiders.filterIsInstance<Hider.Over>().filter { it.part == id }
    val inside = hiders.filterIsInstance<Hider.Inside>().filter { it.part != id }
    if (over.isEmpty() && inside.isEmpty()) return this
    val held = hiding
    val mesh = surface.mesh
    val fixed = held?.fixed?.copyOf() ?: BooleanArray(mesh.vertexCount)
    val flags = held?.flags?.copyOf() ?: BooleanArray(mesh.vertexCount)
    var any = false
    forEachVertex { vertex, u, v ->
        if (over.any { it.covers(u, v) }) fixed[vertex] = true
        val hidden = fixed[vertex] || flags[vertex] || inside.any { it.hides(origin + mesh.position(vertex)) }
        any = any || (hidden && !flags[vertex])
        flags[vertex] = hidden
    }
    if (!any) return this
    val all = held?.indices ?: mesh.indices
    val (whole, covering) = inside.partition { it.whole }
    val record = Hiding(all, fixed, held?.inside.orEmpty() + covering.map { it.part }, held?.whole.orEmpty() + whole.map { it.part }, flags)
    return withShown(keptFaces(all, flags), record)
}

/** Calls [visit] with each vertex of this part's mesh, its u and its v on its patch. */
private inline fun PlacedPart.forEachVertex(visit: (vertex: Int, u: Float, v: Float) -> Unit) {
    var vertex = 0
    for (patch in surface.patches) for (local in 0 until patch.nu * patch.nv) {
        visit(vertex, patch.ringUs[local / patch.nv], (local % patch.nv).toFloat() / patch.segments)
        vertex++
    }
}

/** The faces of [indices] with a corner [hidden] does not mark. */
private fun keptFaces(indices: IntArray, hidden: BooleanArray): IntArray {
    val kept = IntArray(indices.size)
    var count = 0
    for (t in 0 until indices.size / 3) {
        val a = indices[t * 3]
        val b = indices[t * 3 + 1]
        val c = indices[t * 3 + 2]
        if (hidden[a] && hidden[b] && hidden[c]) continue
        kept[count++] = a
        kept[count++] = b
        kept[count++] = c
    }
    return if (count == kept.size) kept else kept.copyOf(count)
}

private fun PlacedPart.withShown(indices: IntArray, hiding: Hiding): PlacedPart {
    val mesh = RenderMesh(surface.mesh.vertices, indices)
    return showing(PartSurface(surface.patches, surface.mirrored, mesh, surface.silhouettes, surface.box, surface.extents), turn, hiding)
}

/**
 * This character, a pose of [rest], with what the garments hide decided again where they and the parts under them now are: a part
 * hidden inside a garment at rest that the pose carries out of every garment shows again, and a part the pose carries inside one is
 * hidden, as are holes and cuffs where the part through them now lies. A part neither moved nor under anything moved stays the
 * same object, and with nothing changed this is the character itself.
 */
fun AssembledCharacter.hiddenAgain(rest: AssembledCharacter): AssembledCharacter {
    if (this === rest) return this
    val posed = parts.mapNotNull { part -> part.id?.let { it to part } }.toMap()
    val atRest = rest.parts.mapNotNull { part -> part.id?.let { it to part } }.toMap()
    var changed = false
    val shown = parts.map { part ->
        val hiding = part.hiding ?: return@map part
        val id = part.id ?: return@map part
        val carried = atRest[id]?.surface?.patches !== part.surface.patches
        val movedHiders = hiding.by.filter { atRest[it] !== posed[it] }
        if (!carried && movedHiders.isEmpty()) return@map part
        val box = part.box ?: return@map part
        fun testsOf(ids: Collection<PartId>) = ids.mapNotNull { other ->
            posed[other]?.let(::insideOf)?.takeIf { it.reaches(box.low, box.high) }?.let { Hider.Inside(other, it, other in hiding.whole) }
        }
        val tests = testsOf(hiding.by)
        val moving = if (carried) {
            emptyList()
        } else {
            testsOf(movedHiders).map { it.inside } + movedHiders.mapNotNull { other -> atRest[other]?.let(::insideOf) }
        }
        val mesh = part.surface.mesh
        val flags = BooleanArray(mesh.vertexCount) { vertex ->
            if (hiding.fixed[vertex]) return@BooleanArray true
            val point = part.origin + mesh.position(vertex)
            if (!carried && moving.none { it.near(point) }) hiding.flags[vertex] else tests.any { it.hides(point) }
        }
        val kept = keptFaces(hiding.indices, flags)
        if (kept.contentEquals(mesh.indices)) return@map part
        changed = true
        part.withShown(kept, Hiding(hiding.indices, hiding.fixed, hiding.inside, hiding.whole, flags))
    }
    return if (changed) AssembledCharacter(shown, sockets) else this
}
