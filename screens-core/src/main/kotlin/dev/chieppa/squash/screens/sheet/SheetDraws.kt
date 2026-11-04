package dev.chieppa.squash.screens.sheet

import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.DepthLayer
import dev.chieppa.squash.render.MeshDraw
import dev.chieppa.squash.render.ObjectShading
import dev.chieppa.squash.render.PartMaterial
import dev.chieppa.squash.render.warmShifted
import dev.chieppa.stretch.gpu.Mat4

/** The matrix moving a part's mesh to where it sits. */
fun SheetPart.model(): Mat4 = translation(origin)

/** This material lit as [shading] says, its shade the warm shift of its base. */
private fun PartMaterial.shadedBy(shading: ObjectShading) =
    copy(shade = base.warmShifted(shading.shadowDepth), shading = shading.mode, toLight = shading.toLight)

/** A part's draw in its side's color: the far side takes the shade, as the boards draw it. [shading] lights it, flat by default. */
fun SheetLook.draw(part: SheetPart, outline: Float, opacity: Float = 1f, layer: DepthLayer = DepthLayer.SCENE, shading: ObjectShading = ObjectShading()) = MeshDraw(
    part.mesh,
    (if (part.mirrored) farPart else this.part).shadedBy(shading),
    part.model(),
    outline,
    opacity,
    layer,
)

/** The matrix taking the unit disc's x, y and facing z to [x], [y] and [facing], at [at]. */
fun placedDisc(at: Vec3, x: Vec3, y: Vec3, facing: Vec3): Mat4 = Mat4.of(
    x.x, x.y, x.z, 0f,
    y.x, y.y, y.z, 0f,
    facing.x, facing.y, facing.z, 0f,
    at.x, at.y, at.z, 1f,
)

fun translation(by: Vec3): Mat4 = Mat4.of(
    1f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f,
    0f, 0f, 1f, 0f,
    by.x, by.y, by.z, 1f,
)
