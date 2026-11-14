package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.ResolvedMark
import dev.chieppa.squash.character.build.ResolvedPoint
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.EyeLook
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkWidth
import dev.chieppa.squash.document.WidthStop
import dev.chieppa.squash.geom.SurfaceUv
import dev.chieppa.squash.document.PaintedEye
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.geom.ColorShape
import dev.chieppa.squash.geom.UvShapeDistance
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.math.tan

/** The lid line's weight from its back corner over the top and under the bottom: thickest over the top. */
private val LID_WIDTH = MarkWidth(listOf(WidthStop(0f, 0.5f), WidthStop(0.25f, 1.25f), WidthStop(0.5f, 0.5f), WidthStop(0.75f, 0.35f), WidthStop(1f, 0.5f)))

/** How far along the head's u and v the eye's chart is read either side of its middle to find which way they run. */
private const val CHART_STEP = 0.01f

/** How far from its middle an eye paints at most, as a multiple of its opening's width. */
private const val EYE_REACH = 4f

/** How far toward the eye's facing a vertex's normal must turn, as a cosine, to take its paint. */
private const val LEAST_FACING = 0.2f

private const val TINY = 1e-9f

/**
 * Eyes painted on a one-surface head. Each eye's middle is its anchor resolved on the field, and its facing the surface's normal there
 * turned outward by its look-out. Every shape of its [EyeShapes] lies in the plane square to that facing, its chart running along the
 * head's u toward the front and against its v toward the top, scaled by how far the surface runs per unit of the head's u and v there; each vertex of the surface near the
 * eye that faces it is projected along the facing into that plane and painted where it falls inside. So the paint foreshortens and wraps
 * as the head turns, and never stands past the head's outline. A mirrored eye is painted again with its anchor's v read as 1 - v.
 */
object SurfaceEyes {
    /**
     * [eyes] painted on [surface], each eye's layers in order, white, iris, pupil and highlight, and its lid, lashes and brow as the head's
     * ink marks, in its look and its front look; each side of each look is one piece of [runAll]'s work.
     */
    suspend fun of(surface: CharacterSurface, eyes: List<PaintedEye>, runAll: RunAll = IN_TURN): SurfacePaint {
        if (eyes.isEmpty()) return SurfacePaint.NONE
        val chart = surface.chart
        val anchors = FieldAnchors(surface.field)
        val placed = eyes.map { eye -> placeOf(chart, anchors, eye) }
        val looks = eyes.map { eye -> listOfNotNull(eye.look, eye.front) }
        val painted = eyes.indices.map { e -> looks[e].map { arrayOfNulls<SurfacePaint>(sidesOf(eyes[e]).size) } }
        runAll(
            eyes.indices.filter { placed[it] != null }.flatMap { e ->
                looks[e].indices.flatMap { l ->
                    sidesOf(eyes[e]).mapIndexed { side, anchor -> { painted[e][l][side] = paintOne(chart, anchors, eyes[e].id, looks[e][l], anchor, side) } }
                }
            },
        )
        return SurfacePaint(
            emptyList(),
            eyes = eyes.indices.mapNotNull { e ->
                val (middle, facing) = placed[e] ?: return@mapNotNull null
                val versions = painted[e].map { sides -> sides.filterNotNull().fold(SurfacePaint.NONE) { all, one -> all + one } }
                EyeVersions(versions[0], versions.getOrNull(1), facing, middle, eyes[e].anchor.copy(offset = 0f, v = 0.5f))
            },
        )
    }

    /** Both sides' anchors of [eye], its own and, mirrored, the other. */
    private fun sidesOf(eye: PaintedEye): List<Anchor> = if (eye.mirrored) listOf(eye.anchor, eye.anchor.copy(v = 1f - eye.anchor.v)) else listOf(eye.anchor)

    /** Where [eye]'s head sits between its eyes and the way it faces; null where its anchor does not resolve. */
    private fun placeOf(chart: SurfaceChart, anchors: FieldAnchors, eye: PaintedEye): Pair<Vec3, Vec3>? {
        val middle = anchors.resolve(eye.anchor.copy(offset = 0f, v = 0.5f)) ?: anchors.resolve(eye.anchor.copy(offset = 0f)) ?: return null
        val head = chart.surface.field.runs.firstOrNull { it.partId == eye.anchor.part }?.part ?: return null
        return middle.position to head.turn * PartBuilder.facing(head.template)
    }

