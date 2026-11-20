package dev.chieppa.squash.recipe.export

import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.LockGroup
import dev.chieppa.squash.recipe.model.RecipeHair
import dev.chieppa.squash.recipe.model.RecipeLockGroup

/**
 * [character]'s lock groups as recipe hair, one entry for each head and mass they grow on, every group with each of its values; the body's
 * collision shapes are set up from the body again where the character holds any.
 */
internal fun exportedHair(character: Character): List<RecipeHair> =
    character.lockGroups.groupBy { it.on to it.mass }.entries.mapIndexed { i, (key, groups) ->
        RecipeHair(
            on = key.first.value,
            mass = key.second?.value,
            groups = groups.map(::exported),
            colliders = i == 0 && character.colliders.isNotEmpty(),
        )
    }

private fun exported(group: LockGroup) = RecipeLockGroup(
    id = group.id.value,
    kind = group.kind,
    count = group.count,
    spread = group.spread,
    around = group.around,
    height = group.height,
    length = group.length,
    lengthJitter = group.lengthJitter,
    width = group.width,
    thickness = group.thickness,
    taper = group.taper,
    section = group.section,
    tip = group.tip,
    tipPoints = group.tipPoints,
    rise = group.rise,
    sweep = group.sweep,
    fall = group.fall,
    curl = group.curl,
    wave = group.wave,
    twist = group.twist,
    seed = group.seed,
    layer = group.layer,
    tie = group.tie,
    band = group.band?.value,
    color = group.color?.value,
    physics = group.physics,
    locks = group.overrides.takeIf { it.isNotEmpty() },
)
