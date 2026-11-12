package dev.chieppa.squash.screens.twin

import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.build.RestDirection
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetBaseSlot
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.screens.fennec.loop
import dev.chieppa.squash.screens.fennec.p
import dev.chieppa.squash.screens.woman.BOTTOM_SLOT
import dev.chieppa.squash.screens.woman.FEET_SLOT
import dev.chieppa.squash.screens.woman.TOP_SLOT
import dev.chieppa.squash.screens.woman.ownedPair
import dev.chieppa.squash.screens.woman.propShape

val BIKINI = GarmentId("bikini")
val SHORTS = GarmentId("shorts")
val SANDALS = GarmentId("sandals")
val SANDAL_STRAPS = GarmentId("sandal-straps")
val CUP_L = PartId("cup-l")
val CUP_R = PartId("cup-r")
val HALTER_L = PartId("halter-l")
val HALTER_R = PartId("halter-r")

val SWIM = SlotName("swim")
val DENIM = SlotName("denim")
val CORK = SlotName("cork")

/** The bikini's band under the bust, close to the skin, and its tie round the base of the neck. */
private const val BAND_FROM = 1.139f
private const val BAND_TO = 1.157f
private const val BIKINI_EASE = 0.0035f
private const val TIE_TO = 0.12f

/** The torso's socket for the cups, on the band's top edge in front of each breast. */
private const val CUP_SOCKET = 7
private const val CUP_HEIGHT = 1.152f
private const val CUP_V = 0.915f

/**
 * A cup in Side, from the band up to the strap's root, forward up: a thin triangle laid over the bust, standing forward over its fullest
 * and back in above it.
 */
private val CUP_SIDE = loop(p(0f, 0.008f), p(0.040f, 0.030f), p(0.090f, 0.010f), p(0.140f, -0.026f), p(0.140f, -0.031f), p(0.090f, 0.005f), p(0.040f, 0.025f), p(0f, 0.003f))

/** The cup's width: broad along the band, narrowing to the strap. */
private val CUP_TOP = loop(p(0f, 0.061f), p(0.045f, 0.044f), p(0.095f, 0.022f), p(0.140f, 0.004f), p(0.140f, -0.004f), p(0.095f, -0.022f), p(0.045f, -0.044f), p(0f, -0.061f))
private const val CUP_IN = 6f

/** The halter straps from each cup's top up and in to the neck tie. */
private const val STRAP_SOCKET = 1
private val STRAP = loop(p(0f, 0.0045f), p(0.10f, 0.0045f), p(0.104f, 0f), p(0.10f, -0.0045f), p(0f, -0.0045f))
private val STRAP_TOP = loop(p(0f, 0.002f), p(0.10f, 0.002f), p(0.104f, 0f), p(0.10f, -0.002f), p(0f, -0.002f))
private const val STRAP_IN = -14f

/** The shorts: the waist over the hips up to below the navel, the legs ending just below the crotch, and a deeper cuff band at the hem. */
private const val SHORTS_TOP = 0.985f
private const val SHORTS_HEM = 0.735f
private const val SHORTS_EASE = 0.0085f

/** The sandals: a cork sole under the foot's footprint, 0.012 m deep, and a strap over the forefoot. */
val SANDAL_SOLE_L = PartId("sandal-sole-l")
val SANDAL_SOLE_R = PartId("sandal-sole-r")
private const val SOLE_DEPTH = 0.012f
private const val STRAP_FROM = 0.56f
private const val STRAP_TO = 0.61f
private const val STRAP_EASE = 0.004f

private const val CUP_RINGS = 24
private const val CUP_SEGMENTS = 12

/** The beach outfit's clothes: a polka-dot-less halter bikini, denim shorts, cork sandals with a red strap; the shades are [twinShades]. */
fun twinBeach(): List<DocumentCommand> = listOf(cloth(SWIM, 0xEE6A58), cloth(DENIM, 0x6F9FCF), cloth(CORK, 0xCF9F6D)) +
    twinGarment(
        BIKINI, "Bikini", TOP_SLOT, SWIM,
        listOf(GarmentBranch(TORSO, torsoU(BAND_FROM), torsoU(BAND_TO), ease = BIKINI_EASE), GarmentBranch(NECK, 0f, TIE_TO, ease = BIKINI_EASE)),
        changes = mapOf(ARM_L to OPEN),
    ) + buildList {
        add(HandSockets.add(TORSO, CUP_SOCKET, torsoU(CUP_HEIGHT), CUP_V, 0f, paired = true, sink = 0f))
        add(ownedPair(BIKINI, "cups", CUP_L to SocketRef(TORSO, HandSockets.name(CUP_SOCKET)), CUP_R to SocketRef(TORSO, HandSockets.name(CUP_SOCKET)), "blob"))
        add(RestDirection.restIn(CUP_L, RestDirection.UP))
        add(SetParameter(CUP_L, PlacementParams.OUTWARD, CUP_IN))
        addAll(propShape(CUP_L, CUP_SIDE, CUP_TOP, 1f, CUP_RINGS, CUP_SEGMENTS))
        add(SetBaseSlot(CUP_L, SWIM))
        add(HandSockets.add(CUP_L, STRAP_SOCKET, 0.97f, 0f, 0f, paired = false, sink = 0f))
        val top = HandSockets.name(STRAP_SOCKET)
        add(ownedPair(BIKINI, "halter", HALTER_L to SocketRef(CUP_L, top), HALTER_R to SocketRef(CUP_R, top), "tube"))
        add(RestDirection.restIn(HALTER_L, RestDirection.UP))
        add(SetParameter(HALTER_L, PlacementParams.OUTWARD, STRAP_IN))
        addAll(propShape(HALTER_L, STRAP, STRAP_TOP, 1f, CUP_RINGS, CUP_SEGMENTS))
        add(SetBaseSlot(HALTER_L, SWIM))
    } +
    twinGarment(
        SHORTS, "Shorts", BOTTOM_SLOT, DENIM,
        listOf(GarmentBranch(TORSO, 0f, torsoU(SHORTS_TOP), ease = SHORTS_EASE), GarmentBranch(LEG_L, INTO_PELVIS, legU(SHORTS_HEM), ease = SHORTS_EASE)),
        layer = WAIST_LAYER,
    ) +
    twinGarment(SANDALS, "Sandals", FEET_SLOT, CORK, emptyList()) +
    sole(SANDALS, SANDAL_SOLE_SOCKET, SANDAL_SOLE_L, SANDAL_SOLE_R, FOOTPRINT, PRINT_BACK, PRINT_FRONT, SOLE_DEPTH, CORK) +
    twinGarment(SANDAL_STRAPS, "Sandal straps", STRAPS_SLOT, SWIM, listOf(GarmentBranch(FOOT_L, STRAP_FROM, STRAP_TO, ease = STRAP_EASE)), layer = OVER_LAYER)
