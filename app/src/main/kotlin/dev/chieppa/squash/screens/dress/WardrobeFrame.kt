package dev.chieppa.squash.screens.dress

import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.branchPartId
import dev.chieppa.squash.geom.BodyView
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.DepthLayer
import dev.chieppa.squash.render.MeshDraw
import dev.chieppa.squash.render.PartMaterial
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.screens.sheet.objectShading
import dev.chieppa.squash.screens.sheet.placedDisc
import dev.chieppa.squash.screens.surface.CharacterState
import dev.chieppa.squash.screens.surface.FACE_BINS
import dev.chieppa.squash.screens.surface.characterLook
import dev.chieppa.squash.screens.surface.characterView
import dev.chieppa.squash.screens.surface.framedCamera
import dev.chieppa.squash.screens.surface.framedPointsCamera
import dev.chieppa.squash.screens.surface.headAngle
import kotlin.math.abs
import kotlin.math.max

/** The share of a thumbnail left round the whole character, and round a garment's close crop. */
private const val WHOLE_MARGIN = 0.03f
private const val CLOSE_MARGIN = 0.04f

/** How far round what a garment's crop holds it reaches, as a multiple of its own size, and at least, as a share of the character's radius. */
private const val CLOSE_REACH = 1.1f
private const val CLOSE_LEAST = 0.2f

/** How much of a close crop's height may lie below the floor. */
private const val BELOW_FLOOR = 0.1f

/** The ground's half size under the character, as multiples of its length and height. */
private const val GROUND_ACROSS = 0.54f
private const val GROUND_TALL = 0.05f

/** How thick a thumbnail's lines are against a view's, at the same density. */
private const val LINE_SCALE = 0.75f

/** The angle a head turned square to the camera shows, in degrees off its axis. */
private const val SIDE_DEGREES = 90f

/** Where a garment's crop is seen from: turned from the side toward the front and a little from above, so a garment shows some of its front. */
private val GARMENT_VIEW = (BodyView.SIDE.toViewer + BodyView.FRONT.toViewer * 0.7f + Vec3.Y * 0.2f).normalized()

/** How far past a garment's own extent a part sitting on one it covers may sit and still be kept whole in its crop, as a share of that extent. */
private const val TIP_REACH = 0.15f

/** How much larger than a garment a part by it may be and still be kept whole in its crop, as a head is under a hood. */
private const val WHOLE_PART = 1.6f

/** The scene's one view: the whole character from the side on its ground, or a three-quarter close crop round the garment it is about. */
internal fun wardrobeFrame(state: CharacterState, document: SquashDocument, scene: WardrobeScene, look: WardrobeLook): SceneFrame? {
    val character = document.character
    val box = state.character.box ?: return null
    val parts = state.character.parts.filter { it.id != null }
    val around = scene.around?.let { garmentPoints(state, character, it) }?.takeIf { it.isNotEmpty() }
    val toViewer = if (around != null) GARMENT_VIEW else BodyView.SIDE.toViewer
    val framed = if (around != null) {
        framedPointsCamera(closeCrop(around, toViewer, box.radius, state.character.floor), toViewer, look.width, look.height, CLOSE_MARGIN)
    } else {
        framedCamera(parts, toViewer, look.width, look.height, WHOLE_MARGIN)
    }
    val view = characterView(
        PixelRect(0, 0, look.width, look.height),
        framed.camera,
        state,
        characterLook(character.lineRules, LINE_SCALE * look.density),
        character.shading.objectShading(),
        framed.pixelsPerUnit,
    ) { id -> state.character.part(id)?.let { FACE_BINS.binFor(headAngle(it, toViewer)) } ?: FACE_BINS.binFor(SIDE_DEGREES) }
    if (around != null) return SceneFrame(look.background, listOf(view))
    val ground = MeshDraw(
        RenderMesh.UnitDisc,
        PartMaterial(look.ground),
        placedDisc(
            Vec3(box.center.x, state.character.floor, box.center.z),
            BodyView.SIDE.right * (abs(box.size.x) * GROUND_ACROSS),
            BodyView.SIDE.up * (abs(box.size.y) * GROUND_TALL),
            toViewer,
        ),
        layer = DepthLayer.BACK,
    )
    return SceneFrame(look.background, listOf(view.copy(draws = listOf(ground) + view.draws)))
}

