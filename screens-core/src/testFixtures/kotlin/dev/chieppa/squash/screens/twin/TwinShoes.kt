package dev.chieppa.squash.screens.twin

import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.RestDirection
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetBaseSlot
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.screens.fennec.loop
import dev.chieppa.squash.screens.fennec.p
import dev.chieppa.squash.screens.woman.FEET_SLOT
import dev.chieppa.squash.screens.woman.ownedPair
import dev.chieppa.squash.screens.woman.propShape

val PUMPS = GarmentId("pumps")
val FLATS = GarmentId("flats")
val BOOTS = GarmentId("boots")
val HEEL_L = PartId("heel-l")
val HEEL_R = PartId("heel-r")

val PATENT = SlotName("patent")
val TAN = SlotName("tan")
val BOOT_LEATHER = SlotName("boot")

/*
 * Shoes are drawn in the foot's units (FOOT_SIZE metres each) from the back of the ankle, where the ground lies 0.156 below; each is the
 * blob the foot is swapped for while it is worn.
 */

/**
 * A pump in Side: its heel seat raised 0.045 m off the ground, the sole falling from it under the arch to the ball and a pointed toe on the
 * ground, a low back and the instep open down to the toe box.
 */
private val PUMP_SIDE = loop(
    p(-0.115f, -0.030f), p(-0.110f, 0.000f), p(-0.040f, 0.008f), p(0.050f, -0.012f), p(0.150f, -0.045f), p(0.260f, -0.088f), p(0.360f, -0.125f),
    p(0.430f, -0.143f), p(0.450f, -0.152f), p(0.380f, -0.157f), p(0.250f, -0.152f), p(0.130f, -0.112f), p(0.000f, -0.072f), p(-0.080f, -0.054f),
    p(-0.115f, -0.050f),
)
private val PUMP_TOP = loop(
    p(-0.115f, 0.000f), p(-0.105f, 0.060f), p(-0.040f, 0.070f), p(0.080f, 0.076f), p(0.220f, 0.088f), p(0.330f, 0.080f), p(0.410f, 0.045f),
    p(0.450f, 0.000f), p(0.410f, -0.045f), p(0.330f, -0.080f), p(0.220f, -0.088f), p(0.080f, -0.076f), p(-0.040f, -0.070f), p(-0.105f, -0.060f),
)

/** A heel from the pump's seat to the ground, 0.013 m round where it meets the shoe and 0.0045 at the tip. */
private val HEEL_SIDE = loop(p(0f, 0.029f), p(0.040f, 0.018f), p(0.098f, 0.010f), p(0.102f, 0f), p(0.098f, -0.010f), p(0.040f, -0.018f), p(0f, -0.029f))
private const val HEEL_SOCKET = 1
private const val HEEL_U = 0.07f
private const val SOLE_V = 0.5f
private const val HEEL_RINGS = 16
private const val HEEL_SEGMENTS = 10

/** A flat in Side: a thin sole on the ground, a low back, the instep showing and a softly pointed toe. */
private val FLAT_SIDE = loop(
    p(-0.130f, -0.100f), p(-0.122f, -0.040f), p(-0.060f, -0.020f), p(0.040f, -0.035f), p(0.150f, -0.060f), p(0.270f, -0.090f), p(0.380f, -0.125f),
    p(0.440f, -0.145f), p(0.430f, -0.160f), p(0.300f, -0.163f), p(0.100f, -0.163f), p(-0.100f, -0.160f),
)
private val FLAT_TOP = loop(
    p(-0.130f, 0.000f), p(-0.118f, 0.064f), p(-0.040f, 0.076f), p(0.080f, 0.084f), p(0.220f, 0.094f), p(0.330f, 0.088f), p(0.410f, 0.050f),
    p(0.440f, 0.000f), p(0.410f, -0.050f), p(0.330f, -0.088f), p(0.220f, -0.094f), p(0.080f, -0.084f), p(-0.040f, -0.076f), p(-0.118f, -0.064f),
)

/** A boot's foot in Side: closed over the ankle, a round toe, standing on a platform sole 0.028 m deep under the ground line. */
private val BOOT_SIDE = loop(
    p(-0.150f, -0.210f), p(-0.148f, -0.050f), p(-0.130f, 0.060f), p(-0.040f, 0.090f), p(0.040f, 0.070f), p(0.130f, 0.010f), p(0.260f, -0.050f),
    p(0.370f, -0.090f), p(0.425f, -0.125f), p(0.440f, -0.170f), p(0.425f, -0.220f), p(0.300f, -0.225f), p(0.050f, -0.225f), p(-0.130f, -0.222f),
)
private val BOOT_TOP = loop(
    p(-0.150f, 0.000f), p(-0.140f, 0.080f), p(-0.040f, 0.098f), p(0.080f, 0.104f), p(0.220f, 0.115f), p(0.330f, 0.112f), p(0.410f, 0.075f),
    p(0.440f, 0.000f), p(0.410f, -0.075f), p(0.330f, -0.112f), p(0.220f, -0.115f), p(0.080f, -0.104f), p(-0.040f, -0.098f), p(-0.140f, -0.080f),
)

/** The boot's shaft up the leg to mid-calf, over the jeans, standing off a little more toward its top. */
private const val SHAFT_TOP = 0.31f
private const val SHAFT_EASE = 0.015f

/** The shoes: pumps with a heel each riding the foot, pointed flats, and platform boots with a shaft to mid-calf. */
fun twinShoes(): List<DocumentCommand> = listOf(cloth(PATENT, 0x1E1B20), cloth(TAN, 0xB07A45), cloth(BOOT_LEATHER, 0x1D1A20)) +
    twinGarment(PUMPS, "Pumps", FEET_SLOT, PATENT, emptyList(), changes = mapOf(FOOT_L to shod(PUMP_SIDE, PUMP_TOP, PATENT))) + buildList {
        add(HandSockets.add(FOOT_L, HEEL_SOCKET, HEEL_U, SOLE_V, 0f, paired = false, sink = 0f))
        val seat = HandSockets.name(HEEL_SOCKET)
        add(ownedPair(PUMPS, "heels", HEEL_L to SocketRef(FOOT_L, seat), HEEL_R to SocketRef(FOOT_R, seat), "tube"))
        add(RestDirection.restIn(HEEL_L, RestDirection.DOWN))
        addAll(propShape(HEEL_L, HEEL_SIDE, HEEL_SIDE, FOOT_SIZE, HEEL_RINGS, HEEL_SEGMENTS))
        add(SetBaseSlot(HEEL_L, PATENT))
    } +
    twinGarment(FLATS, "Flats", FEET_SLOT, TAN, emptyList(), changes = mapOf(FOOT_L to shod(FLAT_SIDE, FLAT_TOP, TAN))) +
    twinGarment(
        BOOTS, "Boots", FEET_SLOT, BOOT_LEATHER, listOf(GarmentBranch(LEG_L, legU(SHAFT_TOP), 1f, ease = SHAFT_EASE)),
        layer = OVER_LAYER, changes = mapOf(FOOT_L to shod(BOOT_SIDE, BOOT_TOP, BOOT_LEATHER)),
    )
