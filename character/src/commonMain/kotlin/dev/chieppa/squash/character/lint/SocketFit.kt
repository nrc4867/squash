package dev.chieppa.squash.character.lint

import dev.chieppa.squash.character.build.AssemblyPart
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.socketsOn
import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.character.model.End
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.geom.Slice
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3

private const val RING_SAMPLES = 16
private const val SECTION_SAMPLES = 48

/** How far outside a ring's section a point may lie, as a share of its half extent, and still count as inside. */
private const val SLACK = 1e-3f

private val PARENT = PartId("parent")
private val CHILD = PartId("child")

/**
 * Whether a part sits far enough inside what it is on: its socket ring, the ring on the socket plane for a part with an open end,
 * else its mount or its axis start, lies inside the parent's runs once sunk. Both parts are built undrawn, as the lint sees them.
 * A closed part's rings overlap its parent on purpose, so only the point it hangs from is read.
 */
object SocketFit {
    /**
     * Whether an undrawn [child] on [parent]'s socket named [socket], as [plan] or [parent] defines it, keeps its socket ring inside
     * [parent]; null when the socket is not there or either part builds nothing.
     */
    fun encloses(parent: PartTemplate, socket: String, child: PartTemplate, plan: BodyPlan?): Boolean? {
        val library = TemplateLibrary(listOf(parent, child), listOfNotNull(plan))
        val spec = socketsOn(plan, parent, emptyMap()).firstOrNull { it.name == socket } ?: return null
        val side = if (spec.pair != null) Side.LEFT else null
        val parts = listOf(
            AssemblyPart(PARENT, parent.ref, null, null, null),
            AssemblyPart(CHILD, child.ref, SocketRef(PARENT, spec.name), null, side),
        )
        val assembled = assembleNow(parts, { PartData() }, plan, library)
        val on = assembled.part(PARENT) ?: return null
        val placed = assembled.part(CHILD) ?: return null
        val ring = socketRing(placed) ?: return null
        return ring.all { inside(on, it) }
    }

    /** The findings for every socket of [templates]' own that names what it takes, each child a template among them. */
    fun lint(templates: List<PartTemplate>): List<Finding> = templates.flatMap { parent ->
        parent.blocks.flatMap { it.sockets() }.flatMap { socket ->
            socket.takes.mapNotNull { id -> templates.filter { it.id == id }.maxByOrNull { it.version } }.mapNotNull { child ->
                if (encloses(parent, socket.name, child, null) == false) {
                    Finding("${parent.id.value}.$.blocks.socket.${socket.name}", "'${child.id.value}' sits with its socket ring outside the part")
                } else {
                    null
                }
            }
        }
    }

    /** The open ring of a part with an open end; the mount of a closed part that has one, else its axis start. */
    private fun socketRing(part: PlacedPart): List<Vec3>? {
        val template = part.template
        val u = when (template.openEnd) {
            End.START -> 0f
            End.END -> 1f
            null -> return listOf(template.mount?.let { part.pointOn(it.u, it.v) ?: return null } ?: part.origin)
        }
        return List(RING_SAMPLES) { part.pointOn(u, it / RING_SAMPLES.toFloat()) ?: return null }
    }

    /** Whether [point], in body space, lies inside one of [part]'s runs; [part] is a root, so its patches sit at its origin unturned. */
    private fun inside(part: PlacedPart, point: Vec3): Boolean {
        val local = point - part.origin
        return part.surface.patches.any { patch -> patch.slices?.let { insideRun(it, local) } == true }
    }

    private fun insideRun(slices: List<Slice>, p: Vec3): Boolean {
        for (i in 0 until slices.size - 1) {
            val a = slices[i]
            val b = slices[i + 1]
            val pastA = (p - a.frame.center) dot a.frame.tangent
            val pastB = (p - b.frame.center) dot b.frame.tangent
            if (pastA < 0f || pastB > 0f) continue
            if (insideRing(if (pastA <= -pastB) a else b, p)) return true
        }
        return false
    }

    private fun insideRing(ring: Slice, p: Vec3): Boolean {
        if (ring.width <= 0f || ring.height <= 0f) return false
        val q = p - ring.frame.center
        val x = (q dot ring.frame.side) / (ring.width / 2f) / (1f + SLACK)
        val y = (q dot ring.frame.up) / (ring.height / 2f) / (1f + SLACK)
        return insidePolygon(List(SECTION_SAMPLES) { ring.section.pointAt(it / SECTION_SAMPLES.toFloat()) }, x, y)
    }

    /** Even-odd test of (x, y) against the closed [polygon]. */
    private fun insidePolygon(polygon: List<Vec2>, x: Float, y: Float): Boolean {
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[j]
            if ((a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x) inside = !inside
            j = i
        }
        return inside
    }
}