/**
 * Where [garment] shows on the posed character: its drawn surface, or its branch parts on a character of parts' own meshes, and the parts
 * it owns or changes; then, so its crop keeps them whole, each part it covers and each part standing up from one within its reach, as a
 * head under a hood and the ears through it, that is not much larger than it.
 */
private fun garmentPoints(state: CharacterState, character: Character, garment: GarmentId): List<Vec3> {
    val held = character.wardrobe.garment(garment) ?: return emptyList()
    fun bothSides(ids: Collection<PartId>) = ids.toSet() + ids.mapNotNull { id -> character.part(id)?.let(character::twinOf)?.id }
    val own = bothSides(held.changes.keys + character.ownedBy(garment).map { it.id })
    val covered = bothSides(held.branches.map { it.part })
    val branchPrefix = branchPartId(garment, PartId("")).value
    val placed = state.character.parts.filter { it.id != null }
    val drawn = state.dress?.garments.orEmpty().filter { it.cover.garment.id == garment }.map { it.drawn }
    val points = drawn.flatMap { mesh -> List(mesh.vertexCount, mesh::position) } +
        placed.filter { it.id in own || it.id!!.value.startsWith(branchPrefix) }.flatMap(::pointsOf)
    if (points.isEmpty()) return points
    val low = points.reduce(::lowest)
    val high = points.reduce(::highest)
    val size = sizeOf(points)
    val slack = (high - low) * TIP_REACH
    fun within(at: Vec3) = at.x in low.x - slack.x..high.x + slack.x && at.y in low.y - slack.y..high.y + slack.y && at.z in low.z - slack.z..high.z + slack.z
    val near = placed.map { it to pointsOf(it) }.filter { (part, at) ->
        val sitsOn = character.part(part.id!!)?.socket?.parent
        val base = part.anchor ?: part.origin
        val close = part.id in covered || (sitsOn in covered && within(base) && at.sumOf { it.y.toDouble() } / at.size > base.y)
        close && at.isNotEmpty() && sizeOf(at) <= size * WHOLE_PART
    }
    return points + near.flatMap { it.second }
}

private fun pointsOf(part: PlacedPart): List<Vec3> = part.shownPoints()

private fun lowest(a: Vec3, b: Vec3) = Vec3(minOf(a.x, b.x), minOf(a.y, b.y), minOf(a.z, b.z))

private fun highest(a: Vec3, b: Vec3) = Vec3(maxOf(a.x, b.x), maxOf(a.y, b.y), maxOf(a.z, b.z))

/** The largest of [points]' extents along the axes. */
private fun sizeOf(points: List<Vec3>): Float {
    val extent = points.reduce(::highest) - points.reduce(::lowest)
    return maxOf(extent.x, extent.y, extent.z)
}

/**
 * Points that frame [points] seen along [toViewer] with room round them: their extent across and up the view grown by [CLOSE_REACH], at
 * least [CLOSE_LEAST] of [radius] each way, and lifted to reach no further below the [floor] than [BELOW_FLOOR] of its height, so a crop
 * round the feet shows the legs over them.
 */
private fun closeCrop(points: List<Vec3>, toViewer: Vec3, radius: Float, floor: Float): List<Vec3> {
    val back = toViewer.normalized()
    val right = (Vec3.Y cross back).normalized()
    val up = back cross right
    fun span(axis: Vec3) = points.minOf { it dot axis } to points.maxOf { it dot axis }
    fun reach(low: Float, high: Float) = max((high - low) * 0.5f * CLOSE_REACH, radius * CLOSE_LEAST)
    val (left, rightmost) = span(right)
    val (bottom, top) = span(up)
    val (far, near) = span(back)
    val across = reach(left, rightmost)
    val tall = reach(bottom, top)
    val ground = points.reduce { a, b -> a + b } * (1f / points.size)
    val middle = max((bottom + top) * 0.5f, (Vec3(ground.x, floor, ground.z) dot up) + tall * (1f - 2f * BELOW_FLOOR))
    val center = right * ((left + rightmost) * 0.5f) + up * middle + back * ((far + near) * 0.5f)
    val depth = (near - far) * 0.5f
    return listOf(-1f, 1f).flatMap { i -> listOf(-1f, 1f).flatMap { j -> listOf(-1f, 1f).map { k -> center + right * (across * i) + up * (tall * j) + back * (depth * k) } } }
}
