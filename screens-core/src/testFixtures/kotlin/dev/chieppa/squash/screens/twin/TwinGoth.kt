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
import dev.chieppa.squash.screens.fennec.tuft
import dev.chieppa.squash.screens.woman.BOTTOM_SLOT
import dev.chieppa.squash.screens.woman.NECK_SLOT
import dev.chieppa.squash.screens.woman.TOP_SLOT
import dev.chieppa.squash.screens.woman.WAIST_SLOT
import dev.chieppa.squash.screens.woman.owned
import dev.chieppa.squash.screens.woman.propShape

val DARK_TOP = GarmentId("dark-top")
val BLACK_JEANS = GarmentId("black-jeans")
val GOTH_BELT = GarmentId("goth-belt")
val CHOKER = GarmentId("choker")
val BUCKLE = PartId("buckle")
val PENDANT = PartId("pendant")

val PLUM = SlotName("plum")
val BLACK_DENIM = SlotName("black-denim")
val BELT_LEATHER = SlotName("belt")
val STEEL = SlotName("steel")
val CHOKER_BAND = SlotName("choker")

/** The dark top from the hips to its scoop neckline, with long sleeves to the wrist; the jeans from under the navel to the boots. */
private const val TOP_FROM = 0.955f
private const val NECKLINE = 1.36f
private const val TOP_EASE = 0.006f
private const val SLEEVE_SHORT_OF_WRIST = 0.012f
private const val JEANS_TOP = 0.985f
private const val JEANS_HEM = 0.2f
private const val JEANS_WAIST_EASE = 0.0085f
private const val JEANS_LEG_EASE = 0.0065f

/** The belt over the jeans' waistband, and the buckle at its front, a block 0.034 wide, 0.026 high and 0.006 deep standing 0.016 off the body. */
private const val BELT_FROM = 0.961f
private const val BELT_TO = 0.983f
private const val BELT_EASE = 0.0125f
private const val BUCKLE_SOCKET = 6
private const val BUCKLE_HEIGHT = 0.972f
private const val FRONT = 0f
private val BUCKLE_SIDE = loop(p(0f, 0.013f), tuft(0.019f, 0.013f), tuft(0.022f, -0.013f), p(0f, -0.013f))
private val BUCKLE_TOP = loop(p(0f, 0.017f), tuft(0.019f, 0.017f), tuft(0.022f, -0.017f), p(0f, -0.017f))

/** The choker, a band round the base of the neck, and its small steel ring hanging at the front. */
private const val CHOKER_TO = 0.18f
private const val CHOKER_EASE = 0.006f
private const val PENDANT_SOCKET = 2
private const val PENDANT_U = 0.02f
private val PENDANT_SIDE = loop(p(0f, 0.002f), p(0.008f, 0.002f), p(0.010f, 0f), p(0.008f, -0.010f), p(0f, -0.010f))
private val PENDANT_TOP = loop(p(0f, 0.006f), p(0.008f, 0.006f), p(0.010f, 0f), p(0.008f, -0.006f), p(0f, -0.006f))

private const val STEEL_RINGS = 16
private const val STEEL_SEGMENTS = 16

/** The goth outfit: a dark long-sleeved top, black jeans, a studless belt with its buckle, and a choker with its ring; the boots are [twinShoes]. */
fun twinGoth(): List<DocumentCommand> =
    listOf(cloth(PLUM, 0x3A3442), cloth(BLACK_DENIM, 0x2C2A33), cloth(BELT_LEATHER, 0x17151A), cloth(STEEL, 0xC4C4CC), cloth(CHOKER_BAND, 0x141215)) +
        twinGarment(
            DARK_TOP, "Dark top", TOP_SLOT, PLUM,
            listOf(GarmentBranch(TORSO, torsoU(TOP_FROM), torsoU(NECKLINE), ease = TOP_EASE), GarmentBranch(ARM_L, 0f, armU(SLEEVE_SHORT_OF_WRIST), ease = TOP_EASE)),
        ) +
        twinGarment(
            BLACK_JEANS, "Black jeans", BOTTOM_SLOT, BLACK_DENIM,
            listOf(GarmentBranch(TORSO, 0f, torsoU(JEANS_TOP), ease = JEANS_WAIST_EASE), GarmentBranch(LEG_L, INTO_PELVIS, legU(JEANS_HEM), ease = JEANS_LEG_EASE)),
            layer = WAIST_LAYER,
        ) +
        twinGarment(GOTH_BELT, "Belt", WAIST_SLOT, BELT_LEATHER, listOf(GarmentBranch(TORSO, torsoU(BELT_FROM), torsoU(BELT_TO), ease = BELT_EASE)), layer = BELT_LAYER) +
        twinGarment(CHOKER, "Choker", NECK_SLOT, CHOKER_BAND, listOf(GarmentBranch(NECK, 0f, CHOKER_TO, ease = CHOKER_EASE))) + buildList {
            add(HandSockets.add(TORSO, BUCKLE_SOCKET, torsoU(BUCKLE_HEIGHT), FRONT, 0f, paired = false, sink = 0f))
            add(owned(GOTH_BELT, BUCKLE, "blob", SocketRef(TORSO, HandSockets.name(BUCKLE_SOCKET))))
            add(RestDirection.restIn(BUCKLE, RestDirection.FORWARD))
            addAll(propShape(BUCKLE, BUCKLE_SIDE, BUCKLE_TOP, 1f, STEEL_RINGS, STEEL_SEGMENTS))
            add(SetBaseSlot(BUCKLE, STEEL))

            add(HandSockets.add(NECK, PENDANT_SOCKET, PENDANT_U, FRONT, 0f, paired = false, sink = 0f))
            add(owned(CHOKER, PENDANT, "blob", SocketRef(NECK, HandSockets.name(PENDANT_SOCKET))))
            add(RestDirection.restIn(PENDANT, RestDirection.FORWARD))
            addAll(propShape(PENDANT, PENDANT_SIDE, PENDANT_TOP, 1f, STEEL_RINGS, STEEL_SEGMENTS))
            add(SetBaseSlot(PENDANT, STEEL))
        }
