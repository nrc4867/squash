package dev.chieppa.squash.screens.twin

import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SetOutlines
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.fennec.loop
import dev.chieppa.squash.screens.fennec.p
import dev.chieppa.squash.screens.fennec.ref

val HEAD = PartId("head")

/** The head's scale about its origin on the neck: drawn in metres as the reference's unscaled head, shown 1.1 times as large. */
const val HEAD_SIZE = 1.1f

/** The head's drawn extent up its axis, from under the chin to the crown, in its drawn units from the neck's top. */
internal const val CHIN = -0.030f
internal const val CROWN = 0.2173f

/** Up the head from the chin, as a share of its drawn height, of a place [height] above the neck's top. */
internal fun headU(height: Float) = (height - CHIN) / (CROWN - CHIN)

/**
 * The head in Side, from under the chin, forward up in the drawing: up the front over the chin, the lips, under the nose to its tip, the
 * nasion and the brow to the forehead and the crown, down the round back of the skull to the nape and forward under the jaw.
 */
internal val HEAD_SIDE = loop(
    p(-0.030f, 0.060f), p(-0.0227f, 0.080f), p(-0.012f, 0.083f), p(0.004f, 0.085f), p(0.0093f, 0.098f), p(0.0147f, 0.105f), p(0.0253f, 0.103f),
    p(0.036f, 0.101f), p(0.0413f, 0.103f), p(0.0467f, 0.110f), p(0.052f, 0.120f), p(0.0573f, 0.123f), p(0.063f, 0.122f), p(0.068f, 0.120f),
    p(0.0787f, 0.115f), p(0.0947f, 0.110f), p(0.1107f, 0.105f), p(0.132f, 0.101f), p(0.153f, 0.093f), p(0.1747f, 0.080f), p(0.1907f, 0.065f),
    p(0.2013f, 0.0513f), p(0.212f, 0.030f), p(0.2173f, 0.006f), p(0.212f, -0.040f), p(0.2013f, -0.0637f), p(0.1907f, -0.077f), p(0.1747f, -0.092f),
    p(0.153f, -0.1037f), p(0.132f, -0.1103f), p(0.1107f, -0.112f), p(0.0893f, -0.1087f), p(0.068f, -0.1003f), p(0.0467f, -0.0887f), p(0.0253f, -0.0737f),
    p(0.004f, -0.057f), p(-0.012f, -0.045f), p(-0.0173f, -0.0287f), p(-0.0227f, -0.017f), p(-0.028f, 0.011f),
)

/** The head from in front, its half width up from the chin: a narrow chin, the jaw widening to the cheeks, the temples and the cranium. */
internal val HEAD_TOP = loop(
    p(-0.030f, 0.000f), p(-0.028f, 0.014f), p(-0.017f, 0.025f), p(-0.007f, 0.036f), p(0.004f, 0.046f), p(0.015f, 0.058f), p(0.025f, 0.069f),
    p(0.036f, 0.077f), p(0.047f, 0.082f), p(0.057f, 0.088f), p(0.068f, 0.091f), p(0.079f, 0.094f), p(0.095f, 0.0965f), p(0.111f, 0.097f),
    p(0.132f, 0.095f), p(0.153f, 0.090f), p(0.175f, 0.078f), p(0.190f, 0.064f), p(0.207f, 0.043f), p(0.2173f, 0.000f), p(0.207f, -0.043f),
    p(0.190f, -0.064f), p(0.175f, -0.078f), p(0.153f, -0.090f), p(0.132f, -0.095f), p(0.111f, -0.097f), p(0.095f, -0.0965f), p(0.079f, -0.094f),
    p(0.068f, -0.091f), p(0.057f, -0.088f), p(0.047f, -0.082f), p(0.036f, -0.077f), p(0.025f, -0.069f), p(0.015f, -0.058f), p(0.004f, -0.046f),
    p(-0.007f, -0.036f), p(-0.017f, -0.025f), p(-0.028f, -0.014f),
)

/** The head's section at the eyes seen from above, face up: broad and flat across the face, the cheekbones turning back, longer behind. */
internal val HEAD_FRONT = loop(
    p(0f, 1f), p(0.356f, 0.910f), p(0.700f, 0.784f), p(0.911f, 0.517f), p(0.985f, 0.212f), p(1f, -0.091f), p(0.917f, -0.397f), p(0.731f, -0.693f),
    p(0.413f, -0.923f), p(0f, -1f), p(-0.413f, -0.923f), p(-0.731f, -0.693f), p(-0.917f, -0.397f), p(-1f, -0.091f), p(-0.985f, 0.212f),
    p(-0.911f, 0.517f), p(-0.700f, 0.784f), p(-0.356f, 0.910f),
)

internal val HEAD_OUTLINES = mapOf(View.SIDE to HEAD_SIDE, View.TOP to HEAD_TOP, View.FRONT to HEAD_FRONT)

private fun on(part: PartId, socket: Int) = SocketRef(part, HandSockets.name(socket))

/** The round head on the neck's end, upright with its profile in Side, at the reference's head scale. */
fun twinHead(): List<DocumentCommand> = listOf(
    AddParts(listOf(PartInstance(HEAD, ref("head-round"), on(NECK, END_SOCKET), role = Role.HEAD))),
    SetOutlines(HEAD, HEAD_OUTLINES),
    SetParameter(HEAD, "size", HEAD_SIZE),
)
