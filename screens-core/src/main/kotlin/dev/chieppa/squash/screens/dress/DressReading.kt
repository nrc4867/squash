package dev.chieppa.squash.screens.dress

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.wornPart
import dev.chieppa.squash.character.catalog.isLeader
import kotlin.math.floor

/**
 * One body part under a garment, read for its pair as one: [covered] when a branch of the garment runs over it, [through] when it sits
 * on a socket inside the region a branch covers and so comes out through a hole, and the garment's own [change] to it, if any.
 */
data class PartUnder(val part: PartInstance, val covered: Boolean, val through: Boolean, val change: BodyPartChange?)

/** Where two or more worn garments cover or change one [part]: the [outer] one wins over the [under] ones. */
data class LayerClash(val part: PartInstance, val outer: GarmentId, val under: List<GarmentId>)

/** [part], or the leader of its pair. */
fun Character.leaderOf(part: PartInstance): PartInstance = if (part.isLeader) part else twinOf(part) ?: part

/** Whether [garment] covers [part] or the other side of its pair with a branch. */
fun Character.covers(garment: Garment, part: PartInstance): GarmentBranch? =
    garment.branch(part.id) ?: twinOf(part)?.let { garment.branch(it.id) }

/** The change [garment] holds for [part], on either side of its pair. */
fun Character.changeOf(garment: Garment, part: PartInstance): BodyPartChange? =
    garment.changes[part.id] ?: twinOf(part)?.let { garment.changes[it.id] }

/**
 * Whether [part] comes out through [garment]: it sits on a socket of a part the garment covers, inside that branch's u and v range as
 * [assembled] places the socket, or anywhere on it when nothing is assembled yet, and the garment does not hide it.
 */
fun Character.comesThrough(garment: Garment, part: PartInstance, assembled: AssembledCharacter?): Boolean {
    val parent = part.socket?.parent?.let(::part) ?: return false
    val branch = covers(garment, parent) ?: return false
    if (changeOf(garment, part) == BodyPartChange.Hide) return false
    val spec = assembled?.part(part.id)?.socketSpec ?: return true
    return spec.u in branch.from..branch.to && (branch.allRound || around(spec.v, branch.fromV, branch.toV))
}

/** Whether [v] lies from [from] to [to] round a part, reading v round. */
private fun around(v: Float, from: Float, to: Float): Boolean {
    val ahead = v - from - floor(v - from)
    return ahead <= to - from
}

/**
 * The body parts under [garment], one row per pair in the character's order: those a branch covers, those that come out through it,
 * and those it changes.
 */
fun Character.partsUnder(garment: Garment, assembled: AssembledCharacter?): List<PartUnder> =
    parts.filter { it.isLeader }.mapNotNull { part ->
        val covered = covers(garment, part) != null
        val through = !covered && (comesThrough(garment, part, assembled) || twinOf(part)?.let { comesThrough(garment, it, assembled) } == true)
        val change = changeOf(garment, part)
        if (covered || through || change != null) PartUnder(part, covered, through, change) else null
    }

/** The parts that come out through [garment], each pair once. */
fun Character.holesIn(garment: Garment, assembled: AssembledCharacter?): List<PartUnder> = partsUnder(garment, assembled).filter { it.through }

/** How many holes [garment] has, each side of a pair counted. */
fun Character.holeCount(garment: Garment, assembled: AssembledCharacter?): Int =
    holesIn(garment, assembled).sumOf { if (it.part.pair != null) PAIR_SIDES else 1 }

/** How many of [garment]'s branches join the branch over the part their own part sits on. */
fun Character.joinCount(garment: Garment): Int = garment.branches.count { branch ->
    val parent = part(branch.part)?.socket?.parent?.let(::part) ?: return@count false
    covers(garment, parent) != null
}

/** The branches of [garment] that join another, each with the part of the branch it comes out of. */
fun Character.joinsOf(garment: Garment): List<Pair<GarmentBranch, PartId>> = garment.branches.mapNotNull { branch ->
    val parent = part(branch.part)?.socket?.parent?.let(::part) ?: return@mapNotNull null
    covers(garment, parent)?.let { branch to it.part }
}

/** Every part two or more worn garments cover or change, with the outermost, which wins. */
fun Character.layerClashes(): List<LayerClash> {
    val worn = wardrobe.worn
    if (worn.size < 2) return emptyList()
    return parts.filter { it.isLeader }.mapNotNull { part ->
        val touching = worn.filter { covers(it, part) != null || changeOf(it, part) != null }
        if (touching.size < 2) null else LayerClash(part, touching.last().id, touching.dropLast(1).map { it.id }.reversed())
    }
}

/** What [part] is under the worn garments, as the Parts rows note it in Dress. */
sealed interface DressNote {
    data object Hidden : DressNote
    data object Through : DressNote
    data object Bare : DressNote
    data class Under(val garments: List<String>) : DressNote
}

/** [part]'s note in the Parts rows: hidden by a worn garment, under the worn garments that cover it, outermost first, through one, or bare. */
fun Character.dressNote(part: PartInstance, assembled: AssembledCharacter?): DressNote {
    if (wornPart(part).hidden) return DressNote.Hidden
    val worn = wardrobe.worn
    val over = worn.filter { covers(it, part) != null }.reversed()
    if (over.isNotEmpty()) return DressNote.Under(over.map { it.name })
    val twin = twinOf(part)
    if (worn.any { comesThrough(it, part, assembled) || (twin != null && comesThrough(it, twin, assembled)) }) return DressNote.Through
    return DressNote.Bare
}

private const val PAIR_SIDES = 2
