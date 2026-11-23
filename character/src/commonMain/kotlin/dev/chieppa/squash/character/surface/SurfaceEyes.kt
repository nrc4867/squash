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
import dev.chieppa.squash.document.shownFront
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.geom.ChartCut
import dev.chieppa.squash.geom.ChartOval
import dev.chieppa.squash.geom.ColorShape
import dev.chieppa.squash.geom.UvShapeDistance
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.character.build.PlacedPart
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.tan

/** The lid line's weight from its back corner over the top and under the bottom: thickest over the top. */
private val LID_WIDTH = MarkWidth(listOf(WidthStop(0f, 0.5f), WidthStop(0.25f, 1.25f), WidthStop(0.5f, 0.5f), WidthStop(0.75f, 0.35f), WidthStop(1f, 0.5f)))

/** How thick a brow's ends are, a share of its middle's thickness, once it is thicker than a line. */
private const val BROW_END_SHARE = 0.45f

/** A brow [thickness] times the line weight: an ink line at 1, and above that full in the middle and thinning toward its ends. */
private fun browWidth(thickness: Float): MarkWidth {
    if (thickness == 1f) return MarkWidth()
    val ends = if (thickness > 1f) maxOf(1f, thickness * BROW_END_SHARE) else thickness
    return MarkWidth(listOf(WidthStop(0f, ends), WidthStop(0.5f, thickness), WidthStop(1f, ends)))
}

/** How far along the head's u and v the eye's chart is read either side of its middle to find which way they run. */
private const val CHART_STEP = 0.01f

/** How far from its middle an eye paints at most, as a multiple of its opening's width. */
private const val EYE_REACH = 4f

/** How far past where their values cut them, in cells, an eye's fills are laid, so each fill's edge drawn at each fragment lies on its mesh. */
private const val FILL_GROW_CELLS = 1.5f

/** How far toward the eye's facing a vertex's normal must turn, as a cosine, to take its paint. */
private const val LEAST_FACING = 0.2f

private const val TINY = 1e-9f

/** How nearly along a head's axis, as a cosine, its facing may lie for its eyes' chart to run along its u. */
private const val ACROSS_AXIS = 0.5f

/**
 * Eyes painted on a head. Each eye's middle is its anchor resolved on the field, and its facing the surface's normal there
 * turned outward by its look-out. Every shape of its [EyeShapes] lies in the plane square to that facing, its chart running along the
 * head's coordinates toward the front and the top (see [chartOf]), scaled by how far the surface runs per unit of the head's u and v there; each vertex of the surface near the
 * eye that faces it is projected along the facing into that plane and painted where it falls inside. So the paint foreshortens and wraps
 * as the head turns, and never stands past the head's outline. A flat eye faces the way its head faces, turned out by its look-out, in
 * place of the surface's normal, so the same projection lays it on the head as a decal seen undistorted from that facing. A mirrored eye
 * is painted again with its anchor's v read as 1 - v.
 */
