package dev.chieppa.squash.screens.twin

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
import dev.chieppa.squash.screens.woman.EYES_SLOT
import dev.chieppa.squash.screens.woman.ownedPair
import dev.chieppa.squash.screens.woman.propShape

val SHADES = GarmentId("shades")
val SHADES_L = PartId("shades-l")
val SHADES_R = PartId("shades-r")
val TEMPLE_L = PartId("temple-l")
val TEMPLE_R = PartId("temple-r")

val FRAME = SlotName("frame")
val LENS = SlotName("lens")
val GLINT = SlotName("glint")

private const val FRAME_HEX = 0x2B2023
private const val LENS_HEX = 0x3A2A30
private const val GLINT_HEX = 0x8A8085

/** The head's socket the shades sit on, at the bridge between the eyes, 0.009 above the head centre, which both halves share. */
private const val BRIDGE_SOCKET = 4
private const val BRIDGE_Y = 0.009f
private val BRIDGE by lazy { FACE.on(0f, HEAD_CENTRE + BRIDGE_Y) }

/** How far each half stands off the face at the bridge, as a share of its template's depth: below 0 stands it off, clear of the nose. */
private const val BRIDGE_SINK = -1f

/** The socket at each half's outer end, near the frame's top, that its temple runs back from. */
private const val HINGE_SOCKET = 1
private const val HINGE_U = 0.95f
private const val HINGE_V = 0.1f

private const val SHADE_RINGS = 48
private const val SHADE_SEGMENTS = 16
private const val TEMPLE_RINGS = 16
private const val TEMPLE_SEGMENTS = 8

/**
 * One half of the shades in Side, from the middle of the bridge out past the eye's outer corner, up at the top, in the head's units: the
 * bridge a little above the lens's middle, the lens 0.05 wide and 0.035 high within a frame 0.0026 thick, its outer corner lifted.
 */
private val HALF_SIDE = loop(
    p(0.000f, 0.003f), p(0.016f, 0.004f), p(0.020f, 0.010f), p(0.030f, 0.0115f), p(0.045f, 0.012f), p(0.060f, 0.0145f), p(0.068f, 0.012f),
    p(0.0722f, 0.000f), p(0.069f, -0.020f), p(0.060f, -0.0265f), p(0.045f, -0.028f), p(0.030f, -0.0275f), p(0.020f, -0.024f), p(0.016f, -0.006f),
    p(0.000f, -0.003f),
)

/** The half in Top, from the bridge out: thin, the frame's own depth. */
private val HALF_TOP = loop(p(0.000f, 0.003f), p(0.030f, 0.003f), p(0.068f, 0.003f), p(0.0722f, 0.000f), p(0.068f, -0.003f), p(0.030f, -0.003f), p(0.000f, -0.003f))

/** A temple from the hinge back past the ear into the hair, 0.0023 round. */
private val TEMPLE_SIDE = loop(p(0f, 0.0023f), p(0.15f, 0.0023f), p(0.165f, 0f), p(0.15f, -0.0023f), p(0f, -0.0023f))
private val TEMPLE_TOP = TEMPLE_SIDE

/** Where on each half its lens shows inside the frame, round the half's front, which faces a half resting out at v = 0.75. */
private const val LENS_FROM_U = 0.27f
private const val LENS_TO_U = 0.94f
private const val LENS_FROM_V = 0.6f
private const val LENS_TO_V = 0.9f

private fun anchor(u: Float, v: Float) = Anchor(SHADES_L, u = u, v = v)

private val LENS_REGION = Region(
    RegionId("lens"),
    listOf(anchor(LENS_FROM_U, LENS_FROM_V), anchor(LENS_TO_U, LENS_FROM_V), anchor(LENS_TO_U, LENS_TO_V), anchor(LENS_FROM_U, LENS_TO_V)),
    LENS,
)

/** The glint: a narrow slanting band across the lens. */
private val GLINT_REGION = Region(RegionId("glint"), listOf(anchor(0.45f, 0.66f), anchor(0.52f, 0.66f), anchor(0.70f, 0.84f), anchor(0.63f, 0.84f)), GLINT)

/** The beach shades: two halves meeting at the bridge with their lenses painted inside the frames, and a temple from each half's outer end. */
fun twinShades(): List<DocumentCommand> = listOf(cloth(FRAME, FRAME_HEX), cloth(LENS, LENS_HEX), cloth(GLINT, GLINT_HEX)) +
    twinGarment(SHADES, "Shades", EYES_SLOT, FRAME, emptyList()) + buildList {
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
