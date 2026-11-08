package dev.chieppa.squash.screens.smoke

import dev.chieppa.squash.screens.TestWork

import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.BrushStroke
import dev.chieppa.squash.document.BrushStrokeId
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Region
import dev.chieppa.squash.document.RegionId
import dev.chieppa.squash.document.SetAreaFill
import dev.chieppa.squash.document.SetBaseSlot
import dev.chieppa.squash.document.SetBrushStroke
import dev.chieppa.squash.document.SetPaletteSlot
import dev.chieppa.squash.document.SetRegion
import dev.chieppa.squash.document.SlotColor
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.positionAt
import dev.chieppa.squash.geom.surfaceAt
import dev.chieppa.squash.render.BodySide
import dev.chieppa.squash.render.MeshDraw
import dev.chieppa.squash.render.ObjectShading
import dev.chieppa.squash.render.PartMaterial
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.render.SceneView
import dev.chieppa.squash.render.ViewsScene
import dev.chieppa.squash.screens.sheet.coloredPartDraw
import dev.chieppa.squash.screens.sheet.translation
import dev.chieppa.stretch.gpu.CameraView
import dev.chieppa.stretch.gpu.GpuOptions
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan
import kotlin.test.assertNotNull
import dev.chieppa.stretch.gpu.Vec3 as GpuVec3

internal const val SEAMS_SIZE = 900
private const val FOV = 0.698f
private const val OUTLINE = 3f
private const val NEAREST_RINGS = 80
private const val NEAREST_AROUND = 96

internal val SEAMS_WELL = Color(0xFFF2EEE6)
internal val SEAMS_INK = Color(0xFF1C1517)
internal val FUR = SlotName("fur")
internal val CREAM = SlotName("cream")
internal val SOCK = SlotName("sock")
internal val SPOT = SlotName("spot")
internal val PAD = SlotName("pad")
private val FALLBACK = PartMaterial(Color(0xFFB0B0B0), ink = SEAMS_INK)

internal fun seamsRef(id: String) = TemplateRef(TemplateId(id), 1)

internal fun seamsSession(work: TestWork, parts: List<PartInstance>): CharacterSession {
    val folder = TemplateFolder.shipped()
    val empty = SquashDocument(Character(CharacterId("fennec"), "Fennec", BodyPlanRef(BodyPlanId("canine-quadruped"), 1)))
    return CharacterSession(AddParts(parts).apply(empty).document, TemplateLibrary(folder.parts, folder.plans), work.scope, work.dispatcher)
}

internal fun seamsPalette(): List<DocumentCommand> = listOf(
    SetPaletteSlot(FUR, SlotColor(0.91f, 0.63f, 0.29f)),
    SetPaletteSlot(CREAM, SlotColor(0.98f, 0.92f, 0.78f)),
    SetPaletteSlot(SOCK, SlotColor(0.25f, 0.45f, 0.70f)),
    SetPaletteSlot(SPOT, SlotColor(0.55f, 0.20f, 0.35f)),
    SetPaletteSlot(PAD, SlotColor(0.35f, 0.25f, 0.28f)),
)

/** A knee band, a blot with a curved border and a wavy stroke over the v seam. */
internal fun seamsPaint(part: PartId): List<DocumentCommand> {
    val around = List(10) { i ->
        val angle = 2.0 * PI * i / 10
        Anchor(part, u = 0.28f + 0.09f * cos(angle).toFloat(), v = 0.25f + 0.12f * sin(angle).toFloat())
    }
    val wave = List(9) { i -> Anchor(part, u = 0.78f + 0.05f * sin(i * 1.4).toFloat(), v = (0.82f + i * 0.045f) % 1f) }
    return listOf(
        SetBaseSlot(part, FUR),
        SetAreaFill(part, "knee", CREAM),
        SetRegion(part, RegionId("blot"), Region(RegionId("blot"), around, SOCK)),
        SetBrushStroke(part, BrushStrokeId("wave"), BrushStroke(BrushStrokeId("wave"), wave, SPOT, 0.045f)),
    )
}