object SurfaceEyes {
    /**
     * [eyes] painted on [surface], each eye's layers in order, white, lid shadow, iris, its lighter crescent, darker top and rim, pupil,
     * highlight, the lid's own colour and marks and its mass, those its look leaves off left out, and its lid, lashes and brow as the
     * head's ink marks, in its look and its front look, its lids standing where the eye says; each side of each look laid anew is one piece
     * of [runAll]'s work. With a [memo], a side laid on the same surface last time is taken from it with its lids stood again, so moving
     * a lid lays no layer. The skin the lids cover as they close takes the colour of the layer of [under]'s paint below the eyes that
     * the eye sits in (see [paintEyeSitsIn]), else its head's own skin.
     */
    suspend fun of(
        surface: CharacterSurface,
        eyes: List<PaintedEye>,
        runAll: RunAll = IN_TURN,
        memo: EyeMemo? = null,
        under: suspend () -> EyeGround = { EyeGround.NONE },
    ): SurfacePaint {
        if (eyes.isEmpty()) return SurfacePaint.NONE
        val chart = surface.chart
        val anchors = FieldAnchors(surface.field)
        val placed = eyes.map { eye -> placeOf(chart, anchors, eye) }
        val looks = eyes.map { eye -> listOfNotNull(eye.look, eye.shownFront()) }
        val earlier = memo?.earlier(surface).orEmpty()
        val keys = eyes.indices.map { e -> looks[e].map { look -> sidesOf(eyes[e]).indices.map { side -> EyeSideKey.of(eyes[e], look, side) } } }
        val laid = eyes.indices.map { e -> looks[e].indices.map { l -> Array(keys[e][l].size) { side -> earlier[keys[e][l][side]] } } }
        runAll(
            eyes.indices.filter { placed[it] != null }.flatMap { e ->
                looks[e].indices.flatMap { l ->
                    sidesOf(eyes[e]).mapIndexedNotNull { side, anchor ->
                        if (laid[e][l][side] != null) null else { -> laid[e][l][side] = layOne(chart, anchors, eyes[e], looks[e][l], anchor, side) }
                    }
                }
            },
        )
        memo?.keep(surface, eyes.indices.flatMap { e -> looks[e].indices.flatMap { l -> keys[e][l].indices.mapNotNull { side -> laid[e][l][side]?.let { keys[e][l][side] to it } } } }.toMap())
        val beneath = under()
        return SurfacePaint(
            emptyList(),
            eyes = eyes.indices.mapNotNull { e ->
                val (middle, facing) = placed[e] ?: return@mapNotNull null
                val versions = looks[e].indices.map { l ->
                    sidesOf(eyes[e]).indices.mapNotNull { side -> laid[e][l][side]?.let { standOne(anchors, eyes[e], looks[e][l], it, beneath) } }
                        .fold(SurfacePaint.NONE) { all, one -> all + one }
                }
                EyeVersions(versions[0], versions.getOrNull(1), facing, middle, eyes[e].anchor.copy(offset = 0f, v = 0.5f))
            },
        )
    }

    /** Both sides' anchors of [eye], its own and, mirrored, the other. */
    private fun sidesOf(eye: PaintedEye): List<Anchor> = if (eye.mirrored) listOf(eye.anchor, eye.anchor.copy(v = 1f - eye.anchor.v)) else listOf(eye.anchor)

    /** Where [eye]'s head sits between its eyes and the way it faces; null where its anchor does not resolve. */
    private fun placeOf(chart: SurfaceChart, anchors: FieldAnchors, eye: PaintedEye): Pair<Vec3, Vec3>? {
        val underOpenJaw = chart.surface.field.openJawOf(eye.anchor.part) != null
        val middle = (if (underOpenJaw) null else anchors.resolve(eye.anchor.copy(offset = 0f, v = 0.5f))) ?: anchors.resolve(eye.anchor.copy(offset = 0f)) ?: return null
        val head = chart.surface.field.runs.firstOrNull { it.partId == eye.anchor.part }?.part ?: return null
        return middle.position to head.turn * PartBuilder.facing(head.template)
    }

    /**
     * Where each side of [eye] lies on [surface] in its own look, its own side first: its middle, and how far body space runs per unit of
     * its chart forward and up. A side whose anchor does not resolve is left out.
     */
    fun framesOf(surface: CharacterSurface, eye: PaintedEye): List<EyeChartFrame> {
        val anchors = FieldAnchors(surface.field)
        return sidesOf(eye).mapIndexedNotNull { side, anchor ->
            frameOf(surface, anchors, anchor, side > 0, eye.look, eye.flat)?.let { EyeChartFrame(it.middle.position, it.across * it.along, it.up * it.around, it.facing, side > 0) }
        }
    }

    /** One side's frame: where its middle resolves, its head's members, and the chart's directions and scales there. */
    internal class Frame(val middle: ResolvedPoint, val members: List<Int>, val facing: Vec3, val up: Vec3, val across: Vec3, val along: Float, val around: Float)

