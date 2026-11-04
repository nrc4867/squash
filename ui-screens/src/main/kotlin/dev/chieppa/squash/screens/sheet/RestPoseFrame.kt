package dev.chieppa.squash.screens.sheet

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.geom.BodyBox
import dev.chieppa.squash.geom.BodyView
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.DepthLayer
import dev.chieppa.squash.render.MeshDraw
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.render.SceneView
import dev.chieppa.stretch.frame.buildOverlay
import kotlin.math.abs
import kotlin.math.min

/** How much of the image the rest pose fills along its tighter direction. */
private const val REST_POSE_FILL = 0.84f

/** The ground's half size under the rest pose, as multiples of the character's length and height. */
private const val GROUND_ACROSS = 0.54f
private const val GROUND_TALL = 0.05f

/** The camera that shows all of [box] from the side in a [width] by [height] image, as the boards draw the rest pose. */
fun restPoseCamera(box: BodyBox, width: Int, height: Int): BodyCamera {
    val view = BodyView.SIDE
    val across = abs(box.size dot view.right)
    val tall = abs(box.size dot view.up)
    val scale = min(width * REST_POSE_FILL / across, height * REST_POSE_FILL / tall)
    return BodyCamera(view, SheetFit(box.center, scale, box.radius), width, height)
}

/**
 * The character standing square from the side, as one image [width] by [height]: parts in the document filled, the rest
 * dashed, or every part dashed when [dashed]. [sockets] show as dots, [picked] the larger one.
 */
fun restPoseFrame(
    character: AssembledCharacter,
    look: SheetLook,
    width: Int,
    height: Int,
    sockets: List<SocketRef> = emptyList(),
    picked: SocketRef? = null,
    dashed: Boolean = false,
): SceneFrame {
    val box = character.box ?: return SceneFrame(look.well)
    val camera = restPoseCamera(box, width, height)
    val shown = character.parts.filter { it.box != null }.map(::SheetPart)
    val (hinted, solid) = shown.partition { dashed || it.planned }
    val ground = MeshDraw(
        RenderMesh.UnitDisc,
        look.groundMaterial,
        placedDisc(
            Vec3(box.center.x, character.floor, box.center.z),
            BodyView.SIDE.right * (abs(box.size.x) * GROUND_ACROSS),
            BodyView.SIDE.up * (abs(box.size.y) * GROUND_TALL),
            BodyView.SIDE.toViewer,
        ),
        layer = DepthLayer.BACK,
    )
    val draws = listOf(ground) + solid.sortedBy { it.center dot BodyView.SIDE.toViewer }.map { look.draw(it, look.fullOutline) }
    val overlay = buildOverlay {
        for (part in hinted) {
            for (path in part.silhouetteFrom(BodyView.SIDE)) {
                dashed(path.map(camera::toPixel), look.template, look.templateWidth, look.templateDash, look.templateGap)
            }
        }
        for (socket in character.sockets) {
            if (socket.ref !in sockets) continue
            val at: Offset = camera.toPixel(socket.position)
            val radius = if (socket.ref == picked) look.pickedDotRadius else look.dotRadius
            dot(at, radius + look.dotEdge, look.ring)
            dot(at, radius, if (socket.ref == picked) look.active else look.dotFill)
        }
    }
    return SceneFrame(look.well, listOf(SceneView(PixelRect(0, 0, width, height), camera.viewProjection(), draws)), overlay)
}