    private fun paintOne(chart: SurfaceChart, anchors: FieldAnchors, id: String, look: EyeLook, anchor: Anchor, side: Int): SurfacePaint {
        val mirrored = side > 0
        val surface = chart.surface
        val middle = anchors.resolve(anchor.copy(offset = 0f)) ?: return SurfacePaint.NONE
        val run = surface.field.runs.indexOfFirst { it.partId == anchor.part && it.patch.name == anchor.patch }
        if (run < 0) return SurfacePaint.NONE
        val members = surface.field.runs.indices.filter { surface.field.runs[it].partId == anchor.part }
        val forward = span(anchors, anchor, CHART_STEP, 0f) ?: return SurfacePaint.NONE
        val down = span(anchors, anchor, 0f, if (mirrored) -CHART_STEP else CHART_STEP) ?: return SurfacePaint.NONE
        val along = forward.length / (2f * CHART_STEP)
        val around = down.length / (2f * CHART_STEP)
        val facing = turnedOut(middle.normal, middle.position, look.lookOut)
        val up = flattened(down * -1f, facing) ?: return SurfacePaint.NONE
        val across = flattened(forward - up * (forward dot up), facing) ?: return SurfacePaint.NONE
        val shapes = EyeShapes(look)
        val reach = EYE_REACH * look.width * along
        val mesh = surface.mesh
        val rim = (shapes.opening as ColorShape.Border).points.maxOf { sqrt((it.u * along).let { a -> a * a } + (it.v * around).let { b -> b * b }) }
        val openReach = minOf(reach, rim + chart.reach)
        val near = chart.held(members) { v -> (mesh.position(v) - middle.position).length <= openReach && (mesh.normal(v) dot facing) >= LEAST_FACING }
        val candidates = chart.trianglesNear(chart.trianglesOf(members), near)
        fun distances(shape: ColorShape, only: BooleanArray): FloatArray {
            val distance = UvShapeDistance(shape, closedV = false)
            return chart.layOn(members, only, candidates) { v, _, _ ->
                val offset = mesh.position(v) - middle.position
                distance.at((offset dot across) / along, (offset dot up) / around, along, around)
            }
        }
        val opening = distances(shapes.opening, near)
        val open = BooleanArray(near.size) { near[it] && opening[it] > -chart.reach }
        fun inside(shape: ColorShape) = distances(shape, open)
        fun layer(slot: SlotName?, values: FloatArray) = PaintLayer.of(slot, surface, values, candidates, flat = true)
        fun within(values: FloatArray) = FloatArray(values.size) { minOf(values[it], opening[it]) }
        val layers = listOfNotNull(
            layer(look.white, opening),
            layer(look.iris, within(inside(shapes.iris))),
            layer(look.pupil, within(inside(shapes.pupil))),
            layer(look.highlight, within(inside(shapes.highlight))),
        )
        fun mark(name: String, places: List<SurfaceUv>?, width: MarkWidth): ResolvedMark? {
            places ?: return null
            val points = places.map { place ->
                val (position, normal) = anchors.onSurface(middle.position + across * (place.u * along) + up * (place.v * around), facing)
                ResolvedPoint(anchor, position, normal)
            }
            return ResolvedMark(MarkId("$id-$side-$name"), points, width, ink = true, cameraLocked = false, versions = emptyMap(), slot = look.ink)
        }
        val marks = listOfNotNull(mark("lid", shapes.lid, LID_WIDTH), mark("lashes", shapes.lashes, MarkWidth()), mark("brow", shapes.brow, MarkWidth()))
        return SurfacePaint(layers, mapOf(anchor.part to marks))
    }

    /** How far the field's surface runs from [anchor] as its u goes from [du] below to [du] above and its v likewise by [dv]; null where it does not resolve. */
    private fun span(anchors: FieldAnchors, anchor: Anchor, du: Float, dv: Float): Vec3? {
        val ahead = anchors.resolve(anchor.copy(u = anchor.u + du, v = anchor.v + dv, offset = 0f)) ?: return null
        val behind = anchors.resolve(anchor.copy(u = anchor.u - du, v = anchor.v - dv, offset = 0f)) ?: return null
        return (ahead.position - behind.position).takeIf { it.length > TINY }
    }

    /** [normal] turned [degrees] outward, away from the body's middle plane on the side [at] lies. */
    private fun turnedOut(normal: Vec3, at: Vec3, degrees: Float): Vec3 {
        if (degrees == 0f) return normal
        val side = Vec3(0f, 0f, if (at.z >= 0f) 1f else -1f)
        val outward = flattened(side, normal) ?: return normal
        return (normal + outward * tan(degrees * PI.toFloat() / 180f)).normalized()
    }

    /** [v] with its part along [normal] taken out, made unit length; null when nothing is left. */
    private fun flattened(v: Vec3, normal: Vec3): Vec3? {
        val flat = v - normal * (v dot normal)
        val length = sqrt(flat dot flat)
        return if (length > TINY) flat * (1f / length) else null
    }
}