    private fun frameOf(surface: CharacterSurface, anchors: FieldAnchors, anchor: Anchor, mirrored: Boolean, look: EyeLook, flat: Boolean): Frame? {
        val middle = anchors.resolve(anchor.copy(offset = 0f)) ?: return null
        val run = surface.field.runs.indexOfFirst { it.partId == anchor.part && it.patch.name == anchor.patch }
        if (run < 0) return null
        val members = surface.field.runs.indices.filter { surface.field.runs[it].partId == anchor.part }
        val head = surface.field.runs[run].part
        val (forward, down) = chartOf(anchors, anchor, mirrored, head) ?: return null
        val along = forward.length / (2f * CHART_STEP)
        val around = down.length / (2f * CHART_STEP)
        val facing = turnedOut(if (flat) head.turn * PartBuilder.facing(head.template) else middle.normal, middle.position, look.lookOut)
        val up = flattened(down * -1f, facing) ?: return null
        val across = flattened(forward - up * (forward dot up), facing) ?: return null
        return Frame(middle, members, facing, up, across, along, around)
    }

    /**
     * One side of an eye laid with its lids open: where it lies, at which [anchor] and [side], its [layers], and the vertices [covered] by
     * its opening and a [COVER_MARGIN] round it.
     */
    internal class Laid(val frame: Frame, val anchor: Anchor, val side: Int, val layers: List<PaintLayer>, val covered: IntArray)

    /** [eye]'s [side] at [anchor] in [look] laid on the surface with its lids open, its layers reaching wherever a lid may stand; null where it does not frame. */
    private fun layOne(chart: SurfaceChart, anchors: FieldAnchors, eye: PaintedEye, look: EyeLook, anchor: Anchor, side: Int): Laid? {
        val surface = chart.surface
        val frame = frameOf(surface, anchors, anchor, side > 0, look, eye.flat) ?: return null
        val middle = frame.middle
        val members = frame.members
        val facing = frame.facing
        val up = frame.up
        val across = frame.across
        val along = frame.along
        val around = frame.around
        val shapes = EyeShapes(look, eye.gazeForward, eye.gazeUp, lidAspect(look, along, around))
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
        val (chartU, chartV) = eyeChartAt(surface, candidates, middle.position, across * (1f / along), up * (1f / around))
        val grow = mesh.cell * FILL_GROW_CELLS
        fun layer(slot: SlotName?, values: FloatArray, oval: ChartOval?, cut: ChartCut? = null, tint: LayerTint? = null) =
            if (oval != null && (oval.halfU <= 0f || oval.halfV <= 0f)) null else PaintLayer.of(slot, surface, values, candidates, flat = true, fill = LayerFill(chartU, chartV, shapes.almond, oval, shapes.turn, shapes.fillTurn, grow, cut), tint = tint)
        fun within(values: FloatArray) = FloatArray(values.size) { minOf(values[it], opening[it]) }
        val iris = within(inside(shapes.iris))
        val detail = shapes.detail
        val tones = EyeTones(look)
        fun cutLayer(tone: EyeTones.Tone, values: FloatArray, oval: ChartOval?, cut: ChartCut?) = cut?.let { layer(tone.slot, values, oval, it, tone.tint) }
        val layers = listOfNotNull(
            layer(look.white, opening, null),
            cutLayer(tones.lidShadow, opening, null, detail.lidShadow),
            layer(look.iris, iris, shapes.irisOval),
            cutLayer(tones.irisLower, iris, shapes.irisOval, detail.irisLower),
            cutLayer(tones.irisUpper, iris, shapes.irisOval, detail.irisUpper),
            cutLayer(tones.irisRim, iris, shapes.irisOval, detail.irisRim),
            layer(look.pupil, within(inside(shapes.pupil)), shapes.pupilOval),
            layer(look.highlight, within(inside(shapes.highlight)), shapes.highlightOval),
        ) + eyeLidLayers(chart, members, middle.position, facing, across, up, along, around, shapes, tones, grow)
        val margin = -COVER_MARGIN * look.height * around
        val covered = near.indices.filter { near[it] && opening[it] >= margin }.toIntArray()
        return Laid(frame, anchor, side, layers, covered)
    }

