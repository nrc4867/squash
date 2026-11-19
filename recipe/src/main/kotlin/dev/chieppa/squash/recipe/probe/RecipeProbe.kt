package dev.chieppa.squash.recipe.probe

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.FaceMap
import dev.chieppa.squash.character.build.JointTree
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.build.socketsOn
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.character.model.SIZE_PARAM
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.covers
import dev.chieppa.squash.geom.positionAt
import dev.chieppa.squash.recipe.RecipeException
import java.util.Locale

/** Steps along u and round v the nearest-point search starts from, and how many times it narrows. */
private const val SEARCH_STEPS = 48
private const val NARROWINGS = 4

/** Where round a part its frame is read. */
private val ROUND = listOf(0f, 0.25f, 0.5f, 0.75f)
private const val MIDDLE_U = 0.5f

/** Points round each ring a face's reach is read from. */
private const val FACE_SAMPLES = 32

/** A place on a part's surface: its part, patch, u and v, and where it lies in body space. */
data class ProbePlace(val part: PartId, val patch: String, val u: Float, val v: Float, val point: Vec3)

/**
 * Answers frame questions about a built character at rest, in body space (x forward, y up, z to the character's right): the u and v at
 * a body point or a place on a head's face, the body point at a u and v, and each part's extent, frame, joints and sockets.
 */
class RecipeProbe(private val character: Character, private val built: AssembledCharacter, private val templates: TemplateFolder, private val plan: BodyPlan?) {
    private val parts: List<PlacedPart> = built.parts.filter { it.id != null }

    private fun part(name: String, path: String): PlacedPart =
        parts.firstOrNull { it.id?.value == name } ?: throw RecipeException(path, "no part \"$name\"; parts: ${parts.mapNotNull { it.id?.value }.joinToString()}")

    /** The place on any part's surface nearest [point], searched over every patch. */
    fun nearest(point: Vec3): ProbePlace? = parts.flatMap { part -> part.surface.patches.map { part to it } }
        .mapNotNull { (part, patch) -> nearestOn(part, patch, point) }
        .minByOrNull { (it.point - point).length }

    private fun nearestOn(part: PlacedPart, patch: Patch, point: Vec3): ProbePlace? {
        val us = patch.ringUs
        if (us.isEmpty()) return null
        var uLow = us.first()
        var uHigh = us.last()
        var vLow = 0f
        var vHigh = 1f
        var best: ProbePlace? = null
        repeat(NARROWINGS) {
            val uStep = (uHigh - uLow) / SEARCH_STEPS
            val vStep = (vHigh - vLow) / SEARCH_STEPS
            for (i in 0..SEARCH_STEPS) for (j in 0..SEARCH_STEPS) {
                val u = uLow + uStep * i
                val v = vLow + vStep * j
                val at = part.origin + part.surface.shown(patch.positionAt(u, v))
                if (best == null || (at - point).length < (best!!.point - point).length) best = ProbePlace(checkNotNull(part.id), patch.name, u, wrap(v), at)
            }
            val found = best ?: return null
            uLow = (found.u - uStep * 2).coerceAtLeast(us.first())
            uHigh = (found.u + uStep * 2).coerceAtMost(us.last())
            vLow = found.v - vStep * 2
            vHigh = found.v + vStep * 2
        }
        return best
    }

    private fun wrap(v: Float) = v - kotlin.math.floor(v)

    /** The body point at [u] and [v] on [name]'s [patch], or its first patch reaching [u]. */
    fun at(name: String, u: Float, v: Float, patch: String? = null): ProbePlace {
        val placed = part(name, "uv")
        val patches = placed.surface.patches
        val on = if (patch != null) patches.firstOrNull { it.name == patch } else patches.firstOrNull { it.name == BODY_PATCH && it.covers(u) } ?: patches.firstOrNull { it.covers(u) }
        val point = on?.let { placed.pointOn(u, v, it.name) } ?: throw RecipeException("uv", "$name has no surface at u $u${patch?.let { " on $it" } ?: ""}")
        return ProbePlace(checkNotNull(placed.id), on.name, u, v, point)
    }

    /** The place on head [name]'s face seen from straight ahead at [x] and [y]: x to the character's left, y up, in its drawn units. */
    fun face(name: String, x: Float, y: Float): ProbePlace {
        val instance = character.part(PartId(name)) ?: throw RecipeException("face", "no part \"$name\"")
        val template = checkNotNull(templates.template(instance.template))
        val map = FaceMap.of(instance.id, template, character.resolvedShape(instance)) ?: throw RecipeException("face", "$name has no face to place on")
        val anchor = map.at(x, y) ?: throw RecipeException("face", "($x, $y) lies off $name's face as drawn")
        return at(name, anchor.u, anchor.v, anchor.patch)
    }

