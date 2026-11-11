package dev.chieppa.squash.screens.woman

import dev.chieppa.squash.character.build.GenericParams
import dev.chieppa.squash.character.build.HandJoints
import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.RestDirection
import dev.chieppa.squash.character.model.Hinge
import dev.chieppa.squash.character.model.Limits
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkWidth
import dev.chieppa.squash.document.SetMark
import dev.chieppa.squash.document.WidthStop
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.PairId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SetOutlines
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.SetRole
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.View
import kotlin.math.sin
import dev.chieppa.squash.screens.fennec.loop
import dev.chieppa.squash.screens.fennec.p
import dev.chieppa.squash.screens.fennec.ref
import dev.chieppa.squash.screens.fennec.tuft

val HEAD = PartId("head")
val HAIR = PartId("hair")
val HAIR_FALL = PartId("hair-fall")
val LOCK_L = PartId("lock-l")
val LOCK_R = PartId("lock-r")

/** The head's hand-made sockets: the crown at the back of the skull, the fall behind it and the locks at the temples. */
const val CROWN_SOCKET = 1
const val FALL_SOCKET = 2
const val LOCK_SOCKET = 3

/** How big the round head is beside its template, and the hair drawn to the same scale. */
const val HEAD_SIZE = 0.68f

/** Where round the round head, which rests forward, its left side lies. */
private const val HEAD_LEFT = 0.27f

/** The fall's two joints, so it sways from the nape and again lower down. */
private val FALL_JOINTS = listOf(0.3f, 0.65f)
private val FALL_SWAY = Limits(-30f, 30f)

private const val HAIR_RINGS = 64
private const val HAIR_SEGMENTS = 32

/**
 * The round head in Side, the back of the skull at the left and the face at the right, drawn to the template's length so its mount
 * lands under its middle: a high crown, the jaw running forward to a small chin under the face.
 */
private val HEAD_SIDE = loop(
    p(0.000f, 0.030f), p(0.030f, 0.150f), p(0.110f, 0.235f), p(0.230f, 0.265f), p(0.340f, 0.250f), p(0.420f, 0.200f), p(0.470f, 0.130f),
    p(0.495f, 0.050f), p(0.500f, -0.010f), p(0.493f, -0.080f), p(0.470f, -0.150f), p(0.425f, -0.210f), p(0.360f, -0.235f), p(0.270f, -0.200f),
    p(0.150f, -0.160f), p(0.050f, -0.090f),
)
private val HEAD_TOP = loop(
    p(0.000f, 0.000f), p(0.030f, 0.130f), p(0.130f, 0.205f), p(0.260f, 0.225f), p(0.370f, 0.210f), p(0.440f, 0.170f), p(0.485f, 0.100f),
    p(0.500f, 0.000f), p(0.485f, -0.100f), p(0.440f, -0.170f), p(0.370f, -0.210f), p(0.260f, -0.225f), p(0.130f, -0.205f), p(0.030f, -0.130f),
)

/** The head's section, seen from in front: a full cranium narrowing through the cheeks to the chin. */
private val HEAD_FRONT = loop(
    p(0f, 1f), p(0.6f, 0.88f), p(0.93f, 0.5f), p(1f, 0.05f), p(0.9f, -0.4f), p(0.64f, -0.74f), p(0.32f, -0.95f), p(0f, -1f),
    p(-0.32f, -0.95f), p(-0.64f, -0.74f), p(-0.9f, -0.4f), p(-1f, 0.05f), p(-0.93f, 0.5f), p(-0.6f, 0.88f),
)

/** The crown of hair from the back of the skull over the top to a swept fringe on the brow, hugging the skull's curve. */
private val HAIR_SIDE = loop(
    p(0.000f, 0.000f), p(0.012f, 0.160f), p(0.090f, 0.262f), p(0.210f, 0.292f), p(0.320f, 0.282f), p(0.390f, 0.240f), tuft(0.430f, 0.190f),
    p(0.400f, 0.040f), p(0.360f, -0.190f), p(0.270f, -0.240f), p(0.140f, -0.240f), p(0.040f, -0.170f),
)
private val HAIR_TOP = loop(
    p(0.000f, 0.000f), p(0.012f, 0.140f), p(0.090f, 0.228f), p(0.230f, 0.245f), p(0.330f, 0.236f), p(0.390f, 0.210f), p(0.420f, 0.120f),
    p(0.430f, 0.000f), p(0.420f, -0.120f), p(0.390f, -0.210f), p(0.330f, -0.236f), p(0.230f, -0.245f), p(0.090f, -0.228f), p(0.012f, -0.140f),
)

/** The fall down the back, from the crown to below the shoulder blades, front up in Side, ending in points. */
private val FALL_SIDE = loop(
    p(0.000f, 0.090f), p(0.200f, 0.080f), p(0.450f, 0.050f), p(0.700f, 0.020f), p(0.880f, -0.010f), tuft(0.950f, -0.040f),
    p(0.880f, -0.075f), p(0.700f, -0.080f), p(0.450f, -0.070f), p(0.200f, -0.060f), p(0.050f, -0.040f),
)
private val FALL_TOP = loop(
    p(0.000f, 0.000f), p(0.060f, 0.170f), p(0.250f, 0.230f), p(0.500f, 0.250f), p(0.750f, 0.220f), p(0.880f, 0.150f), tuft(0.960f, 0.080f),
    p(0.920f, 0.030f), tuft(0.950f, 0.000f), p(0.920f, -0.030f), tuft(0.960f, -0.080f), p(0.880f, -0.150f), p(0.750f, -0.220f),
    p(0.500f, -0.250f), p(0.250f, -0.230f), p(0.060f, -0.170f),
)

