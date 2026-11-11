package dev.chieppa.squash.screens.woman

import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.SlotName

val SHORTS = GarmentId("shorts")
val TROUSERS = GarmentId("trousers")
val JEANS = GarmentId("jeans")
val BLACK_JEANS = GarmentId("black-jeans")

val DENIM = SlotName("denim")
val BLACK_DENIM = SlotName("black-denim")
val KHAKI = SlotName("khaki")

/** Where a waistband sits on the torso: up from the seat to just under the navel. */
private const val WAIST = 0.3f
private const val SHORTS_LEG = 0.26f
private const val FULL_LEG = 0.97f
private const val TROUSER_EASE = 0.02f
private const val JEANS_EASE = 0.012f

private fun bottom(id: GarmentId, name: String, color: SlotName, leg: Float, ease: Float) = garment(
    id, name, BOTTOM_SLOT, color,
    listOf(GarmentBranch(TORSO, 0f, WAIST, ease = ease), GarmentBranch(LEG_L, 0f, leg, ease = ease)),
    layer = WAIST_LAYER,
)

/** The bottoms: shorts, suit trousers, blue jeans and black jeans, each a waistband over the hips and a leg down each leg. */
fun bottoms(): List<DocumentCommand> = listOf(
    cloth(DENIM, 0.30f, 0.42f, 0.62f),
    cloth(BLACK_DENIM, 0.12f, 0.12f, 0.14f),
    cloth(KHAKI, 0.80f, 0.70f, 0.50f),
) + bottom(SHORTS, "Shorts", KHAKI, SHORTS_LEG, TROUSER_EASE) +
    bottom(TROUSERS, "Trousers", SUIT, FULL_LEG, TROUSER_EASE) +
    bottom(JEANS, "Jeans", DENIM, FULL_LEG, JEANS_EASE) +
    bottom(BLACK_JEANS, "Black jeans", BLACK_DENIM, FULL_LEG, JEANS_EASE)
