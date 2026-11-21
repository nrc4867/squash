package dev.chieppa.squash.character.hair

import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.document.HairShell
import dev.chieppa.squash.document.Hairline
import dev.chieppa.squash.document.LockGroupId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.ShellFade
import dev.chieppa.squash.document.lockGroupPartId
import dev.chieppa.squash.geom.Patch
import kotlin.math.PI
import kotlin.math.cos

/** The patch name of a short-hair shell. */
const val SHELL_PATCH = "shell"

/** The rings from a shell's edge to the crown, and the points round each. */
private const val SHELL_RINGS = 40
private const val SHELL_COLUMNS = 120

/** How far past its softened hairline a shell's edge runs, in degrees, so the edge lies in bare skin. */
private const val EDGE_MARGIN = 3f

/** How far off the scalp a shell's edge still stands, as a share of its thickness, so it never sinks into the head there. */
private const val EDGE_LIFT = 0.3f

/** How far a shell's contour lies behind it, as a share of the head's reach, so it draws no line over the head under it. */
private const val SHELL_TUCK = 0.03f

/** How far up from the hairline each fade reaches before the tone is whole, in degrees. */
private val FADE_REACH = mapOf(ShellFade.NONE to 0f, ShellFade.LOW to 14f, ShellFade.MID to 26f, ShellFade.HIGH to 40f)

/** Degrees round from the front where a fade starts taking over from the front's hairline, and where it has whole. */
private const val FADE_FROM = 40f
private const val FADE_WHOLE = 75f

/** The places round the head, in degrees from the front, the hairline's heights are given at, each with how it reads them. */
private val HAIRLINE_KNOTS: List<Pair<Float, (Hairline) -> Float>> = listOf(
    0f to { it.front },
    30f to { it.temples },
    58f to { it.temples + (it.sideburns - it.temples) * 0.3f },
    72f to { it.sideburns },
    84f to { it.sideburns },
    100f to { it.ears },
    140f to { it.ears + (it.nape - it.ears) * 0.65f },
    180f to { it.nape },
)

/** How far round the head [azimuth] lies from the front either way, 0 to 180 degrees. */
private fun fromFront(azimuth: Float): Float {
    val a = ((azimuth % 360f) + 360f) % 360f
    return if (a > 180f) 360f - a else a
}

/** The lock-group id a head's shell is placed under. */
fun shellGroupId(head: PartId): LockGroupId = LockGroupId("${head.value}~shell")

/** The part id the short-hair shell over [head] is built and coloured as. */
fun shellPartId(head: PartId): PartId = lockGroupPartId(shellGroupId(head))

/** How high a hairline runs at [azimuth] degrees round the head from the front, in degrees up it, eased between its knots. */
fun hairlineAt(hairline: Hairline, azimuth: Float): Float {
    val a = fromFront(azimuth)
    val k = HAIRLINE_KNOTS.indexOfLast { it.first <= a }.coerceIn(0, HAIRLINE_KNOTS.size - 2)
    val (a0, h0) = HAIRLINE_KNOTS[k]
    val (a1, h1) = HAIRLINE_KNOTS[k + 1]
    val t = ((a - a0) / (a1 - a0)).coerceIn(0f, 1f)
    val eased = (1f - cos(t * PI.toFloat())) / 2f
    return h0(hairline) + (h1(hairline) - h0(hairline)) * eased
}

/**
 * How much of a shell's tone shows at [azimuth] and [elevation], 0 bare skin to 1 whole: none below its hairline, softening into it over
 * its soft degrees, and with a fade, rising from its fadeTo at the hairline to whole by the fade's reach up the sides and back.
 */
fun shellToneAt(shell: HairShell, azimuth: Float, elevation: Float): Float {
    val line = hairlineAt(shell.hairline, azimuth)
    val soft = shell.soft.coerceAtLeast(0.5f)
    val edge = smooth((elevation - line + soft / 2f) / soft)
    val reach = FADE_REACH.getValue(shell.fade)
    if (reach == 0f) return edge
    val a = fromFront(azimuth)
    val sides = smooth((a - FADE_FROM) / (FADE_WHOLE - FADE_FROM))
    val faded = shell.fadeTo + (1f - shell.fadeTo) * smooth((elevation - line) / reach)
    return edge * (1f - sides * (1f - faded))
}

/**
 * A short-hair shell over a head: [patch], a thin cap from just outside its hairline to the crown lying its thickness off the scalp and
 * coming down to it at its edge, and each vertex's [skin], how much of the skin shows over the shell's tone there. Its contour lies [tuck]
 * behind it.
 */
class ShellMesh(val patch: Patch, val skin: FloatArray, val tuck: Float) {
    companion object {
        /** [shell] over [head], read from the head's own surface as it shows at rest, round its skull's middle. */
        fun of(shell: HairShell, head: PlacedPart): ShellMesh {
            val middle = Scalp.over(head, null).middle
            val reach = HeadReach.of(head, middle)
            val headReach = reach.at(0f, 45f)
            val lift = shell.thickness * headReach
            val points = FloatArray(SHELL_RINGS * SHELL_COLUMNS * Patch.COMPONENTS)
            val skin = FloatArray(SHELL_RINGS * SHELL_COLUMNS)
            for (ring in 0 until SHELL_RINGS) {
                val u = ring.toFloat() / (SHELL_RINGS - 1)
                for (column in 0 until SHELL_COLUMNS) {
                    val azimuth = 360f * column / SHELL_COLUMNS
                    val edge = hairlineAt(shell.hairline, azimuth) - shell.soft - EDGE_MARGIN
                    val elevation = if (ring == SHELL_RINGS - 1) 90f else edge + (90f - edge) * u
                    val rise = EDGE_LIFT + (1f - EDGE_LIFT) * smooth((elevation - edge) / (shell.soft + EDGE_MARGIN).coerceAtLeast(1f))
                    val at = if (ring == SHELL_RINGS - 1) 0f else azimuth
                    val out = Scalp.direction(at, elevation)
                    val p = middle + out * (reach.at(at, elevation) + lift * rise)
                    val i = (ring * SHELL_COLUMNS + column)
                    points[i * 3] = p.x
                    points[i * 3 + 1] = p.y
                    points[i * 3 + 2] = p.z
                    skin[i] = 1f - shellToneAt(shell, azimuth, elevation)
                }
            }
            val us = FloatArray(SHELL_RINGS) { it.toFloat() / (SHELL_RINGS - 1) }
            val patch = Patch(SHELL_PATCH, SHELL_RINGS, SHELL_COLUMNS, closedV = true, slices = null, points = points, us = us)
            return ShellMesh(patch, skin, headReach * SHELL_TUCK)
        }
    }
}

private fun smooth(x: Float): Float {
    val a = x.coerceIn(0f, 1f)
    return a * a * (3f - 2f * a)
}