/** A lock framing the face, from the temple past the jaw to the collarbone, thin across and tapering to a point. */
private val LOCK_SIDE = loop(
    p(0.000f, 0.060f), p(0.200f, 0.070f), p(0.420f, 0.062f), p(0.600f, 0.040f), tuft(0.720f, 0.000f), p(0.600f, -0.045f), p(0.420f, -0.060f),
    p(0.200f, -0.065f), p(0.000f, -0.055f),
)
private val LOCK_TOP = loop(
    p(0.000f, 0.050f), p(0.200f, 0.048f), p(0.450f, 0.040f), p(0.620f, 0.024f), tuft(0.700f, 0.000f), p(0.620f, -0.024f), p(0.450f, -0.040f),
    p(0.200f, -0.048f), p(0.000f, -0.050f),
)

/** Where strands run down the fall and over the crown, round each part, and how far along it. */
private val FALL_STRANDS = listOf(0.38f, 0.44f, 0.5f, 0.56f, 0.62f)
private val CROWN_STRANDS = listOf(0.08f, 0.16f, 0.84f, 0.92f)
private val STRAND_WIDTH = MarkWidth(listOf(WidthStop(0f, 0.2f), WidthStop(0.4f, 0.8f), WidthStop(1f, 0.15f)))

/** A strand line down [part] at [v], from [from] to [to] along it, wavering a little round it. */
private fun strand(part: PartId, name: String, v: Float, from: Float, to: Float): DocumentCommand {
    val points = List(STRAND_POINTS) { i ->
        val share = i.toFloat() / (STRAND_POINTS - 1)
        Anchor(part, BODY_PATCH, from + (to - from) * share, v + STRAND_WAVER * sin(share * 3f + v * 20f))
    }
    val id = MarkId("$name-${(v * 100).toInt()}")
    return SetMark(part, id, Mark(id, points, STRAND_WIDTH))
}

private const val STRAND_POINTS = 6
private const val STRAND_WAVER = 0.012f

/** Ink strands down the fall's back and over the crown's sides, so the hair reads as strands and not a cap. */
private fun strands(): List<DocumentCommand> =
    FALL_STRANDS.map { strand(HAIR_FALL, "strand", it, 0.15f, 0.9f) } + CROWN_STRANDS.map { strand(HAIR, "strand", it, 0.25f, 0.85f) }

private fun on(part: PartId, socket: Int) = SocketRef(part, HandSockets.name(socket))

/** A generic hair part drawn at the head's scale and lofted fine. */
private fun hairShape(part: PartId, side: dev.chieppa.squash.document.OutlineCurve, top: dev.chieppa.squash.document.OutlineCurve) = listOf(
    SetRole(part, Role.OTHER),
    SetOutlines(part, mapOf(View.SIDE to side, View.TOP to top)),
    SetParameter(part, "size", HEAD_SIZE),
    GenericParams.rings(part, HAIR_RINGS),
    GenericParams.segments(part, HAIR_SEGMENTS),
)

/**
 * The head and the hair: the shipped round head on the neck, whose mount, neck joint, jaw and eye landmark a generic blob lacks, drawn
 * in Side, Top and from in front; the hair as generic parts on sockets placed by hand, a blob crown with a fringe, a long fall down
 * the back with two joints, and a lock at each temple framing the face.
 */
fun womanHead(): List<DocumentCommand> = buildList {
    add(AddParts(listOf(PartInstance(HEAD, ref("head-round"), on(NECK, END_SOCKET), role = Role.HEAD))))
    add(SetOutlines(HEAD, mapOf(View.SIDE to HEAD_SIDE, View.TOP to HEAD_TOP, View.FRONT to HEAD_FRONT)))
    add(SetParameter(HEAD, "size", HEAD_SIZE))
    add(HandSockets.add(HEAD, CROWN_SOCKET, 0f, 0f, 0f, paired = false))
    add(HandSockets.add(HEAD, FALL_SOCKET, 0.12f, 0f, 0f, paired = false))
    add(HandSockets.add(HEAD, LOCK_SOCKET, 0.4f, HEAD_LEFT, 0f, paired = true))

    add(AddParts(listOf(PartInstance(HAIR, ref("blob"), on(HEAD, CROWN_SOCKET), role = Role.OTHER, roleGuessed = true))))
    addAll(hairShape(HAIR, HAIR_SIDE, HAIR_TOP))

    add(AddParts(listOf(PartInstance(HAIR_FALL, ref("tube"), on(HEAD, FALL_SOCKET), role = Role.OTHER, roleGuessed = true))))
    add(RestDirection.restIn(HAIR_FALL, RestDirection.DOWN))
    addAll(hairShape(HAIR_FALL, FALL_SIDE, FALL_TOP))
    FALL_JOINTS.forEachIndexed { i, u -> add(HandJoints.add(HAIR_FALL, i + 1, u, Hinge.X, FALL_SWAY, stretch = false)) }

    add(
        AddParts(
            listOf(
                PartInstance(LOCK_L, ref("tube"), on(HEAD, LOCK_SOCKET), pair = PairId("locks"), side = Side.LEFT, role = Role.OTHER, roleGuessed = true),
                PartInstance(LOCK_R, ref("tube"), on(HEAD, LOCK_SOCKET), pair = PairId("locks"), side = Side.RIGHT, role = Role.OTHER, roleGuessed = true),
            ),
        ),
    )
    add(RestDirection.restIn(LOCK_L, RestDirection.DOWN))
    addAll(hairShape(LOCK_L, LOCK_SIDE, LOCK_TOP))
    addAll(strands())
}
