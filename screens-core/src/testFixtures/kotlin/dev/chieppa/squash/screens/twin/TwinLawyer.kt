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
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.fennec.loop
import dev.chieppa.squash.screens.fennec.p
import dev.chieppa.squash.screens.woman.BOTTOM_SLOT
import dev.chieppa.squash.screens.woman.OUTER_SLOT
import dev.chieppa.squash.screens.woman.TOP_SLOT
import dev.chieppa.squash.screens.woman.owned
import dev.chieppa.squash.screens.woman.ownedPair
import dev.chieppa.squash.screens.woman.propShape

val BLOUSE = GarmentId("blouse")
val BLAZER = GarmentId("blazer")
val TROUSERS = GarmentId("trousers")
val LAPEL_L = PartId("lapel-l")
val LAPEL_R = PartId("lapel-r")
val BLAZER_BUTTON = PartId("blazer-button")

val BLOUSE_SILK = SlotName("blouse")
val SUIT = SlotName("suit")
val LAPEL_FACING = SlotName("lapel")
val BUTTON_HORN = SlotName("button")

/** The blouse from the hips to the neck, close; the blazer over it from below the seat, open down the front; both fitted to the reference's offsets. */
private const val BLOUSE_FROM = 0.97f
private const val BLOUSE_EASE = 0.0045f
private const val BLAZER_FROM = 0.795f
private const val BLAZER_EASE = 0.0135f
private const val OPEN_FRONT = 0.05f
private const val SLEEVE_EASE = 0.0115f
private const val SLEEVE_SHORT_OF_WRIST = 0.028f

/** The trousers: the waist to just under the navel, the legs to the ankle, widening below the knee to a wide leg. */
private const val TROUSERS_TOP = 1.0f
private const val TROUSERS_EASE = 0.009f
private const val TROUSERS_HEM = 0.075f

/**
 * A trouser leg along the leg from the pelvis, its half depth in Side and half width in Top: the leg's own with the waist's ease to below
 * the knee, then flaring wider at the sides than front and back to the hem.
 */
private val TROUSER_SIDE = loop(
    p(0f, 0.094f), p(0.118f, 0.082f), p(0.213f, 0.068f), p(0.290f, 0.061f), p(0.368f, 0.068f), p(0.477f, 0.064f), p(0.560f, 0.060f),
    p(0.626f, 0.061f), p(0.671f, 0.061f), p(0.671f, -0.061f), p(0.626f, -0.061f), p(0.560f, -0.062f), p(0.477f, -0.066f), p(0.368f, -0.077f),
    p(0.290f, -0.062f), p(0.213f, -0.068f), p(0.118f, -0.082f), p(0f, -0.094f),
)
private val TROUSER_TOP = loop(
    p(0f, 0.094f), p(0.118f, 0.083f), p(0.213f, 0.066f), p(0.290f, 0.060f), p(0.368f, 0.066f), p(0.477f, 0.064f), p(0.560f, 0.068f),
    p(0.626f, 0.073f), p(0.671f, 0.073f), p(0.671f, -0.073f), p(0.626f, -0.073f), p(0.560f, -0.068f), p(0.477f, -0.064f), p(0.368f, -0.066f),
    p(0.290f, -0.060f), p(0.213f, -0.066f), p(0.118f, -0.083f), p(0f, -0.094f),
)

/** The torso's sockets for the blazer's parts: each lapel's top beside the neck, and the button where the lapels meet. */
private const val LAPEL_SOCKET = 4
private const val BUTTON_SOCKET = 5
private const val LAPEL_TOP = 1.36f
private const val LAPEL_V = 0.80f
private const val BUTTON_HEIGHT = 1.075f
private const val FRONT = 0f
private const val LAPEL_IN = -18f

/**
 * A lapel in Side, from its top beside the neck down to the button, front up: a thin sheet the blazer's offset off the chest, out over the
 * bust and back in to the waist.
 */
private val LAPEL_SIDE = loop(
    p(0.000f, 0.048f), p(0.070f, 0.092f), p(0.130f, 0.137f), p(0.180f, 0.157f), p(0.250f, 0.117f), p(0.300f, 0.097f),
    p(0.300f, 0.091f), p(0.250f, 0.111f), p(0.180f, 0.151f), p(0.130f, 0.131f), p(0.070f, 0.086f), p(0.000f, 0.042f),
)

