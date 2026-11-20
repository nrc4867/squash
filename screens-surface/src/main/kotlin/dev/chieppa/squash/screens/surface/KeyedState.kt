package dev.chieppa.squash.screens.surface

import dev.chieppa.squash.character.surface.PosedSurface
import dev.chieppa.squash.character.viewkeys.ViewKeyShapes
import dev.chieppa.squash.character.viewkeys.keyed
import dev.chieppa.squash.character.viewkeys.weightsFor
import dev.chieppa.squash.geom.Vec3

/**
 * This state as a viewer whose way from a point is [toViewer] sees it with [shapes]' head cheats blended in: its body, lines and one
 * surface with its paint moved, everything else as it is. Itself where no cheat moves anything.
 */
fun CharacterState.keyedFor(shapes: ViewKeyShapes, toViewer: (Vec3) -> Vec3): CharacterState {
    if (shapes.isEmpty) return this
    val weights = shapes.weightsFor(character, toViewer)
    val keyed = shapes.keyed(character, lines, work, PosedSurface(surface, paint, dress, creases = creases), weights)
    val moved = keyed.surface ?: return this
    return CharacterState(keyed.body, color, work, palette, keyed.lines, fur, moved.surface, taper, shown, moved.paint, dress, only, creases)
}
