package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.geom.PartSurface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** How many planned surfaces [PlannedSurfaces] keeps, the most recently used first. */
private const val PLANNED_KEPT = 48

/**
 * The surfaces of templates with nothing drawn, kept for the whole process. One depends only on its template, its side, its lean and its
 * length, so every session and every screen with no session shares them; a template saved anew is a new template and builds its own.
 */
object PlannedSurfaces {
    private data class Key(val template: PartTemplate, val mirrored: Boolean, val spread: Float, val lengthFactor: Float)

    private val kept = MutableStateFlow<List<Pair<Key, PartSurface>>>(emptyList())

    /** [template]'s surface with nothing drawn, [mirrored] or not, leaning [spread] degrees out, at [lengthFactor] of its length. */
    fun of(template: PartTemplate, mirrored: Boolean, spread: Float, lengthFactor: Float, checkCancelled: () -> Unit = {}): PartSurface {
        val key = Key(template, mirrored, spread, lengthFactor)
        kept.value.firstOrNull { it.first == key }?.let { (_, surface) ->
            kept.update { held -> listOf(key to surface) + held.filter { it.first != key } }
            return surface
        }
        val built = surfaceOf(template, plannedData(spread), mirrored, checkCancelled, lengthFactor)
        kept.update { held -> (listOf(key to built) + held.filter { it.first != key }).take(PLANNED_KEPT) }
        return built
    }
}
