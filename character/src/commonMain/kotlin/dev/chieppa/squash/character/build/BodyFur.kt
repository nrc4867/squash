package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.AreaSpec
import dev.chieppa.squash.character.model.GrainSpec
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.FurRule
import dev.chieppa.squash.document.Grain
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkKind
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.positionAt

private const val HALF_WAY_ROUND = 0.5f

private const val ORDER_MIX = -1640531535
private const val ORDER_SHIFT = 15

/** A stroke's place in the order, spread from its id's hash, so strokes with ids in a row do not fall in a row. */
private fun orderOf(id: MarkId): Int {
    val mixed = id.value.hashCode() * ORDER_MIX
    return mixed xor (mixed ushr ORDER_SHIFT)
}

/**
 * [roots] each with its room: the first keeps all of it, and each next is the one furthest from those already taken, by that distance. So the
 * strokes with room of at least a given distance lie that far apart and leave no gap wider than it, at every distance, wherever the camera is.
 */
private fun withRoom(roots: List<FurRoot>): List<FurRoot> {
    val nearest = FloatArray(roots.size) { Float.POSITIVE_INFINITY }
    val taken = BooleanArray(roots.size)
    return List(roots.size) {
        val next = nearest.indices.filter { !taken[it] }.maxBy { nearest[it] }
        taken[next] = true
        val root = roots[next]
        for (i in roots.indices) if (!taken[i]) nearest[i] = minOf(nearest[i], (roots[i].position - root.position).length)
        root.copy(room = nearest[next])
    }
}

/** What a part's fur reads from its document and template. */
data class PartFurInput(val spec: GrainSpec, val grain: Grain?, val rule: FurRule, val areas: List<AreaSpec>)

/** What every part's fur reads, by part. */
fun furInputs(character: Character, templates: TemplateLibrary): Map<PartId, PartFurInput> = character.parts.mapNotNull { instance ->
    val template = templates.find(instance.template) ?: return@mapNotNull null
    val data = character.resolved(instance)
    instance.id to PartFurInput(template.grain, data.surface.grain, data.surface.fur ?: FurRule(), PartBuilder.areas(template, data.params))
}.toMap()

/**
 * One fur stroke on the body, in body space: its root, the surface's [normal] there, the way the grain runs, the slot under it, the
 * part's [radius] at that place, and the [room] it has: how far it lies, on the body, from every stroke before it.
 */
data class FurRoot(val id: MarkId, val position: Vec3, val normal: Vec3, val direction: Vec3, val slot: SlotName?, val radius: Float, val room: Float = Float.POSITIVE_INFINITY)

/** A part's fur strokes and the rule they show by. */
data class PartFur(val rule: FurRule, val roots: List<FurRoot>)

/** Every part's fur, camera aside, on a figure of [figureRadius] around [figureCenter]. */
data class BodyFur(val parts: Map<PartId, PartFur>, val figureRadius: Float = 0f, val figureCenter: Vec3 = Vec3.ZERO)

/**
 * The fur strokes in [work], each following the grain of the part holding it, each with its [FurRoot.room], which [furView] shows them by. A root on a part or patch not there is dropped. [checkCancelled] runs before each part.
 */
fun bodyFur(inputs: Map<PartId, PartFurInput>, work: BodySurfaceWork, character: AssembledCharacter, checkCancelled: () -> Unit = {}): BodyFur {
    val placed = character.parts.mapNotNull { part -> part.id?.let { it to part } }.toMap()
    return BodyFur(
        inputs.mapNotNull { (id, input) ->
            checkCancelled()
            val held = work.parts[id] ?: return@mapNotNull null
            val field = GrainField(input.spec, input.grain)
            val roots = held.marks.filter { it.kind == MarkKind.FUR }.mapNotNull { mark ->
                val root = mark.points.first()
                val surface = placed[root.anchor.part]?.surface ?: return@mapNotNull null
                val patch = surface.patches.firstOrNull { it.name == root.anchor.patch } ?: return@mapNotNull null
                val direction = surface.shown(field.directionAt(patch, root.anchor.u, root.anchor.v))
                val radius = (patch.positionAt(root.anchor.u, root.anchor.v) - patch.positionAt(root.anchor.u, root.anchor.v + HALF_WAY_ROUND)).length / 2f
                FurRoot(mark.id, root.position, root.normal, direction, slotUnder(root.anchor, root.position, held, input.areas, patch.closedV), radius)
            }
            if (roots.isEmpty()) null else id to PartFur(input.rule, withRoom(roots.sortedBy { orderOf(it.id) }))
        }.toMap(),
        character.box?.radius ?: 0f,
        character.box?.center ?: Vec3.ZERO,
    )
}
