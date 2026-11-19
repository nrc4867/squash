package dev.chieppa.squash.screens.look.measure

import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch

/**
 * [character]'s wardrobe as lines: its slots; each garment with its slot, layer, fit, the parts it covers (a cover's u and, when it does not
 * go all round, v span; a part painted by areas alone as `painted`), what it changes, its holes and the parts it owns; each outfit with
 * its picks by slot, the shown one marked. Empty for a character with no garments or outfits.
 */
fun wardrobeLines(character: Character): List<String> {
    val wardrobe = character.wardrobe
    if (wardrobe.garments.isEmpty() && wardrobe.outfits.isEmpty()) return emptyList()
    val lines = mutableListOf<String>()
    lines += "slots ${wardrobe.slots.joinToString { it.id.value }.ifEmpty { "none" }}"
    for (garment in wardrobe.garments.sortedBy { it.layer }) lines += garmentLine(character, garment)
    for (outfit in wardrobe.outfits) {
        val picks = outfit.picks.entries.joinToString { (slot, garment) -> "${slot.value}=${garment.value}" }.ifEmpty { "nothing" }
        lines += "outfit ${outfit.id.value}${if (outfit.id == wardrobe.shown) " (shown)" else ""}: $picks"
    }
    return lines
}

private fun garmentLine(character: Character, garment: Garment): String {
    val covers = garment.branches.map { coverText(it) }
    val painted = garment.areas.flatMap { it.cells.keys }.distinct().filter { part -> garment.branches.none { it.part == part } }.map { "${it.value} painted" }
    val owned = character.parts.filter { it.garment == garment.id }.map { it.id.value }
    return buildString {
        append("garment ${garment.id.value} slot ${garment.slot?.value ?: "none"} layer ${garment.layer}")
        garment.fit?.let { append(" fit %.3f".format(it)) }
        append(" covers ${(covers + painted).joinToString().ifEmpty { "nothing" }}")
        if (garment.changes.isNotEmpty()) append("; changes ${garment.changes.entries.joinToString { (part, change) -> "${part.value} ${change::class.simpleName?.lowercase()}" }}")
        if (garment.holes.isNotEmpty()) append("; holes ${garment.holes.joinToString { it.part.value + if (it.cuff) " cuffed" else "" }}")
        if (owned.isNotEmpty()) append("; owns ${owned.joinToString()}")
    }
}

private fun coverText(branch: GarmentBranch): String {
    val v = if (branch.allRound) "" else " v %.2f-%.2f".format(branch.fromV, branch.toV)
    return "${branch.part.value} u %.2f-%.2f$v".format(branch.from, branch.to)
}
