package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.BuildStages
import dev.chieppa.squash.graph.Graph
import dev.chieppa.squash.graph.Node
import kotlinx.coroutines.flow.MutableStateFlow

/** What the shown-paint node reads. */
private sealed interface ShownSource {
    class Body(val held: HeldSurface) : ShownSource

    class Paint(val held: HeldPaint) : ShownSource

    class Dress(val held: HeldDress) : ShownSource
}

private enum class ShownKey { BODY, PAINT, DRESS }

/**
 * The [paint] on [surface] as it shows under its [dress]: each layer kept to the skin the garments leave showing, so paint never shows
 * through a garment over hidden skin. The paint as it is where nothing is worn.
 */
fun Graph.shownPaintNode(surface: Node<*, HeldSurface>, paint: Node<*, HeldPaint>, dress: Node<*, HeldDress>): Node<*, HeldPaint> {
    val body = follow<HeldSurface, ShownSource>(surface, cacheSize = KEPT_BUILDS, stage = BuildStages.ASSEMBLY) { ShownSource.Body(it) }
    val painted = follow<HeldPaint, ShownSource>(paint, cacheSize = KEPT_BUILDS, stage = BuildStages.ASSEMBLY) { ShownSource.Paint(it) }
    val worn = follow<HeldDress, ShownSource>(dress, cacheSize = KEPT_BUILDS, stage = BuildStages.ASSEMBLY) { ShownSource.Dress(it) }
    return gather(MutableStateFlow(Unit), { mapOf(ShownKey.BODY to body, ShownKey.PAINT to painted, ShownKey.DRESS to worn) }, cacheSize = KEPT_BUILDS, stage = BuildStages.ASSEMBLY) { gathered ->
        val held = (gathered.values[ShownKey.PAINT] as ShownSource.Paint).held
        val on = (gathered.values[ShownKey.BODY] as ShownSource.Body).held.surface
        val dress = (gathered.values[ShownKey.DRESS] as ShownSource.Dress).held.dress
        val shown = dress?.shown
        val laid = held.paint
        if (on == null || laid == null || shown == null || !dress.isOn(on) || !held.isOn(on)) held else HeldPaint(laid.within(shown, on), on)
    }
}
