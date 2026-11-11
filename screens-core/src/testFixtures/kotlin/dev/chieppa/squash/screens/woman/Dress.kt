package dev.chieppa.squash.screens.woman

import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.fennec.loop
import dev.chieppa.squash.screens.fennec.p

val SKIRT = GarmentId("skirt")
val TANK_TOP = GarmentId("tank-top")
val SKIRT_CLOTH = SlotName("skirt")
val TANK = SlotName("tank")

/** How far below the torso's lowest point the skirt's hem hangs, in body units: above the knee. */
private const val HEM_DROP = 0.40f

/** The skirt runs from its hem, past the torso's start, up to the waist; it hangs free below the torso. */
private const val SKIRT_FROM = -HEM_DROP / TORSO_LENGTH
private const val SKIRT_TO = 0.32f
private const val SKIRT_EASE = 0.02f

/**
 * The skirt in Side, along the torso from the hem below it to the waist, front up: flaring from the waist over the seat to a full hem.
 * Where it lies inside the torso and its ease, the ease holds.
 */
private val SKIRT_SIDE = loop(
    p(-0.40f, 0.195f), p(-0.26f, 0.18f), p(-0.12f, 0.165f), p(0f, 0.15f), p(0.12f, 0.125f), p(0.24f, 0.1f),
    p(0.24f, -0.1f), p(0.12f, -0.16f), p(0f, -0.18f), p(-0.12f, -0.19f), p(-0.26f, -0.2f), p(-0.40f, -0.21f),
)

/** The skirt from in front: out from the waist over the hips and on to the hem, clear of both legs. */
private val SKIRT_TOP = loop(
    p(-0.40f, 0.32f), p(-0.26f, 0.295f), p(-0.12f, 0.27f), p(0f, 0.245f), p(0.12f, 0.215f), p(0.24f, 0.15f),
    p(0.24f, -0.15f), p(0.12f, -0.215f), p(0f, -0.245f), p(-0.12f, -0.27f), p(-0.26f, -0.295f), p(-0.40f, -0.32f),
)

/** The tank top runs from under the bust to the base of the neck, the arms out of it bare. */
private const val TANK_FROM = 0.42f
private const val TANK_TO = 0.95f
private const val TANK_EASE = 0.01f

/**
 * The dress: a skirt flaring from the waist to above the knee, hanging free below her own torso with her legs inside it, and a tank top
 * made for it.
 */
fun dress(): List<DocumentCommand> = listOf(
    cloth(SKIRT_CLOTH, 0.18f, 0.36f, 0.30f),
    cloth(TANK, 0.95f, 0.88f, 0.70f),
) + garment(
    SKIRT, "Skirt", BOTTOM_SLOT, SKIRT_CLOTH,
    listOf(GarmentBranch(TORSO, SKIRT_FROM, SKIRT_TO, ease = SKIRT_EASE, outlines = mapOf(View.SIDE to SKIRT_SIDE, View.TOP to SKIRT_TOP))),
    layer = WAIST_LAYER,
) + garment(TANK_TOP, "Tank top", TOP_SLOT, TANK, listOf(GarmentBranch(TORSO, TANK_FROM, TANK_TO, ease = TANK_EASE)), changes = mapOf(ARM_L to OPEN))
