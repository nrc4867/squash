package dev.chieppa.squash.screens.woman

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

val BELT = GarmentId("belt")
val BUCKLE = PartId("buckle")
val PENDANT = PartId("pendant")

val BELT_LEATHER = SlotName("belt")
val STEEL = SlotName("steel")

/** The belt, a narrow band over the jeans' waistband, outside every layer at the waist. */
private const val BELT_FROM = 0.27f
private const val BELT_TO = 0.305f
private const val BELT_EASE = 0.006f
private const val BELT_LAYER = 4

/** The torso's socket for the buckle, at the front of the belt, and the neck's for the choker's pendant. */
private const val BUCKLE_SOCKET = 6
private const val BUCKLE_U = 0.288f
private const val PENDANT_SOCKET = 2
private const val PENDANT_U = 0.36f
private const val FRONT = 0f

/** The buckle, a squarish block from the skin out through the belt, so only its face and edges show past it, wider than tall. */
private val BUCKLE_SIDE = loop(p(0f, 0.018f), tuft(0.043f, 0.018f), tuft(0.045f, -0.018f), p(0f, -0.018f))
private val BUCKLE_TOP = loop(p(0f, 0.026f), tuft(0.043f, 0.026f), tuft(0.045f, -0.026f), p(0f, -0.026f))

/** The pendant, a small drop on the choker's front, from the skin out past the band. */
private val PENDANT_SIDE = loop(p(0f, 0.004f), p(0.012f, 0.004f), p(0.016f, 0f), p(0.012f, -0.012f), p(0f, -0.010f))
private val PENDANT_TOP = loop(p(0f, 0.007f), p(0.012f, 0.007f), p(0.016f, 0f), p(0.012f, -0.007f), p(0f, -0.007f))

private const val STEEL_RINGS = 16
private const val STEEL_SEGMENTS = 16
private const val UNSCALED = 1f

/** The belt with the buckle it owns, and the pendant the choker owns, each riding the bone it sits on. */
fun belt(): List<DocumentCommand> = listOf(
    cloth(BELT_LEATHER, 0.08f, 0.07f, 0.08f),
    cloth(STEEL, 0.72f, 0.73f, 0.76f),
) + garment(BELT, "Belt", WAIST_SLOT, BELT_LEATHER, listOf(GarmentBranch(TORSO, BELT_FROM, BELT_TO, ease = BELT_EASE)), layer = BELT_LAYER) + buildList {
    add(HandSockets.add(TORSO, BUCKLE_SOCKET, BUCKLE_U, FRONT, 0f, paired = false, sink = 0f))
    add(owned(BELT, BUCKLE, "blob", SocketRef(TORSO, HandSockets.name(BUCKLE_SOCKET))))
    add(RestDirection.restIn(BUCKLE, RestDirection.FORWARD))
    addAll(propShape(BUCKLE, BUCKLE_SIDE, BUCKLE_TOP, UNSCALED, STEEL_RINGS, STEEL_SEGMENTS))
    add(SetBaseSlot(BUCKLE, STEEL))

    add(HandSockets.add(NECK, PENDANT_SOCKET, PENDANT_U, FRONT, 0f, paired = false, sink = 0f))
    add(owned(CHOKER, PENDANT, "blob", SocketRef(NECK, HandSockets.name(PENDANT_SOCKET))))
    add(RestDirection.restIn(PENDANT, RestDirection.FORWARD))
    addAll(propShape(PENDANT, PENDANT_SIDE, PENDANT_TOP, UNSCALED, STEEL_RINGS, STEEL_SEGMENTS))
    add(SetBaseSlot(PENDANT, STEEL))
}