    /**
     * [laid] with [eye]'s lids stood where it says in [look]: its layers closed by them, the skin they cover in the colour of the layer of
     * [under]'s paint the eye sits in, else its head's skin, and its lid, lashes and brow as the head's ink marks.
     */
    private fun standOne(anchors: FieldAnchors, eye: PaintedEye, look: EyeLook, laid: Laid, under: EyeGround): SurfacePaint {
        val id = eye.id
        val frame = laid.frame
        val anchor = laid.anchor
        val side = laid.side
        val middle = frame.middle
        val facing = frame.facing
        val up = frame.up
        val across = frame.across
        val along = frame.along
        val around = frame.around
        val aspect = lidAspect(look, along, around)
        val shapes = EyeShapes(look, eye.gazeForward, eye.gazeUp, aspect, eye.lidPose)
        val lids = shapes.almond.lids
        val layers = if (lids.isOpen) laid.layers.filterNot { it.isLidCover } else coveredAs(laid.layers, paintEyeSitsIn(laid.covered, under.layers), under.skinOf(laid.anchor.part)).map { it.withLids(lids) }
        val cell = anchors.cellOf(anchor.part, anchor.patch)
        fun mark(name: String, places: List<SurfaceUv>?, width: MarkWidth, alongFacing: Boolean = true): ResolvedMark? {
            places ?: return null
            val points = places.map { place ->
                val inPlane = middle.position + across * (place.u * along) + up * (place.v * around)
                val (position, normal) = if (alongFacing) anchors.onSurfaceAlong(inPlane, facing, cell) else anchors.onSurface(inPlane, facing, cell)
                ResolvedPoint(anchor, position, normal)
            }
            return ResolvedMark(MarkId("$id-$side-$name"), points, width, ink = true, cameraLocked = false, versions = emptyMap(), slot = look.ink)
        }
        val lashes = shapes.lashes.mapIndexed { i, lash -> mark(if (i == 0) "lashes" else "lashes-$i", lash, MarkWidth()) }
        val marks = listOfNotNull(mark("lid", shapes.lid, LID_WIDTH)) + lashes.filterNotNull() + listOfNotNull(mark("brow", shapes.brow, browWidth(look.browWidth), alongFacing = false))
        return SurfacePaint(layers, mapOf(anchor.part to marks))
    }

    /**
     * The eye's chart at [anchor] on [head]: how far the surface runs toward the eye's front corner and toward its bottom per
     * 2 [CHART_STEP] of the head's coordinates. On a head facing along its axis, the front runs along its u and the bottom along its v; on
     * a head facing across its axis, as an upright head does, the bottom runs down the head's axis as far as its u does and the front round
     * its v toward its face, so the eye stays level however the head's u lines lean where it sits.
     */
    private fun chartOf(anchors: FieldAnchors, anchor: Anchor, mirrored: Boolean, head: PlacedPart): Pair<Vec3, Vec3>? {
        val faces = head.template.axis.faces?.toVec3()?.normalized()
        val rest = head.template.axis.rest.toVec3().normalized()
        if (faces == null || abs(faces dot rest) > ACROSS_AXIS) {
            val forward = span(anchors, anchor, CHART_STEP, 0f) ?: return null
            val down = span(anchors, anchor, 0f, if (mirrored) -CHART_STEP else CHART_STEP) ?: return null
            return forward to down
        }
        val up = span(anchors, anchor, CHART_STEP, 0f) ?: return null
        val round = span(anchors, anchor, 0f, CHART_STEP) ?: return null
        val toFace = head.turn * PartBuilder.facing(head.template)
        val headUp = (head.turn * rest).normalized().let { if ((it dot up) >= 0f) it else it * -1f }
        return (if ((round dot toFace) >= 0f) round else round * -1f) to headUp * -up.length
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
