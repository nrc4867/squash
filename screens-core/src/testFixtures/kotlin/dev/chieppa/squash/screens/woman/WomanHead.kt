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

/** Where round the round head, which rests upright with its face at v = 0, the hair's sockets lie: the back, and a little behind her left side. */
private const val HEAD_BACK = 0.5f
private const val HEAD_LEFT_BEHIND = 0.68f

/** Up the head from the chin, as shares of its drawn height, where the crown of hair, the fall and the locks sit. */
private const val CROWN_U = 0.60f
private const val FALL_U = 0.79f
private const val LOCK_U = 0.61f

/** How deep the crown, the fall and the locks sit in the skull, as shares of each one's own sink: the crown stands off it, so the hair keeps its place over a skull drawn inside it. */
private const val CROWN_SINK = -0.8f
private const val FALL_SINK = 0.3f
private const val LOCK_SINK = 0.5f

/** The fall's two joints, so it sways from the nape and again lower down. */
private val FALL_JOINTS = listOf(0.3f, 0.65f)
private val FALL_SWAY = Limits(-30f, 30f)

private const val HAIR_RINGS = 64
private const val HAIR_SEGMENTS = 32

/**
 * The head in Side, from the chin up, forward to the right of the drawing's up: her profile from the chin over the lips, the nose and the
 * brow to the crown, then down the back of the skull to the nape and forward under the jaw. Drawn from the neck's top, which lies under the
 * middle of her jaw, in the head's units.
 */
internal val HEAD_SIDE = loop(
    p(-0.070f, 0.085f), p(-0.045f, 0.112f), p(-0.010f, 0.126f), p(0.025f, 0.117f), p(0.045f, 0.127f), p(0.062f, 0.125f), p(0.078f, 0.133f),
    p(0.098f, 0.128f), p(0.118f, 0.146f), p(0.138f, 0.168f), p(0.155f, 0.172f), p(0.195f, 0.156f), p(0.235f, 0.140f), p(0.260f, 0.134f),
    p(0.295f, 0.143f), p(0.335f, 0.141f), p(0.395f, 0.122f), p(0.445f, 0.080f), p(0.480f, 0.015f), p(0.478f, -0.075f), p(0.440f, -0.170f),
    p(0.365f, -0.240f), p(0.265f, -0.250f), p(0.165f, -0.212f), p(0.085f, -0.160f), p(0.020f, -0.125f), p(-0.030f, -0.090f), p(-0.060f, -0.030f),
    p(-0.068f, 0.040f),
)

/** The head from in front, its half width up from the chin: a small chin, the jaw widening to the cheeks, the temples and a full cranium. */
internal val HEAD_TOP = loop(
    p(-0.070f, 0.000f), p(-0.067f, 0.045f), p(-0.050f, 0.085f), p(-0.015f, 0.120f), p(0.035f, 0.152f), p(0.090f, 0.174f), p(0.160f, 0.188f),
    p(0.240f, 0.196f), p(0.320f, 0.198f), p(0.400f, 0.184f), p(0.450f, 0.142f), p(0.480f, 0.060f), p(0.482f, 0.000f), p(0.480f, -0.060f),
    p(0.450f, -0.142f), p(0.400f, -0.184f), p(0.320f, -0.198f), p(0.240f, -0.196f), p(0.160f, -0.188f), p(0.090f, -0.174f), p(0.035f, -0.152f),
    p(-0.015f, -0.120f), p(-0.050f, -0.085f), p(-0.067f, -0.045f),
)

/** The head's section seen from above, her face up: a face broad across the front turning back at the cheekbones, round behind. */
internal val HEAD_FRONT = loop(
    p(0f, 1f), p(0.35f, 0.97f), p(0.62f, 0.86f), p(0.82f, 0.66f), p(0.95f, 0.38f), p(1f, 0.05f), p(0.97f, -0.3f), p(0.86f, -0.6f), p(0.64f, -0.84f),
    p(0.34f, -0.97f), p(0f, -1f), p(-0.34f, -0.97f), p(-0.64f, -0.84f), p(-0.86f, -0.6f), p(-0.97f, -0.3f), p(-1f, 0.05f), p(-0.95f, 0.38f),
    p(-0.82f, 0.66f), p(-0.62f, 0.86f), p(-0.35f, 0.97f),
)

internal val HEAD_OUTLINES = mapOf(View.SIDE to HEAD_SIDE, View.TOP to HEAD_TOP, View.FRONT to HEAD_FRONT)

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
 * The head and the hair: the shipped round head on the neck, upright with its face drawn as a profile in Side, its width from in front
 * and its section from above, whose neck joint, face direction and eye landmark a generic blob lacks; the hair as generic parts on
 * sockets placed by hand, a blob crown with a fringe, a long fall down the back with two joints, and a lock at each temple framing the face.
 */
fun womanHead(): List<DocumentCommand> = buildList {
    add(AddParts(listOf(PartInstance(HEAD, ref("head-round"), on(NECK, END_SOCKET), role = Role.HEAD))))
    add(SetOutlines(HEAD, HEAD_OUTLINES))
    add(SetParameter(HEAD, "size", HEAD_SIZE))
    add(HandSockets.add(HEAD, CROWN_SOCKET, CROWN_U, HEAD_BACK, 0f, paired = false, sink = CROWN_SINK))
    add(HandSockets.add(HEAD, FALL_SOCKET, FALL_U, HEAD_BACK, 0f, paired = false, sink = FALL_SINK))
    add(HandSockets.add(HEAD, LOCK_SOCKET, LOCK_U, HEAD_LEFT_BEHIND, 0f, paired = true, sink = LOCK_SINK))

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
