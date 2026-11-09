package dev.chieppa.squash.document

import kotlinx.serialization.Serializable

/** A named place on the body, such as head, neck or feet, that an outfit fills with at most one garment. */
@Serializable
data class GarmentSlot(val id: GarmentSlotId, val name: String)

/** A named set of garments, at most one per slot, by [picks]. */
@Serializable
data class Outfit(val id: OutfitId, val name: String, val picks: Map<GarmentSlotId, GarmentId> = emptyMap())

/**
 * A character's clothes: its [garments], its [slots] in order, its [outfits] and the outfit Build [shown], or null for the bare
 * body. An outfit's pick for a slot names a garment in that slot. See HLD: data-model, Garments.
 */
@Serializable
data class Wardrobe(
    val garments: List<Garment> = emptyList(),
    val slots: List<GarmentSlot> = emptyList(),
    val outfits: List<Outfit> = emptyList(),
    val shown: OutfitId? = null,
) {
    init {
        require(garments.distinctBy { it.id }.size == garments.size) { "A garment id repeats" }
        require(slots.distinctBy { it.id }.size == slots.size) { "A slot id repeats" }
        require(outfits.distinctBy { it.id }.size == outfits.size) { "An outfit id repeats" }
        val slotIds = slots.map { it.id }.toSet()
        garments.forEach { garment -> require(garment.slot == null || garment.slot in slotIds) { "Garment ${garment.id.value} sits in no slot of the wardrobe" } }
        outfits.forEach { outfit ->
            outfit.picks.forEach { (slot, id) ->
                require(slot in slotIds) { "Outfit ${outfit.id.value} picks for a slot the wardrobe lacks" }
                require(garment(id)?.slot == slot) { "Outfit ${outfit.id.value} picks ${id.value}, which is not in slot ${slot.value}" }
            }
        }
        require(shown == null || outfit(shown) != null) { "The shown outfit is not in the wardrobe" }
    }

    fun garment(id: GarmentId): Garment? = garments.firstOrNull { it.id == id }

    fun outfit(id: OutfitId): Outfit? = outfits.firstOrNull { it.id == id }

    fun slot(id: GarmentSlotId): GarmentSlot? = slots.firstOrNull { it.id == id }

    /** The garments [outfit] wears, innermost layer first, ties in wardrobe order. */
    fun wornIn(outfit: OutfitId?): List<Garment> {
        val picks = outfit?.let(::outfit)?.picks ?: return emptyList()
        val worn = picks.values.toSet()
        return garments.filter { it.id in worn }.sortedBy { it.layer }
    }

    /** The garments [shown] wears, innermost first. */
    val worn: List<Garment> get() = wornIn(shown)

    /** This wardrobe with [id] changed by [change]. */
    internal fun withGarment(id: GarmentId, change: (Garment) -> Garment): Wardrobe {
        require(garment(id) != null) { "No garment ${id.value}" }
        return copy(garments = garments.map { if (it.id == id) change(it) else it })
    }

    /** This wardrobe with every outfit's pick that [keep] refuses dropped. */
    internal fun picksKept(keep: (GarmentSlotId, GarmentId) -> Boolean): Wardrobe =
        copy(outfits = outfits.map { outfit -> outfit.copy(picks = outfit.picks.filter { (slot, garment) -> keep(slot, garment) }) })
}
