package dev.chieppa.squash.screens.look

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.isMouth
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.TestWork
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** How far the mouth's view turns from its facing toward the character's left, in degrees: front, three-quarter and side. */
private val MOUTH_TURNS = listOf(0f, 40f, 90f)

/** How far round the mouth a view reaches, a share of the mouth's width from corner to corner. */
private const val MOUTH_MARGIN = 1.1f

/** How many pixels a side each mouth view is. */
private const val MOUTH_TILE = 700

/** Where a mouth close-up looks: the mouth's middle, how far round it the view reaches, and the way it faces, all in body space. */
class MouthSpot(val center: Vec3, val radius: Float, val facing: Vec3)

/**
 * Close views of a character's first mouth: framed on its lip line from the way it faces, then turned toward the character's left to
 * three-quarter and side, in one row.
 */
class MouthCloseUps(private val work: TestWork, private val errors: MutableList<String>, private val folder: File) {
    /** Where the close-ups of the character's first mouth look, framed on its lip line as drawn; null where it has none that resolves. */
    fun spotOf(session: CharacterSession): MouthSpot? {
        work.settle(session)
        val body = session.bodySurfaceWork.output.value ?: return null
        val (_, resolved) = body.parts.entries.firstOrNull { (_, part) -> part.groups.any { it.isMouth } } ?: return null
        val group = resolved.groups.first { it.isMouth }
        val lip = group.roles.firstOrNull { it.role == MarkRole.LIP_LINE }?.marks.orEmpty().toSet()
        val points = resolved.marks.filter { it.source in lip }.flatMap { it.points }
        if (points.size < 2) return null
        val center = points.fold(Vec3.ZERO) { sum, p -> sum + p.position } * (1f / points.size)
        val width = points.maxOf { a -> points.maxOf { b -> (a.position - b.position).length } }
        val facing = points.fold(Vec3.ZERO) { sum, p -> sum + p.normal }.let { Vec3(it.x, 0f, it.z) }
        if (facing.length == 0f || width == 0f) return null
        return MouthSpot(center, width * MOUTH_MARGIN, facing.normalized())
    }

    /** The row through [spot], kept as the image [name]. */
    fun keep(session: CharacterSession, name: String, spot: MouthSpot) {
        val spots = MOUTH_TURNS.map { degrees -> Spot(spot.center, spot.radius, turnedLeft(spot.facing, degrees)) }
        SpotViews(work, errors, folder).keep(session, listOf(spots), name, MOUTH_TILE)
    }

    /** [facing] turned [degrees] about up toward the character's left, its -z. */
    private fun turnedLeft(facing: Vec3, degrees: Float): Vec3 {
        val angle = degrees * PI.toFloat() / 180f
        val left = Vec3(0f, 0f, -1f)
        val side = (left - facing * (left dot facing)).let { if (it.length > 0f) it.normalized() else left }
        return facing * cos(angle) + side * sin(angle)
    }
}
