package dev.chieppa.squash.screens.woman

import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.RestDirection
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetBaseSlot
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.screens.fennec.loop
import dev.chieppa.squash.screens.fennec.p

val PUMPS = GarmentId("pumps")
val HEEL_L = PartId("heel-l")
val HEEL_R = PartId("heel-r")

/**
 * A pump in Side, from the ankle forward, front up: a low back over the heel, the instep falling steeply to a soft toe that reaches
 * below the bare foot's sole, and the sole rising from the ball to a heel seat raised off the ground.
 */
private val PUMP_SIDE = loop(
    p(0.000f, 0.030f), p(0.030f, 0.042f), p(0.120f, 0.018f), p(0.250f, -0.025f), p(0.380f, -0.074f), p(0.455f, -0.098f), p(0.470f, -0.116f),
    p(0.430f, -0.130f), p(0.320f, -0.128f), p(0.210f, -0.096f), p(0.110f, -0.058f), p(0.030f, -0.046f), p(-0.005f, -0.024f),
)
private val PUMP_TOP = loop(
    p(0.000f, 0.000f), p(0.010f, 0.030f), p(0.090f, 0.035f), p(0.230f, 0.044f), p(0.340f, 0.046f), p(0.420f, 0.026f), p(0.470f, 0.000f),
    p(0.420f, -0.026f), p(0.340f, -0.046f), p(0.230f, -0.044f), p(0.090f, -0.035f), p(0.010f, -0.030f),
)

/** The foot's socket under the heel seat, where the swapped shoe's sole meets its back, at the bottom of the shoe. */
private const val HEEL_SOCKET = 1
private const val HEEL_U = 0.07f
private const val SOLE_V = 0.5f

/** A heel from the seat down to the ground, front up in Side: broad where it meets the shoe and narrowing to a small tip. */
private val HEEL_SIDE = loop(p(0f, 0.022f), p(0.035f, 0.011f), p(0.084f, 0.008f), p(0.09f, 0f), p(0.084f, -0.008f), p(0.035f, -0.011f), p(0f, -0.022f))
private val HEEL_TOP = loop(p(0f, 0.018f), p(0.035f, 0.010f), p(0.084f, 0.008f), p(0.09f, 0f), p(0.084f, -0.008f), p(0.035f, -0.010f), p(0f, -0.018f))
private const val HEEL_RINGS = 16
private const val HEEL_SEGMENTS = 10

/**
 * The pumps: the foot swapped for a slim shoe whose heel is raised and whose instep falls low enough that the ankle shows above it, with no
 * shell over it, and a heel the garment owns under each, riding the foot's bone.
 */
fun pumps(): List<DocumentCommand> =
    garment(PUMPS, "Pumps", FEET_SLOT, PATENT, emptyList(), changes = mapOf(FOOT_L to shod(PUMP_SIDE, PUMP_TOP, PATENT))) + buildList {
        add(HandSockets.add(FOOT_L, HEEL_SOCKET, HEEL_U, SOLE_V, 0f, paired = false, sink = 0f))
        val seat = HandSockets.name(HEEL_SOCKET)
        add(ownedPair(PUMPS, "heels", HEEL_L to SocketRef(FOOT_L, seat), HEEL_R to SocketRef(FOOT_R, seat), "tube"))
        add(RestDirection.restIn(HEEL_L, RestDirection.DOWN))
        addAll(propShape(HEEL_L, HEEL_SIDE, HEEL_TOP, FOOT_SIZE, HEEL_RINGS, HEEL_SEGMENTS))
        add(SetBaseSlot(HEEL_L, PATENT))
    }
