package dev.chieppa.squash.character.viewkeys

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.held
import dev.chieppa.squash.character.surface.HeldSurface
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.graph.Graph
import dev.chieppa.squash.graph.Node
import dev.chieppa.squash.graph.cancellationCheck
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private enum class KeysSource { ASSEMBLY, SURFACE }

/**
 * The character's head cheats, built once an assembly and its one surface at rest (see [ViewKeyShapes.of]), with the views blended so far
 * kept; none while the surface is a drag's coarse one, so a drag never waits on them.
 */
fun Graph.viewKeysNode(document: Flow<SquashDocument>, assembly: Node<*, AssembledCharacter>, surface: Node<*, HeldSurface>): Node<*, ViewKeyCache> {
    val sources = mapOf(KeysSource.ASSEMBLY to held(assembly), KeysSource.SURFACE to held(surface))
    return gather(document.map { it.character.viewKeys }.distinctUntilChanged(), sourcesOf = { sources }, cacheSize = 1) { (keys, held) ->
        val character = held.getValue(KeysSource.ASSEMBLY).value as AssembledCharacter
        val shown = held.getValue(KeysSource.SURFACE).value as HeldSurface
        if (shown.coarse) ViewKeyCache(ViewKeyShapes.none(character, shown.surface))
        else ViewKeyCache(ViewKeyShapes.of(character, shown.surface, keys, cancellationCheck()))
    }
}
