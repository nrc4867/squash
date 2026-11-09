package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.PartId

/**
 * [rest], every part's surface work on the character at rest, resolved again on [body], a pose of it: a part whose work names a part
 * the pose [moved] turns its anchors into points on [body]'s surfaces, as at rest but on the posed grids, from what [inputs] holds for
 * it; every other part keeps its work from [rest]. [checkCancelled] runs before each part resolved again.
 */
fun posedSurfaceWork(
    rest: BodySurfaceWork,
    inputs: Map<PartId, SurfaceWorkInput>,
    body: AssembledCharacter,
    moved: Set<PartId>,
    checkCancelled: () -> Unit = {},
): BodySurfaceWork = BodySurfaceWork(
    rest.parts.mapValues { (id, held) ->
        val input = inputs[id]?.takeIf { input -> input.parts.any { it in moved } } ?: return@mapValues held
        checkCancelled()
        val surfaces = input.parts.mapNotNull { part -> body.part(part)?.let { part to it.surface } }.toMap()
        placeSurfaceWork(mapOf(id to resolveSurfaceWork(input, surfaces)), body).parts.getValue(id)
    },
)

/**
 * [rest], every part's fur on the character at rest, on [body], a pose of it: a part the pose [moved], or whose surface work [workMoved]
 * says moved, roots its strokes on [body] again from [work] with the grain carried along the posed surface, and takes its rims from the
 * posed surface; each stroke keeps its room and the slot under it from [rest], since both are the body's own, and the strokes keep
 * their order. Every other part keeps its fur from [rest]. [checkCancelled] runs before each part rooted again.
 */
fun posedFur(
    rest: BodyFur,
    inputs: Map<PartId, PartFurInput>,
    work: BodySurfaceWork,
    body: AssembledCharacter,
    carried: Set<PartId>,
    moved: Set<PartId>,
    workMoved: (PartId) -> Boolean,
    checkCancelled: () -> Unit = {},
): BodyFur {
    val worn by lazy { body.garmentCovers() }
    val parts = rest.parts.mapValues { (id, fur) ->
        val input = inputs[id]?.takeIf { id in carried || workMoved(id) }
        val held = work.parts[id]
        checkCancelled()
        when {
            input != null && held != null -> fur.rootedOn(id, input, held, body, worn)
            id in moved && (fur.covered.isNotEmpty() || fur.roots.isNotEmpty()) -> fur.coveredAgain(worn)
            else -> fur
        }
    }
    return BodyFur(parts, body.box?.radius ?: rest.figureRadius, body.box?.center ?: rest.figureCenter)
}
