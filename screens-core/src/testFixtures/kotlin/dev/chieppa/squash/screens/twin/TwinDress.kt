package dev.chieppa.squash.screens.twin

import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.fennec.loop
import dev.chieppa.squash.screens.fennec.p
import dev.chieppa.squash.screens.woman.BOTTOM_SLOT
import dev.chieppa.squash.screens.woman.TOP_SLOT
import dev.chieppa.squash.screens.woman.WAIST_SLOT

val CREAM_TOP = GarmentId("cream-top")
val SKIRT = GarmentId("skirt")
val DRESS_BELT = GarmentId("dress-belt")

val CREAM = SlotName("cream")
val TEAL = SlotName("teal")

/** The cream top from the waist to the neckline, which the reference scoops low in front and keeps high behind, and short puffed sleeves. */
private const val TOP_FROM = 0.99f
private const val NECKLINE = 1.36f
private const val TOP_EASE = 0.006f
private const val PUFF_EASE = 0.01f
private const val PUFF_TO = 0.2f

/** The skirt runs from its hem, 0.305 below the torso's start, up to the waist; below the torso it hangs free. */
private const val HEM_DROP = 0.305f
private const val WAIST = 1.005f
private const val SKIRT_EASE = 0.013f

/**
 * The skirt in Side, along the torso from the hem below it to the waist, front up: from the waist out over the seat and on as a cone
 * flaring 0.2 a metre below the hips, deeper behind than in front.
 */
private val SKIRT_SIDE = loop(
    p(-0.305f, 0.193f), p(-0.200f, 0.172f), p(-0.089f, 0.150f), p(0.000f, 0.132f), p(0.126f, 0.107f), p(0.200f, 0.097f), p(0.270f, 0.096f),
    p(0.270f, -0.089f), p(0.200f, -0.120f), p(0.126f, -0.153f), p(0.000f, -0.170f), p(-0.089f, -0.196f), p(-0.200f, -0.218f), p(-0.305f, -0.239f),
)

/** The skirt from in front, its half width: the hips' footprint carried down and flaring to 0.29 at the hem. */
private val SKIRT_TOP = loop(
    p(-0.305f, 0.287f), p(-0.200f, 0.266f), p(-0.089f, 0.244f), p(0.000f, 0.226f), p(0.126f, 0.201f), p(0.200f, 0.172f), p(0.270f, 0.148f),
    p(0.270f, -0.148f), p(0.200f, -0.172f), p(0.126f, -0.201f), p(0.000f, -0.226f), p(-0.089f, -0.244f), p(-0.200f, -0.266f), p(-0.305f, -0.287f),
)

/** The tan belt at the waist over the skirt's top. */
private const val BELT_FROM = 1.004f
private const val BELT_TO = 1.020f
private const val BELT_EASE = 0.0155f

/** The dress outfit: a cream top with puffed sleeves, a teal skirt hanging free over her own legs, and a tan belt; the flats are [twinShoes]. */
fun twinDress(): List<DocumentCommand> = listOf(cloth(CREAM, 0xF5EEDD), cloth(TEAL, 0x2F8A86)) +
    twinGarment(
        CREAM_TOP, "Cream top", TOP_SLOT, CREAM,
        listOf(GarmentBranch(TORSO, torsoU(TOP_FROM), torsoU(NECKLINE), ease = TOP_EASE), GarmentBranch(ARM_L, 0f, PUFF_TO, ease = PUFF_EASE)),
    ) +
    twinGarment(
        SKIRT, "Skirt", BOTTOM_SLOT, TEAL,
        listOf(GarmentBranch(TORSO, -HEM_DROP / TORSO_LENGTH, torsoU(WAIST), ease = SKIRT_EASE, outlines = mapOf(View.SIDE to SKIRT_SIDE, View.TOP to SKIRT_TOP))),
        layer = WAIST_LAYER,
    ) +
    twinGarment(DRESS_BELT, "Belt", WAIST_SLOT, TAN, listOf(GarmentBranch(TORSO, torsoU(BELT_FROM), torsoU(BELT_TO), ease = BELT_EASE)), layer = BELT_LAYER)
