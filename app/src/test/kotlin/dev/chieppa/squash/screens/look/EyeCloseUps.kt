package dev.chieppa.squash.screens.look

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.character.surface.SurfaceEyes
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.TestWork
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** How far the eyes' view turns from their facing toward the character's left, in degrees: front, three-quarter and side. */
private val EYE_TURNS = listOf(0f, 40f, 90f)

/** How much past the outer corners a view of both eyes reaches, a share of an eye's opening width. */
private const val EYE_MARGIN = 1.6f

/** How many pixels a side each eye view is. */
private const val EYE_TILE = 800

/**
 * Close views of a character's first painted eye and its mirror: framed on both eyes together, from the way they face, then turned
 * toward the character's left to three-quarter and side, in one row.
 */
class EyeCloseUps(private val work: TestWork, private val errors: MutableList<String>, private val folder: File) {
    /** The row, kept as the image [name]; nothing where the character has no painted eye that resolves. */
    fun keep(session: CharacterSession, name: String) {
        work.settle(session)
        val surface = session.characterSurface.output.value?.surface ?: return
        val eye = session.document.value.character.paintedEyes.firstOrNull() ?: return
        val frames = SurfaceEyes.framesOf(surface, eye)
        if (frames.isEmpty()) return
        val center = frames.fold(Vec3(0f, 0f, 0f)) { sum, f -> sum + f.middle } * (1f / frames.size)
        val opening = frames.maxOf { (it.perU * eye.look.width).length }
        val spread = frames.maxOf { (it.middle - center).length }
        val radius = spread + opening * EYE_MARGIN
        val facing = frames.fold(Vec3(0f, 0f, 0f)) { sum, f -> sum + f.faces }.let { Vec3(it.x, 0f, it.z).normalized() }
        val spots = EYE_TURNS.map { degrees -> Spot(center, radius, turnedLeft(facing, degrees)) }
        SpotViews(work, errors, folder).keep(session, listOf(spots), name, EYE_TILE)
    }

    /** [facing] turned [degrees] about up toward the character's left, its -z. */
    private fun turnedLeft(facing: Vec3, degrees: Float): Vec3 {
        val angle = degrees * PI.toFloat() / 180f
        val c = cos(angle)
        val s = sin(angle)
        val left = Vec3(0f, 0f, -1f)
        val side = (left - facing * (left dot facing)).let { if (it.length > 0f) it.normalized() else left }
        return facing * c + side * s
    }
}
