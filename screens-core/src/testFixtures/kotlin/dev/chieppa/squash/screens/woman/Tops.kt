package dev.chieppa.squash.screens.woman

import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.SlotName

val BIKINI_TOP = GarmentId("bikini-top")
val TEE = GarmentId("tee")
val BLOUSE = GarmentId("blouse")
val BLAZER = GarmentId("blazer")
val DARK_TOP = GarmentId("dark-top")
val COAT = GarmentId("coat")

val SWIM = SlotName("swim")
val TEE_COTTON = SlotName("tee")
val BLOUSE_SILK = SlotName("blouse")
val SUIT = SlotName("suit")
val PLUM = SlotName("plum")
val COAT_WOOL = SlotName("coat")

/** A band round the bust, from under it to over it, close to the skin. */
private const val BIKINI_FROM = 0.68f
private const val BIKINI_TO = 0.85f
private const val SNUG = 0.008f

/** A tee from the hips to the base of the neck, short sleeves. */
private const val TEE_FROM = 0.1f
private const val TEE_TO = 0.96f
private const val SHORT_SLEEVE = 0.3f
private const val FITTED = 0.015f

/** The blouse from the hips to the collar, long sleeves short of the wrist, the neck through it in a collar. */
private const val BLOUSE_FROM = 0.08f
private const val LONG_SLEEVE = 0.93f
private const val COLLAR = 0.35f

/** The blazer over the blouse, open down the front, a little shorter in body and sleeve. */
private const val BLAZER_FROM = 0.16f
private const val BLAZER_SLEEVE = 0.88f
private const val OPEN_FRONT = 0.075f
private const val TAILORED = 0.03f

/** The fitted long-sleeved dark top. */
private const val DARK_FROM = 0.1f
private const val DARK_SLEEVE = 0.97f

/** The winter coat, full and padded, from the hips to the base of the neck, which the scarf covers, the sleeves to the wrist. */
private const val COAT_FROM = 0.0f
private const val COAT_SLEEVE = 0.97f
private const val PADDED = 0.032f
/** How far the coat holds the arms out from the body, in degrees, so its padded sleeves clear its padded body. */
private const val COAT_ARMS = 17f

/** Layers counted out from the skin: what goes under the waistband, the waistband, and what goes over both. */
const val UNDER_LAYER = 1
const val WAIST_LAYER = 2
const val OVER_LAYER = 3

private val ARMS_OUT = mapOf(ARM_L to OPEN)

/** The tops: a bikini top, a tee, a blouse and a blazer to go over it, a dark long-sleeved top and a padded coat. */
fun tops(): List<DocumentCommand> = listOf(
    cloth(SWIM, 0.10f, 0.62f, 0.66f),
    cloth(TEE_COTTON, 0.96f, 0.62f, 0.32f),
    cloth(BLOUSE_SILK, 0.96f, 0.95f, 0.92f),
    cloth(SUIT, 0.22f, 0.23f, 0.29f),
    cloth(PLUM, 0.24f, 0.12f, 0.22f),
    cloth(COAT_WOOL, 0.62f, 0.20f, 0.18f),
) + garment(BIKINI_TOP, "Bikini top", TOP_SLOT, SWIM, listOf(GarmentBranch(TORSO, BIKINI_FROM, BIKINI_TO, ease = SNUG)), changes = ARMS_OUT) +
    garment(TEE, "Tee", TOP_SLOT, TEE_COTTON, listOf(GarmentBranch(TORSO, TEE_FROM, TEE_TO, ease = FITTED), GarmentBranch(ARM_L, 0f, SHORT_SLEEVE, ease = FITTED))) +
    garment(
        BLOUSE, "Blouse", TOP_SLOT, BLOUSE_SILK,
        listOf(GarmentBranch(TORSO, BLOUSE_FROM, 1f, ease = FITTED), GarmentBranch(ARM_L, 0f, LONG_SLEEVE, ease = FITTED)),
        changes = mapOf(NECK to BodyPartChange.Through(cuffSize = COLLAR)),
    ) +
    garment(
        BLAZER, "Blazer", OUTER_SLOT, SUIT,
        listOf(GarmentBranch(TORSO, BLAZER_FROM, 1f, ease = TAILORED, fromV = OPEN_FRONT, toV = 1f - OPEN_FRONT), GarmentBranch(ARM_L, 0f, BLAZER_SLEEVE, ease = TAILORED)),
        layer = OVER_LAYER,
    ) +
    garment(DARK_TOP, "Dark top", TOP_SLOT, PLUM, listOf(GarmentBranch(TORSO, DARK_FROM, TEE_TO, ease = FITTED), GarmentBranch(ARM_L, 0f, DARK_SLEEVE, ease = FITTED))) +
    garment(
        COAT, "Coat", OUTER_SLOT, COAT_WOOL,
        listOf(GarmentBranch(TORSO, COAT_FROM, 1f, ease = PADDED), GarmentBranch(ARM_L, 0f, COAT_SLEEVE, ease = PADDED)),
        layer = OVER_LAYER,
        changes = mapOf(NECK to OPEN, ARM_L to BodyPartChange.Redraw(PartData(params = mapOf(PlacementParams.OUTWARD to COAT_ARMS)))),
    )