/** A part's draw through the colored seam, on the side of the body its origin lies. */
internal fun seamsDraw(session: CharacterSession, part: PlacedPart, shading: ObjectShading, toCamera: Vec3): MeshDraw {
    val id = assertNotNull(part.id)
    val side = when {
        part.side == null -> null
        part.origin.z >= 0f -> BodySide.POSITIVE_Z
        else -> BodySide.NEGATIVE_Z
    }
    val color = assertNotNull(session.bodyColor.output.value).parts[id]
    return coloredPartDraw(part.surface.mesh, color, session.document.value.character.palette, FALLBACK, shading, side, toCamera, translation(part.origin), OUTLINE)
}

internal fun seamsView(rect: PixelRect, camera: CameraView, draws: List<MeshDraw>) = SceneView(rect, camera.viewProjection(rect.width, rect.height), draws)

internal val seamsWhole = PixelRect(0, 0, SEAMS_SIZE, SEAMS_SIZE)

/** Renders [frame], leaves it in the smoke images as [name], and fails if it is blank. Errors go to [errors]. */
internal fun seamsRender(name: String, frame: SceneFrame, errors: MutableList<String>, width: Int = SEAMS_SIZE, height: Int = SEAMS_SIZE): IntArray {
    val image = renderSnapshot(frame, width, height, GpuOptions(onError = { errors += it })) { ViewsScene(it) }
    keepImage(image.pixels, image.width, name)
    assertNotBlank(image.pixels, name)
    return image.pixels
}

internal fun Vec3.toGpuVec() = GpuVec3(x, y, z)

internal fun CameraView.toCameraFrom(point: Vec3) = Vec3(eye.x, eye.y, eye.z) - point

private fun Vec3.rotatedAbout(axis: Vec3, radians: Float): Vec3 =
    this * cos(radians) + (axis cross this) * sin(radians) + axis * ((axis dot this) * (1f - cos(radians)))

/** A camera [distance] from the point at [u] and [v], along the surface's normal turned by [turn] about the part's axis and leaning by [lift] up it. */
internal fun cameraOn(part: PlacedPart, u: Float, v: Float, distance: Float, turn: Float = 0f, near: Float = 0.05f, lift: Float = 0.2f): CameraView {
    val patch = part.surface.patches.first { it.slices != null }
    val at = patch.surfaceAt(u, v)
    val axis = at.alongU.normalized()
    val out = (at.normal.rotatedAbout(axis, turn) + axis * lift).normalized()
    val target = part.origin + part.surface.shown(at.position)
    return CameraView(eye = (target + part.surface.shown(out) * distance).toGpuVec(), target = target.toGpuVec(), near = near)
}

internal fun pixelsPerUnit(distance: Float) = SEAMS_SIZE / (2f * distance * tan(FOV / 2f))

/** The place on the part's first swept patch nearest [point], in body space. */
internal fun PlacedPart.nearest(point: Vec3): Pair<Float, Float> {
    val patch = surface.patches.first { it.slices != null }
    val first = patch.ringUs.first()
    val last = patch.ringUs.last()
    var best = 0f to 0f
    var bestDistance = Float.MAX_VALUE
    for (i in 0..NEAREST_RINGS) {
        val u = first + (last - first) * i / NEAREST_RINGS
        for (j in 0 until NEAREST_AROUND) {
            val v = j.toFloat() / NEAREST_AROUND
            val distance = (origin + surface.shown(patch.positionAt(u, v)) - point).length
            if (distance < bestDistance) {
                bestDistance = distance
                best = u to v
            }
        }
    }
    return best
}

/** The v at [u] on the part's first swept patch whose normal faces [toCamera] most. */
internal fun PlacedPart.vFacing(u: Float, toCamera: Vec3): Float {
    val patch = surface.patches.first { it.slices != null }
    return (0 until NEAREST_AROUND).map { it.toFloat() / NEAREST_AROUND }.maxBy { surface.shown(patch.surfaceAt(u, it).normal) dot toCamera }
}
