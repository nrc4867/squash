package dev.chieppa.squash.screens.woman

import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.build.RestDirection
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetBaseSlot
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.screens.fennec.loop
import dev.chieppa.squash.screens.fennec.p

val LAPEL_L = PartId("lapel-l")
val LAPEL_R = PartId("lapel-r")
val BLAZER_BUTTON = PartId("blazer-button")

val LAPEL_FACING = SlotName("lapel")
val BUTTON_HORN = SlotName("button")

/** The torso's sockets for the blazer's own parts: the lapels' tops either side of the neck, and the button where they meet. */
private const val LAPEL_SOCKET = 4
private const val BUTTON_SOCKET = 5
private const val LAPEL_U = 0.92f
private const val LAPEL_V = 0.85f
private const val BUTTON_U = 0.52f
private const val FRONT = 0f

/** How far each lapel leans in toward her middle as it runs down, in degrees. */
private const val LAPEL_IN = -24f

/**
 * A lapel in Side, from its top by the neck down to the button, front up: a thin sheet standing off the torso by the blouse's and
 * blazer's ease, following the bust forward and back in again to the waist.
 */
private val LAPEL_SIDE = loop(
    p(0.000f, 0.089f), p(0.050f, 0.133f), p(0.100f, 0.152f), p(0.150f, 0.132f), p(0.200f, 0.106f), p(0.270f, 0.098f),
    p(0.270f, 0.090f), p(0.200f, 0.096f), p(0.150f, 0.122f), p(0.100f, 0.142f), p(0.050f, 0.123f), p(0.000f, 0.079f),
)

/** The lapel's width, across: broad at the top, narrowing to a point at the button. */
private val LAPEL_TOP = loop(
    p(0.000f, 0.000f), p(0.008f, 0.046f), p(0.050f, 0.044f), p(0.150f, 0.024f), p(0.250f, 0.006f), p(0.270f, 0.000f),
    p(0.250f, -0.006f), p(0.150f, -0.024f), p(0.050f, -0.044f), p(0.008f, -0.046f),
)

/** The button, a short round stub from the skin out past the blouse, so only its face shows. */
private val BUTTON_OUTLINE = loop(p(0f, 0.012f), p(0.026f, 0.012f), p(0.03f, 0f), p(0.026f, -0.012f), p(0f, -0.012f))

private const val LAPEL_RINGS = 32
private const val LAPEL_SEGMENTS = 12
private const val BUTTON_RINGS = 12
private const val BUTTON_SEGMENTS = 12
private const val UNSCALED = 1f

/**
 * The blazer's own parts: a lapel each side of its open front, lying over the chest from the neck to the button, and the button where
 * they meet, all riding the torso's bones.
 */
fun suitParts(): List<DocumentCommand> = listOf(
    cloth(LAPEL_FACING, 0.27f, 0.29f, 0.37f),
    cloth(BUTTON_HORN, 0.12f, 0.12f, 0.15f),
) + buildList {
    add(HandSockets.add(TORSO, LAPEL_SOCKET, LAPEL_U, LAPEL_V, 0f, paired = true, sink = 0f))
    val lapels = SocketRef(TORSO, HandSockets.name(LAPEL_SOCKET))
    add(ownedPair(BLAZER, "lapels", LAPEL_L to lapels, LAPEL_R to lapels, "blob"))
    add(RestDirection.restIn(LAPEL_L, RestDirection.DOWN))
    add(SetParameter(LAPEL_L, PlacementParams.OUTWARD, LAPEL_IN))
    addAll(propShape(LAPEL_L, LAPEL_SIDE, LAPEL_TOP, UNSCALED, LAPEL_RINGS, LAPEL_SEGMENTS))
    add(SetBaseSlot(LAPEL_L, LAPEL_FACING))

    add(HandSockets.add(TORSO, BUTTON_SOCKET, BUTTON_U, FRONT, 0f, paired = false, sink = 0f))
    add(owned(BLAZER, BLAZER_BUTTON, "blob", SocketRef(TORSO, HandSockets.name(BUTTON_SOCKET))))
    add(RestDirection.restIn(BLAZER_BUTTON, RestDirection.FORWARD))
    addAll(propShape(BLAZER_BUTTON, BUTTON_OUTLINE, BUTTON_OUTLINE, UNSCALED, BUTTON_RINGS, BUTTON_SEGMENTS))
    add(SetBaseSlot(BLAZER_BUTTON, BUTTON_HORN))
}
