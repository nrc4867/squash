package dev.chieppa.squash.screens.woman

import dev.chieppa.squash.character.build.HandJoints
import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.fennec.PenPoint
import dev.chieppa.squash.screens.fennec.drawn
import dev.chieppa.squash.screens.fennec.loop
import dev.chieppa.squash.screens.fennec.p

val SKIRT = GarmentId("skirt")
val TANK_TOP = GarmentId("tank-top")
val SKIRT_CLOTH = SlotName("skirt")
val TANK = SlotName("tank")

/**
 * How far the skirt carries the torso down past the hips. A garment's branch runs along its part and on past the part's far end
 * only, and the torso's far end is the neck, so the skirt redraws the torso longer under it, a bell from the hips to above the knee,
 * and covers that.
 */
private const val SKIRT_DROP = 0.36f
private val SKIRTED_LENGTH = TORSO_LENGTH + SKIRT_DROP

/** Where the redrawn torso's u lies for [u] on her own torso. */
private fun skirted(u: Float) = (u * TORSO_LENGTH + SKIRT_DROP) / SKIRTED_LENGTH

/** Her own torso's outline from the hips' widest up, carried down by the drop. */
private fun upper(points: List<PenPoint>, from: Float) = points.filter { it.x >= from }.map { PenPoint(it.x + SKIRT_DROP, it.y, it.corner) }

private const val HIPS_FROM = 0.12f

/** How far in the skirt turns the legs, in degrees, so they leave the bell's hips and come out under its hem rather than through its sides. */
private const val SKIRT_LEGS = -12f

/** How far out the skirt holds the arms, in degrees, so the hands hang clear of its flare rather than inside it. */
private const val SKIRT_ARMS = 15f

private val BELL_FRONT = listOf(p(0f, 0f), p(0.015f, 0.19f), p(0.1f, 0.185f), p(0.22f, 0.16f), p(0.34f, 0.135f), p(0.42f, 0.12f))
private val BELL_BACK = listOf(p(0.42f, -0.15f), p(0.3f, -0.165f), p(0.18f, -0.185f), p(0.08f, -0.2f), p(0.015f, -0.2f))
private val BELL_SIDE = listOf(p(0f, 0f), p(0.015f, 0.32f), p(0.1f, 0.305f), p(0.22f, 0.27f), p(0.34f, 0.23f), p(0.42f, 0.205f))

private fun skirtedSide(): List<PenPoint> {
    val front = upper(TORSO_SIDE_POINTS.takeWhile { it.y >= 0f }, HIPS_FROM)
    val back = upper(TORSO_SIDE_POINTS.dropWhile { it.y >= 0f }, HIPS_FROM)
    return BELL_FRONT + front + back + BELL_BACK
}

private fun skirtedFront(): List<PenPoint> {
    val near = upper(TORSO_FRONT_POINTS.takeWhile { it.y >= 0f }, HIPS_FROM)
    val far = upper(TORSO_FRONT_POINTS.dropWhile { it.y >= 0f }, HIPS_FROM)
    return BELL_SIDE + near + far + BELL_SIDE.drop(1).reversed().map { PenPoint(it.x, -it.y) }
}

/** The torso as the skirt redraws it: the bell under the skirt, and her joints and sockets moved to stay where they were on her. */
private fun skirtedTorso() = PartData(
    outlines = mapOf(View.SIDE to loop(*skirtedSide().toTypedArray()), View.TOP to loop(*skirtedFront().toTypedArray())),
    params = SPINE.mapIndexed { i, u -> HandJoints.u(i + 1) to skirted(u) }.toMap() + mapOf(
        HandSockets.u(SHOULDER_SOCKET) to skirted(SHOULDER_U),
        HandSockets.u(HIP_SOCKET) to skirted(HIP_U),
    ),
)

/** A leg under the skirt: drawn to a point at its top, which sits on the bell's surface and would show through its side, and turned in. */
private val SKIRTED_LEG_SIDE = drawn(p(0f, 0.015f), p(0.1f, 0.07f), p(0.3f, 0.072f), p(0.46f, 0.058f), p(0.94f, 0f), p(0.46f, -0.058f), p(0.3f, -0.078f), p(0.1f, -0.075f), p(0f, -0.015f))
private val SKIRTED_LEG_FRONT = drawn(p(0f, 0.015f), p(0.1f, 0.068f), p(0.3f, 0.072f), p(0.47f, 0.058f), p(0.94f, 0f), p(0.47f, -0.056f), p(0.3f, -0.07f), p(0.1f, -0.068f), p(0f, -0.015f))

private fun skirtedLeg() = PartData(
    outlines = mapOf(View.SIDE to SKIRTED_LEG_SIDE, View.TOP to SKIRTED_LEG_FRONT),
    params = mapOf(PlacementParams.OUTWARD to SKIRT_LEGS),
)

/** The skirt runs from the hem to the waist; the tank top from under the bust to the base of the neck, the arms out of it bare. */
private val SKIRT_TO = skirted(0.32f)
private val TANK_FROM = skirted(0.42f)
private val TANK_TO = skirted(0.95f)
private const val SKIRT_EASE = 0.02f
private const val TANK_EASE = 0.01f

/** The dress: a skirt flaring from the waist to above the knee, the legs coming out under it, and a tank top made for it. */
fun dress(): List<DocumentCommand> = listOf(
    cloth(SKIRT_CLOTH, 0.18f, 0.36f, 0.30f),
    cloth(TANK, 0.95f, 0.88f, 0.70f),
) + garment(
    SKIRT, "Skirt", BOTTOM_SLOT, SKIRT_CLOTH, listOf(GarmentBranch(TORSO, 0f, SKIRT_TO, ease = SKIRT_EASE)), layer = WAIST_LAYER,
    changes = mapOf(TORSO to BodyPartChange.Redraw(skirtedTorso()), LEG_L to BodyPartChange.Redraw(skirtedLeg()), ARM_L to BodyPartChange.Redraw(PartData(params = mapOf(PlacementParams.OUTWARD to SKIRT_ARMS)))),
) + garment(TANK_TOP, "Tank top", TOP_SLOT, TANK, listOf(GarmentBranch(TORSO, TANK_FROM, TANK_TO, ease = TANK_EASE)), changes = mapOf(ARM_L to OPEN))
