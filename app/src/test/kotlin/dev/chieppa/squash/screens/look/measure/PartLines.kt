package dev.chieppa.squash.screens.look.measure

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.document.Character

/** The part ids a character has, each with its role and template, for the options that take part ids (`close=`, `only=`, a garment's `covers`). */
fun partLines(character: Character): List<String> =
    listOf("part ids " + character.parts.joinToString { "${it.id.value} (${it.role?.name?.lowercase() ?: "no role"}${if (it.garment != null) ", garment ${it.garment?.value}" else ""})" })

/**
 * Each garment's hems as heights above the ground in body units, read on the character [body] built at rest: for each branch, the part and
 * the height of the middle of the part at the branch's start and end, so a hem placed by u can be checked against the height it was meant
 * for.
 */
fun hemLines(body: AssembledCharacter, character: Character): List<String> {
    val ground = body.parts.filter { it.id != null }.minOfOrNull { part -> List(part.surface.mesh.vertexCount) { part.origin.y + part.surface.mesh.position(it).y }.min() } ?: return emptyList()
    return character.wardrobe.garments.sortedBy { it.layer }.mapNotNull { garment ->
        val hems = garment.branches.mapNotNull { branch ->
            val part = branch.part.let(body::part) ?: return@mapNotNull null
            val from = part.middleAt(branch.from)?.y?.minus(ground)
            val to = part.middleAt(branch.to)?.y?.minus(ground)
            if (from == null || to == null) null else "${branch.part.value} u %.2f at %.3f, u %.2f at %.3f".format(branch.from, from, branch.to, to)
        }
        if (hems.isEmpty()) null else "garment ${garment.id.value} hem heights above the ground: ${hems.joinToString("; ")}"
    }
}
