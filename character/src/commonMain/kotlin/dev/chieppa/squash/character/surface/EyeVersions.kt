package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.ResolvedPoint
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.acos

/** How far off a head's facing, in degrees, a view still sees the head from the front, as the face's front angle version does. */
const val FRONT_DEGREES = 20f

private const val DEGREES_PER_RADIAN = (180.0 / PI).toFloat()

/**
 * One painted eye, both sides, as views see it: [look], its paint in its own look, and [front], in its front look, which a view that sees
 * the head within [FRONT_DEGREES] of its [facing] shows instead; null where it has none. [middle] is where the head's eyes sit, [anchor]
 * the place on the head [facing] is carried from in a pose.
 */
class EyeVersions(val look: SurfacePaint, val front: SurfacePaint?, val facing: Vec3, val middle: Vec3, private val anchor: Anchor) {
    /** The eye's paint a viewer at [toViewer] from [middle] sees. */
    fun seenFrom(toViewer: Vec3): SurfacePaint = if (front != null && degrees(facing, toViewer) <= FRONT_DEGREES) front else look

    fun on(render: RenderMesh): EyeVersions = EyeVersions(look.on(render), front?.on(render), facing, middle, anchor)

    fun posed(render: RenderMesh, carry: (ResolvedPoint) -> ResolvedPoint): EyeVersions {
        val moved = carry(ResolvedPoint(anchor, middle, facing))
        return EyeVersions(look.posed(render, carry), front?.posed(render, carry), moved.normal, moved.position, anchor)
    }

    private fun degrees(a: Vec3, b: Vec3): Float = acos((a.normalized() dot b.normalized()).coerceIn(-1f, 1f)) * DEGREES_PER_RADIAN
}
