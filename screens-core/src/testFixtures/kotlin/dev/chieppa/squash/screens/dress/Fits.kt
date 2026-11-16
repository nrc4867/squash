package dev.chieppa.squash.screens.dress

import dev.chieppa.squash.document.DEFAULT_EASE
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch

/**
 * [garment] fitted as [branches] stand off: its fit what most of them stand off by, the first's on a tie, and each other branch's part
 * fitted apart; none with no branches. The branches to cover it with follow, their own ease left at its default.
 */
fun fittedTo(garment: Garment, branches: List<GarmentBranch>): Pair<Garment, List<GarmentBranch>> {
    if (branches.isEmpty()) return garment to branches
    val fit = branches.groupBy { it.ease }.maxBy { (_, held) -> held.size * branches.size - branches.indexOf(held.first()) }.key
    val apart = branches.filter { it.ease != fit }.associate { it.part to it.ease }
    return garment.copy(fit = fit, partFits = apart) to branches.map { it.copy(ease = DEFAULT_EASE) }
}
