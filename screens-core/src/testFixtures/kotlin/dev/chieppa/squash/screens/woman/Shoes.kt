package dev.chieppa.squash.screens.woman

import dev.chieppa.squash.character.build.GenericParams
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SurfaceWork
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.fennec.loop
import dev.chieppa.squash.screens.fennec.p
import dev.chieppa.squash.screens.twin.FOOTPRINT
import dev.chieppa.squash.screens.twin.PRINT_BACK
import dev.chieppa.squash.screens.twin.PRINT_FRONT
import dev.chieppa.squash.screens.twin.sole

val SANDALS = GarmentId("sandals")
val SNEAKERS = GarmentId("sneakers")
val BOOTS = GarmentId("boots")

val SANDAL_LEATHER = SlotName("sandal")
val CANVAS = SlotName("canvas")
val PATENT = SlotName("patent")
val BOOT_LEATHER = SlotName("boot")

/**
 * The sandal: a sole it owns under each foot, the blended foot's footprint at her foot's size, 0.012 m deep before that size, and a strap
 * over the forefoot; the bare toes rest on the sole.
 */
val SANDAL_SOLE_L = PartId("sandal-sole-l")
val SANDAL_SOLE_R = PartId("sandal-sole-r")
private const val SANDAL_SOLE_SOCKET = 2
private const val SOLE_DEPTH = 0.012f
private const val STRAP_FROM = 0.56f
private const val STRAP_TO = 0.61f
private const val STRAP_EASE = 0.005f

private const val SIZE = "size"
private const val SHOE_RINGS = 48

/**
 * A shoe drawn as the blob the foot is swapped for while it is worn, from the ankle forward: the shell over the foot's own build would
 * stay open at its flat toe end, which a garment cannot close, so a closed shoe takes a closed part under it. Front up in Side.
 */
private val SNEAKER_SIDE = loop(
    p(0.000f, 0.030f), p(0.020f, 0.060f), p(0.090f, 0.055f), p(0.200f, 0.035f), p(0.330f, 0.018f), p(0.420f, 0.000f), p(0.440f, -0.040f),
    p(0.400f, -0.075f), p(0.250f, -0.082f), p(0.080f, -0.082f), p(0.000f, -0.070f),
)
private val SNEAKER_TOP = loop(
    p(0.000f, 0.000f), p(0.010f, 0.036f), p(0.090f, 0.042f), p(0.210f, 0.050f), p(0.330f, 0.060f), p(0.410f, 0.052f), p(0.440f, 0.000f),
    p(0.410f, -0.052f), p(0.330f, -0.060f), p(0.210f, -0.050f), p(0.090f, -0.042f), p(0.010f, -0.036f),
)

/** The boots' shafts run up the legs from above the ankle to under the knee, over the jeans. */
private const val SHAFT_FROM = 0.66f
private const val BOOT_EASE = 0.022f
private const val SHOE_EASE = 0.01f

/** The foot swapped for a closed blob drawn as the shoe, at the foot's own size, for as long as the shoe is worn. */
internal fun shod(side: OutlineCurve, top: OutlineCurve, color: SlotName) = BodyPartChange.Swap(
    TemplateRef(TemplateId("blob"), 1),
    PartData(outlines = mapOf(View.SIDE to side, View.TOP to top), params = mapOf(SIZE to FOOT_SIZE, GenericParams.RINGS to SHOE_RINGS.toFloat()), surface = SurfaceWork(baseSlot = color)),
)

/** The shoes: sandals under the bare foot, sneakers that swap the foot for a closed shoe, and boots up to under the knee; the pumps are [pumps]. */
fun shoes(): List<DocumentCommand> = listOf(
    cloth(SANDAL_LEATHER, 0.55f, 0.36f, 0.22f),
    cloth(CANVAS, 0.95f, 0.95f, 0.93f),
    cloth(PATENT, 0.10f, 0.09f, 0.11f),
    cloth(BOOT_LEATHER, 0.16f, 0.13f, 0.13f),
) + garment(SANDALS, "Sandals", FEET_SLOT, SANDAL_LEATHER, listOf(GarmentBranch(FOOT_L, STRAP_FROM, STRAP_TO, ease = STRAP_EASE))) +
    sole(SANDALS, SANDAL_SOLE_SOCKET, SANDAL_SOLE_L, SANDAL_SOLE_R, FOOTPRINT, PRINT_BACK, PRINT_FRONT, SOLE_DEPTH, SANDAL_LEATHER, BARE_FOOT_SIZE) +
    garment(SNEAKERS, "Sneakers", FEET_SLOT, CANVAS, listOf(GarmentBranch(FOOT_L, ease = SHOE_EASE)), changes = mapOf(FOOT_L to shod(SNEAKER_SIDE, SNEAKER_TOP, CANVAS))) +
    garment(
        BOOTS, "Boots", FEET_SLOT, BOOT_LEATHER, listOf(GarmentBranch(FOOT_L, ease = BOOT_EASE), GarmentBranch(LEG_L, SHAFT_FROM, 1f, ease = BOOT_EASE)),
        layer = OVER_LAYER, changes = mapOf(FOOT_L to shod(SNEAKER_SIDE, SNEAKER_TOP, BOOT_LEATHER)),
    )
