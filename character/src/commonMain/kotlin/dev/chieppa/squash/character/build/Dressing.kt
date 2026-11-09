package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.branchPartId
import dev.chieppa.squash.document.cuffPartId
import dev.chieppa.squash.document.isCuffPart
import dev.chieppa.squash.document.worn
import dev.chieppa.squash.graph.Graph
import dev.chieppa.squash.graph.Node
import dev.chieppa.squash.graph.cancellationCheck
import kotlinx.coroutines.flow.Flow

/** What dressing the character reads from the document: the garments worn, innermost first, and each paired part's twin. */
data class Dressing(val worn: List<Garment> = emptyList(), val twins: Map<PartId, PartId> = emptyMap())

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
 * placed where that part is; each branch joined by fillet to the branch over the part its own part sits on; a hole and a cuff where
 * a body part comes out through a branch; and the skin the branches cover hidden. See HLD: data-model, Garments.
 */
fun AssembledCharacter.dressedIn(dressing: Dressing, checkCancelled: () -> Unit = {}): AssembledCharacter {
    if (dressing.worn.isEmpty()) return this
    val dressed = dressing.worn.flatMap { garment ->
        val branches = garment.branches.flatMap { branch ->
            listOfNotNull(branch.part, dressing.twins[branch.part]).mapNotNull { id ->
                checkCancelled()
                part(id)?.let { branchOver(garment, branch, it) }
            }
        }
        joined(garment, branches, this, checkCancelled).flatMap { branch ->
            val (holed, cuffs) = throughHoles(garment, branch, this, dressing.twins, checkCancelled)
            listOf(holed) + cuffs
        }
    }
    if (dressed.isEmpty()) return this
    val covers = dressed.filter { !isCuffPart(requireNotNull(it.id)) }.map(::coverOf)
    val body = parts.map { part ->
        checkCancelled()
        part.coveredBy(covers)
    }
    return AssembledCharacter(body + dressed, sockets)
}

/** The base color of every branch and cuff [character] may wear: its garment's slot, else its branch's own surface work's. */
fun garmentColors(character: Character): Map<PartId, PartColor> {
    val dressing = dressingOf(character)
    return dressing.worn.flatMap { garment ->
        garment.branches.flatMap { branch ->
            val color = PartColor(garment.color ?: branch.surface.baseSlot, emptyList())
            val covered = listOfNotNull(branch.part, dressing.twins[branch.part])
            val through = character.parts.filter { it.socket?.parent in covered }.map { cuffPartId(garment.id, it.id) }
            covered.map { branchPartId(garment.id, it) to color } + through.map { it to color }
        }
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