    /** What a part is in body space: its template, side, origin, extent, the way its axis and v run, its joints and sockets. */
    fun describe(name: String): List<String> = describe(part(name, "part"))

    /** [describe] for every part, in the order they were placed. */
    fun describeAll(): List<String> = parts.flatMap(::describe)

    private fun describe(part: PlacedPart): List<String> {
        val id = checkNotNull(part.id).value
        val lines = mutableListOf<String>()
        val box = part.box
        lines += "part $id: ${part.template.id.value} ${part.template.version}${part.side?.let { " ${it.name.lowercase()} side" } ?: ""}, origin ${vec(part.origin)}" +
            (box?.let { ", box ${vec(it.low)} to ${vec(it.high)}" } ?: ", builds nothing")
        val axes = PartBuilder.axes(part.template)
        val along = if (part.side == Side.RIGHT) axes.along.mirrored else axes.along
        val up = if (part.side == Side.RIGHT) axes.up.mirrored else axes.up
        val round = ROUND.joinToString(", ") { v -> "v ${num(v)} ${facing(part, MIDDLE_U, v)}" }
        lines += "  $id frame: u runs ${bodyWords(along)}, Side's +y ${bodyWords(up)}; at u ${num(MIDDLE_U)}: $round"
        if (part.role == Role.HEAD && part.side == null) faceSpan(part)?.let { lines += "  $id face: $it" }
        JointTree(part.template, part.surface.patches.map { it.name }).chain.forEach { joint ->
            val at = part.middleAt(joint.u)?.let(::vec) ?: "-"
            lines += "  $id joint ${joint.name}: u ${num(joint.u)}, hinge ${joint.hinge.name.lowercase()}, limits ${joint.limits?.let { "${num(it.min)} to ${num(it.max)}" } ?: "none"}, at $at"
        }
        socketsOn(plan, part.template, part.placement, part.surface.patches.map { it.name }).forEach { template ->
            val socket = template.copy(
                u = part.placement[PlacementParams.socketU(template.name, part.side)] ?: part.placement[PlacementParams.socketU(template.name)] ?: template.u,
                v = part.placement[PlacementParams.socketV(template.name, part.side)] ?: part.placement[PlacementParams.socketV(template.name)] ?: template.v,
            )
            val v = if (part.side == Side.RIGHT && !part.mirrored) 1f - socket.v else socket.v
            val at = part.pointOn(socket.u, v, socket.patch)
            lines += "  $id socket ${socket.name}: u ${num(socket.u)}, v ${num(socket.v)}, at ${at?.let(::vec) ?: "-"}, faces ${facing(part, socket.u, v, socket.patch)}"
        }
        return lines
    }

    /** How far a head's face reaches seen from straight ahead, in the `face` places' own units, or null when it has no body patch. */
    private fun faceSpan(part: PlacedPart): String? {
        val instance = character.part(checkNotNull(part.id)) ?: return null
        val template = templates.template(instance.template) ?: return null
        val body = part.surface.patches.firstOrNull { it.name == BODY_PATCH } ?: return null
        val scale = character.resolvedShape(instance).params[SIZE_PARAM] ?: template.defaults[SIZE_PARAM] ?: 1f
        val ahead = PartBuilder.facing(template)
        val up = (Vec3.Y - ahead * (Vec3.Y dot ahead)).normalized()
        val left = up cross ahead
        val flat = body.ringUs.flatMap { u -> (0 until FACE_SAMPLES).map { i -> body.positionAt(u, i.toFloat() / FACE_SAMPLES) } }
            .map { (it dot left) / scale to (it dot up) / scale }
        val xs = flat.map { it.first }
        val ys = flat.map { it.second }
        return "x ${num(xs.min())} to ${num(xs.max())} (+x its left), y ${num(ys.min())} to ${num(ys.max())} (up)"
    }

    private fun facing(part: PlacedPart, u: Float, v: Float, patch: String? = null): String {
        val middle = part.middleAt(u, patch) ?: return "-"
        val point = part.pointOn(u, v, patch) ?: return "-"
        val out = point - middle
        return if (out.length == 0f) "-" else bodyWords(out)
    }

    /** A place as one line. */
    fun line(place: ProbePlace): String = "part ${place.part.value} patch ${place.patch} u ${num(place.u)} v ${num(place.v)} at ${vec(place.point)}"

    private fun num(value: Float) = String.format(Locale.ROOT, "%.3f", value).trimEnd('0').trimEnd('.').let { if (it == "-0") "0" else it }

    private fun vec(p: Vec3) = "[${num(p.x)}, ${num(p.y)}, ${num(p.z)}]"
}
