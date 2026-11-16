package dev.chieppa.squash.screens.woman

import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.RestDirection
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Region
import dev.chieppa.squash.document.RegionId
import dev.chieppa.squash.document.SetBaseSlot
import dev.chieppa.squash.document.SetRegion
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.screens.fennec.loop
import dev.chieppa.squash.screens.fennec.p

val SHADES = GarmentId("shades")
val SHADES_L = PartId("shades-l")
val SHADES_R = PartId("shades-r")
val TEMPLE_L = PartId("temple-l")
val TEMPLE_R = PartId("temple-r")

val FRAME = SlotName("frame")
val LENS = SlotName("lens")
val GLINT = SlotName("glint")

/**
 * The head's socket the shades sit on: one paired socket at the bridge of the nose, between the eyes, which both halves share. It takes
 * the first id past the hair's, since placing a socket on an id in use moves the parts already on it.
 */
private const val BRIDGE_SOCKET = FALL_OUTER_SOCKET + 1
private val BRIDGE by lazy { FACE.on(0f, BRIDGE_HEIGHT) }

/** How high up the face the bridge sits, on the nose between the eyes. */
private const val BRIDGE_HEIGHT = 0.245f

/** The socket at each half's outer end that its temple runs back from, near the frame's top. */
private const val HINGE_SOCKET = 1
private const val HINGE_U = 0.94f
private const val HINGE_V = 0.08f

/** How deep each half sits at the bridge, as a share of its template's depth: 0 seats it on the face, below 0 stands it off. */
private const val BRIDGE_SINK = 0f
private const val SHADE_RINGS = 48
private const val SHADE_SEGMENTS = 16
private const val TEMPLE_RINGS = 16
private const val TEMPLE_SEGMENTS = 8

/**
 * One half of the shades in Side, from the middle of the bridge out past the outer corner of her eye, up at the top: a low bridge rising into a
 * squarish lens, drawn to the head's scale.
 */
private val HALF_SIDE = loop(
    p(0.000f, 0.010f), p(0.012f, 0.013f), p(0.022f, 0.032f), p(0.034f, 0.046f), p(0.080f, 0.050f), p(0.128f, 0.048f), p(0.146f, 0.038f),
    p(0.152f, 0.000f), p(0.146f, -0.032f), p(0.128f, -0.040f), p(0.080f, -0.043f), p(0.034f, -0.040f), p(0.022f, -0.028f), p(0.012f, -0.002f),
    p(0.000f, 0.002f),
)

/** The half in Top, from the bridge out: thin, a little thicker over the lens than at the bridge. */
private val HALF_TOP = loop(
    p(0.000f, 0.004f), p(0.030f, 0.006f), p(0.140f, 0.006f), p(0.152f, 0.000f), p(0.140f, -0.006f), p(0.030f, -0.006f), p(0.000f, -0.004f),
)

/** A temple from the hinge back past the ear into the hair, thin, tapering a little. */
private val TEMPLE_SIDE = loop(p(0f, 0.006f), p(0.12f, 0.005f), p(0.26f, 0.004f), p(0.27f, 0f), p(0.26f, -0.004f), p(0.12f, -0.005f), p(0f, -0.006f))
private val TEMPLE_TOP = loop(p(0f, 0.004f), p(0.12f, 0.004f), p(0.26f, 0.003f), p(0.27f, 0f), p(0.26f, -0.003f), p(0.12f, -0.004f), p(0f, -0.004f))

/** Where on each half its lens shows inside the frame, round the half's front, which faces a half resting out at v = 0.75. */
private const val LENS_FROM_U = 0.26f
private const val LENS_TO_U = 0.93f
private const val LENS_FROM_V = 0.6f
private const val LENS_TO_V = 0.9f

private fun anchor(u: Float, v: Float) = Anchor(SHADES_L, u = u, v = v)

private val LENS_REGION = Region(
    RegionId("lens"),
    listOf(anchor(LENS_FROM_U, LENS_FROM_V), anchor(LENS_TO_U, LENS_FROM_V), anchor(LENS_TO_U, LENS_TO_V), anchor(LENS_FROM_U, LENS_TO_V)),
    LENS,
)

/** A slanting glint across the lens, a narrow band from low inside to high outside. */
private val GLINT_REGION = Region(
    RegionId("glint"),
    listOf(anchor(0.48f, 0.66f), anchor(0.54f, 0.66f), anchor(0.70f, 0.84f), anchor(0.64f, 0.84f)),
    GLINT,
)


/**
 * The shades: a garment of parts it owns and no shell. Two halves meeting at the bridge, each a frame of one piece standing off the face
 * with its lens painted inside it, and a temple from each half's outer end back over the head, all riding the head's bone.
 */
fun shades(): List<DocumentCommand> = listOf(
    cloth(FRAME, 0.10f, 0.08f, 0.09f),
    cloth(LENS, 0.26f, 0.25f, 0.30f),
    cloth(GLINT, 0.62f, 0.62f, 0.66f),
) + garment(SHADES, "Shades", EYES_SLOT, FRAME, emptyList()) + buildList {
    add(HandSockets.add(HEAD, BRIDGE_SOCKET, BRIDGE.u, BRIDGE.v, 0f, paired = true, sink = BRIDGE_SINK))
    val bridge = SocketRef(HEAD, HandSockets.name(BRIDGE_SOCKET))
    add(ownedPair(SHADES, "shades", SHADES_L to bridge, SHADES_R to bridge, "blob"))
    add(RestDirection.restIn(SHADES_L, RestDirection.OUT))
    addAll(propShape(SHADES_L, HALF_SIDE, HALF_TOP, HEAD_SIZE, SHADE_RINGS, SHADE_SEGMENTS))
    add(SetBaseSlot(SHADES_L, FRAME))
    add(SetRegion(SHADES_L, LENS_REGION.id, LENS_REGION))
    add(SetRegion(SHADES_L, GLINT_REGION.id, GLINT_REGION))
    add(HandSockets.add(SHADES_L, HINGE_SOCKET, HINGE_U, HINGE_V, 0f, paired = false, sink = 0f))
    val hinge = HandSockets.name(HINGE_SOCKET)
    add(ownedPair(SHADES, "temples", TEMPLE_L to SocketRef(SHADES_L, hinge), TEMPLE_R to SocketRef(SHADES_R, hinge), "tube"))
    add(RestDirection.restIn(TEMPLE_L, RestDirection.BACK))
    addAll(propShape(TEMPLE_L, TEMPLE_SIDE, TEMPLE_TOP, HEAD_SIZE, TEMPLE_RINGS, TEMPLE_SEGMENTS))
    add(SetBaseSlot(TEMPLE_L, FRAME))
}
