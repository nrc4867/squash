package dev.chieppa.squash.screens.twin

import dev.chieppa.squash.character.build.GenericParams
import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PairId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SetOutlines
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.fennec.loop
import dev.chieppa.squash.screens.fennec.p
import dev.chieppa.squash.screens.fennec.ref
import dev.chieppa.squash.screens.fennec.tuft

val HAIR = PartId("hair")
val HAIR_FALL = PartId("hair-fall")
val LOCK_L = PartId("lock-l")
val LOCK_R = PartId("lock-r")

/** The head's hand-made sockets for the hair: the cap at the back of the skull, the fall below it and the locks beside the face. */
const val CAP_SOCKET = 1
const val FALL_SOCKET = 2
const val LOCK_SOCKET = 3

/** Round the upright head: the back, and her left. */
private const val HEAD_BACK = 0.5f
private const val HEAD_LEFT = 0.75f

/** Heights above the neck's top, in the head's drawn units, of the cap's, the fall's and the locks' sockets. */
private const val CAP_HEIGHT = 0.12f
private const val FALL_HEIGHT = 0.05f
private const val LOCK_HEIGHT = 0.06f

/** The sockets seat each hair part on the skull rather than sinking it in. */
private const val ON_SKULL = 0f

private const val HAIR_RINGS = 64
private const val HAIR_SEGMENTS = 32

/**
 * The cap in Side, forward from the back of the skull where its socket sits, up at the top: the shell a little off the cranium over the
 * back and the crown, down over the forehead to the bangs' ends above the brow, then back under along the hairline, over the temples and
 * the ears to the nape.
 */
private val CAP_SIDE = loop(
    p(-0.020f, 0.000f), p(-0.017f, 0.040f), p(-0.010f, 0.060f), p(0.002f, 0.076f), p(0.020f, 0.097f), p(0.037f, 0.108f), p(0.065f, 0.116f),
    p(0.095f, 0.119f), p(0.125f, 0.116f), p(0.153f, 0.108f), p(0.170f, 0.097f), p(0.183f, 0.087f), p(0.193f, 0.076f), p(0.202f, 0.062f),
    p(0.210f, 0.048f), p(0.222f, 0.030f), tuft(0.232f, 0.002f), p(0.205f, -0.010f), p(0.150f, -0.045f), p(0.090f, -0.080f), p(0.035f, -0.110f),
    p(0.005f, -0.118f), p(-0.012f, -0.090f), p(-0.018f, -0.045f),
)

/** The cap's half width from the back forward: round behind, widest over the ears, narrowing over the forehead. */
private val CAP_TOP = loop(
    p(-0.020f, 0.000f), p(-0.015f, 0.045f), p(0.000f, 0.075f), p(0.030f, 0.097f), p(0.070f, 0.108f), p(0.120f, 0.111f), p(0.170f, 0.104f),
    p(0.200f, 0.090f), p(0.222f, 0.065f), p(0.232f, 0.000f), p(0.222f, -0.065f), p(0.200f, -0.090f), p(0.170f, -0.104f), p(0.120f, -0.111f),
    p(0.070f, -0.108f), p(0.030f, -0.097f), p(0.000f, -0.075f), p(-0.015f, -0.045f),
)

/** The long hair down the back in Side, from the back of the head down, forward up: thick under the cap, falling back clear of the back and in at the ends. */
private val FALL_SIDE = loop(
    p(0.000f, 0.000f), p(0.100f, -0.010f), p(0.200f, -0.040f), p(0.300f, -0.050f), tuft(0.380f, -0.060f),
    p(0.300f, -0.085f), p(0.200f, -0.085f), p(0.100f, -0.070f), p(0.000f, -0.040f),
)

/** The fall's width: as wide as the head behind it, ending in uneven points. */
private val FALL_TOP = loop(
    p(0.000f, 0.070f), p(0.050f, 0.100f), p(0.160f, 0.115f), p(0.270f, 0.118f), p(0.330f, 0.110f), tuft(0.370f, 0.080f), p(0.350f, 0.050f),
    tuft(0.390f, 0.020f), p(0.360f, -0.010f), tuft(0.380f, -0.040f), p(0.340f, -0.070f), tuft(0.360f, -0.100f), p(0.330f, -0.110f),
    p(0.270f, -0.118f), p(0.160f, -0.115f), p(0.050f, -0.100f), p(0.000f, -0.070f),
)

/**
 * A curtain of hair each side, from beside the temple down past the jaw to just above the shoulder, clear of it in every pose, framing
 * the face in front and running back to the fall behind the ear: broad front to back in Side, thin across in Top.
 */
private val LOCK_SIDE = loop(
    p(0.000f, 0.010f), p(0.052f, 0.008f), p(0.104f, 0.000f), tuft(0.150f, -0.010f), p(0.130f, -0.040f), tuft(0.150f, -0.075f), p(0.117f, -0.120f),
    p(0.052f, -0.130f), p(0.000f, -0.110f),
)
private val LOCK_TOP = loop(p(0.000f, 0.012f), p(0.065f, 0.012f), p(0.130f, 0.008f), tuft(0.150f, 0.000f), p(0.130f, -0.008f), p(0.065f, -0.012f), p(0.000f, -0.012f))

private fun on(part: PartId, socket: Int) = SocketRef(part, HandSockets.name(socket))

private fun hairShape(part: PartId, side: OutlineCurve, top: OutlineCurve) = listOf(
    SetOutlines(part, mapOf(View.SIDE to side, View.TOP to top)),
    SetParameter(part, "size", HEAD_SIZE),
    GenericParams.rings(part, HAIR_RINGS),
    GenericParams.segments(part, HAIR_SEGMENTS),
)

/**
 * The hair, drawn in the head's units as the reference's, as hair parts: a cap over the skull with side-swept bangs in the head's field,
 * riding the head's bone, and a long fall down the back to the middle of it and a lock each side framing the face, each its own mesh on its
 * own chain of bones.
 */
fun twinHair(): List<DocumentCommand> = buildList {
    add(HandSockets.add(HEAD, CAP_SOCKET, headU(CAP_HEIGHT), HEAD_BACK, 0f, paired = false, sink = ON_SKULL))
    add(HandSockets.add(HEAD, FALL_SOCKET, headU(FALL_HEIGHT), HEAD_BACK, 0f, paired = false, sink = ON_SKULL))
    add(HandSockets.add(HEAD, LOCK_SOCKET, headU(LOCK_HEIGHT), HEAD_LEFT, 0f, paired = true, sink = ON_SKULL))

    add(AddParts(listOf(PartInstance(HAIR, ref("hair-cap"), on(HEAD, CAP_SOCKET), role = Role.HAIR))))
    addAll(hairShape(HAIR, CAP_SIDE, CAP_TOP))

    add(AddParts(listOf(PartInstance(HAIR_FALL, ref("hair"), on(HEAD, FALL_SOCKET), role = Role.HAIR))))
    addAll(hairShape(HAIR_FALL, FALL_SIDE, FALL_TOP))

    add(
        AddParts(
            listOf(
                PartInstance(LOCK_L, ref("hair"), on(HEAD, LOCK_SOCKET), pair = PairId("locks"), side = Side.LEFT, role = Role.HAIR),
                PartInstance(LOCK_R, ref("hair"), on(HEAD, LOCK_SOCKET), pair = PairId("locks"), side = Side.RIGHT, role = Role.HAIR),
            ),
        ),
    )
    addAll(hairShape(LOCK_L, LOCK_SIDE, LOCK_TOP))
}
