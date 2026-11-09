package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.branchPartId
import dev.chieppa.squash.document.cuffPartId
import dev.chieppa.squash.document.looseEndPartId
import dev.chieppa.squash.document.isCuffPart
import dev.chieppa.squash.document.worn
import dev.chieppa.squash.graph.Graph
import dev.chieppa.squash.graph.Node
import dev.chieppa.squash.graph.cancellationCheck
import kotlinx.coroutines.flow.Flow

/**
 * What dressing the character reads from the document: the garments worn, innermost first, and each paired part's twin; [covers] is
 * whether the skin and the inner layers the garments cover are hidden.
 */
data class Dressing(val worn: List<Garment> = emptyList(), val twins: Map<PartId, PartId> = emptyMap(), val covers: Boolean = true)

/** How the garments show: drawn whole over what they cover, see-through with the body whole under them, or not at all. View state, never saved. */
enum class GarmentLook { SHOWN, GHOSTED, HIDDEN }

/** What the views ask of the garments: how they [look], and the garments left out of view. Never saved. */
data class GarmentView(val look: GarmentLook = GarmentLook.SHOWN, val hidden: Set<GarmentId> = emptySet())

/**
 * This dressing as [view] shows it: without the garments it hides, with nothing hidden under them when ghosted, and with none worn when
 * hidden. The garments' body-part changes are the document's and hold whatever the view.
 */
fun Dressing.seenAs(view: GarmentView): Dressing = when (view.look) {
    GarmentLook.HIDDEN -> Dressing()
    GarmentLook.GHOSTED -> copy(worn = worn.filter { it.id !in view.hidden }, covers = false)
    GarmentLook.SHOWN -> if (view.hidden.isEmpty()) this else copy(worn = worn.filter { it.id !in view.hidden })
}

/** The dressing [character] wears. */
fun dressingOf(character: Character): Dressing {
    val worn = character.worn
    if (worn.isEmpty()) return Dressing()
    val twins = character.parts.mapNotNull { part -> character.twinOf(part)?.let { part.id to it.id } }.toMap()
    return Dressing(worn, twins)
}

/** [assembled] with the garments [dressing] holds, following both. See HLD: data-model, Garments. */
fun Graph.dressedNode(assembled: Node<*, AssembledCharacter>, dressing: Flow<Dressing>): Node<*, AssembledCharacter> = gather(
    dressing,
    sourcesOf = { mapOf(Unit to assembled) },
    stage = BuildStages.ASSEMBLY,
) { (worn, built) ->
    val checkCancelled = cancellationCheck()
    built.getValue(Unit).dressedIn(worn, checkCancelled)
}

/**
 * This character wearing what [dressing] holds: a part for every branch of every worn garment, on each covered part and its twin,
 * placed where that part is and fitted over the layers under it; each branch joined by fillet to the branch over the part its own part
 * sits on; a hole and a cuff where a body part comes out through a branch; the skin the branches and cuffs cover hidden, but for a part
 * that comes out through a branch, which that branch leaves whole, as the hole leaves the branch open where the part fills it; each layer's faces
 * an outer layer covers hidden; each branch and cuff inking its hem, cuffs and joins as the garment's switches say; and each garment's
 * loose ends hung from their branches; with [Dressing.covers] off nothing under a garment is hidden. See HLD: data-model, Garments.
 */
fun AssembledCharacter.dressedIn(dressing: Dressing, checkCancelled: () -> Unit = {}): AssembledCharacter {
    if (dressing.worn.isEmpty()) return this
    val through = HashMap<PartId, MutableSet<PartId>>()
    val layers = dressing.worn.mapIndexed { layer, garment ->
        val under = dressing.worn.take(layer)
        val branches = garment.branches.flatMap { branch ->
            listOfNotNull(branch.part, dressing.twins[branch.part]).mapNotNull { id ->
                checkCancelled()
                part(id)?.let { branchOver(garment, branch.fittedOver(under, dressing.twins), it) }
            }
        }
        joined(garment, branches, this, checkCancelled).map { edgeInked(garment, it, this, dressing.twins) }.flatMap { branch ->
            val holed = throughHoles(garment, branch, this, dressing.twins, checkCancelled)
            holed.through.forEach { part -> through.getOrPut(part, ::HashSet) += requireNotNull(holed.branch.id) }
            listOf(holed.branch) + holed.cuffs
        }
    }
    if (!dressing.covers) {
        val shells = layers.flatten()
        return AssembledCharacter(parts + shells + dressing.worn.flatMap { garment -> looseEnds(garment, shells, this) }, sockets)
    }
    val covers = layers.map { parts -> parts.filter { !isCuffPart(requireNotNull(it.id)) }.mapNotNull(::hiderOf) }
    val dressed = layers.flatMapIndexed { layer, parts ->
        val outer = covers.drop(layer + 1).flatten()
        parts.map { it.hiddenBy(outer) }
    }
    if (dressed.isEmpty()) return this
    val ends = dressing.worn.flatMap { garment -> looseEnds(garment, dressed, this) }
    val all = layers.flatten().mapNotNull(::hiderOf)
    val body = parts.map { part ->
        checkCancelled()
        val passes = through[part.id].orEmpty()
        part.hiddenBy(if (passes.isEmpty()) all else all.filter { it !is Hider.Inside || it.part !in passes })
    }
    return AssembledCharacter(body + dressed + ends, sockets)
}

/**
 * This branch fitted over the garments [under] it: its ease stands off the layer below rather than the skin, so each inner garment's
 * branch the whole way round its part, over any of the same span, adds its ease to this one's.
 */
private fun GarmentBranch.fittedOver(under: List<Garment>, twins: Map<PartId, PartId>): GarmentBranch {
    val below = under.sumOf { garment ->
        garment.branches.filter { (it.part == part || twins[it.part] == part) && it.allRound && it.from < to && it.to > from }
            .sumOf { it.ease.toDouble() }
    }.toFloat()
    return if (below == 0f) this else copy(ease = ease + below)
}

/** The base color of every branch, cuff and loose end [character] may wear: its garment's slot, else its branch's own surface work's. */
fun garmentColors(character: Character): Map<PartId, PartColor> {
    val dressing = dressingOf(character)
    return dressing.worn.flatMap { garment ->
        garment.branches.flatMap { branch ->
            val color = PartColor(garment.color ?: branch.surface.baseSlot, emptyList())
            val covered = listOfNotNull(branch.part, dressing.twins[branch.part])
            val through = character.parts.filter { it.socket?.parent in covered }.map { cuffPartId(garment.id, it.id) }
            covered.map { branchPartId(garment.id, it) to color } + through.map { it to color }
        } + garment.looseEnds.map { looseEndPartId(garment.id, it.id) to PartColor(garment.color ?: garment.branch(it.branch)?.surface?.baseSlot, emptyList()) }
    }.toMap()
}

/** [colors] with the garments' colors [garments] holds, following both. */
fun Graph.dressedColorNode(colors: Node<*, BodyColor>, garments: Flow<Map<PartId, PartColor>>): Node<*, BodyColor> = gather(
    garments,
    sourcesOf = { mapOf(Unit to colors) },
    stage = BuildStages.SURFACE_WORK,
) { (worn, built) ->
    val body = built.getValue(Unit)
    if (worn.isEmpty()) body else BodyColor(body.parts + worn)
}