/** The lapel's width: 0.046 across at the top, narrowing to a point at the button. */
private val LAPEL_WIDTH = loop(p(0f, 0f), p(0.006f, 0.023f), p(0.100f, 0.023f), p(0.200f, 0.012f), p(0.300f, 0f), p(0.200f, -0.012f), p(0.100f, -0.023f), p(0.006f, -0.023f))

/** The button, a short stub from the skin out past the blazer, 0.0085 round, so only its face shows. */
private val BUTTON = loop(p(0f, 0.0085f), p(0.026f, 0.0085f), p(0.030f, 0f), p(0.026f, -0.0085f), p(0f, -0.0085f))

private const val LAPEL_RINGS = 32
private const val LAPEL_SEGMENTS = 12
private const val BUTTON_RINGS = 12
private const val BUTTON_SEGMENTS = 12

/** The office outfit: a blouse, a navy blazer with lapels and a button, and wide-leg trousers; the pumps are [twinPumps]. */
fun twinLawyer(): List<DocumentCommand> =
    listOf(cloth(BLOUSE_SILK, 0xF4EFE4), cloth(SUIT, 0x39425E), cloth(LAPEL_FACING, 0x4A5679), cloth(BUTTON_HORN, 0x1F2233)) +
        twinGarment(
            BLOUSE, "Blouse", TOP_SLOT, BLOUSE_SILK, listOf(GarmentBranch(TORSO, torsoU(BLOUSE_FROM), 1f, ease = BLOUSE_EASE)),
            changes = mapOf(ARM_L to OPEN, NECK to OPEN),
        ) +
        twinGarment(
            BLAZER, "Blazer", OUTER_SLOT, SUIT,
            listOf(
                GarmentBranch(TORSO, torsoU(BLAZER_FROM), 1f, ease = BLAZER_EASE, fromV = OPEN_FRONT, toV = 1f - OPEN_FRONT),
                GarmentBranch(ARM_L, 0f, armU(SLEEVE_SHORT_OF_WRIST), ease = SLEEVE_EASE),
            ),
            layer = OVER_LAYER,
        ) +
        twinGarment(
            TROUSERS, "Trousers", BOTTOM_SLOT, SUIT,
            listOf(
                GarmentBranch(TORSO, 0f, torsoU(TROUSERS_TOP), ease = TROUSERS_EASE),
                GarmentBranch(LEG_L, INTO_PELVIS, legU(TROUSERS_HEM), ease = TROUSERS_EASE, outlines = mapOf(View.SIDE to TROUSER_SIDE, View.TOP to TROUSER_TOP)),
            ),
            layer = WAIST_LAYER,
        ) + buildList {
            add(HandSockets.add(TORSO, LAPEL_SOCKET, torsoU(LAPEL_TOP), LAPEL_V, 0f, paired = true, sink = 0f))
            val lapels = SocketRef(TORSO, HandSockets.name(LAPEL_SOCKET))
            add(ownedPair(BLAZER, "lapels", LAPEL_L to lapels, LAPEL_R to lapels, "blob"))
            add(RestDirection.restIn(LAPEL_L, RestDirection.DOWN))
            add(SetParameter(LAPEL_L, PlacementParams.OUTWARD, LAPEL_IN))
            addAll(propShape(LAPEL_L, LAPEL_SIDE, LAPEL_WIDTH, 1f, LAPEL_RINGS, LAPEL_SEGMENTS))
            add(SetBaseSlot(LAPEL_L, LAPEL_FACING))

            add(HandSockets.add(TORSO, BUTTON_SOCKET, torsoU(BUTTON_HEIGHT), FRONT, 0f, paired = false, sink = 0f))
            add(owned(BLAZER, BLAZER_BUTTON, "blob", SocketRef(TORSO, HandSockets.name(BUTTON_SOCKET))))
            add(RestDirection.restIn(BLAZER_BUTTON, RestDirection.FORWARD))
            addAll(propShape(BLAZER_BUTTON, BUTTON, BUTTON, 1f, BUTTON_RINGS, BUTTON_SEGMENTS))
            add(SetBaseSlot(BLAZER_BUTTON, BUTTON_HORN))
        }
