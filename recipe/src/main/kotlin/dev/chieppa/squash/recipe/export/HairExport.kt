package dev.chieppa.squash.recipe.export

import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.HairOutline
import dev.chieppa.squash.document.hairOutlineOn
import dev.chieppa.squash.recipe.model.RecipeHairOutline
import dev.chieppa.squash.recipe.model.RecipeOutlineView
import dev.chieppa.squash.document.LockGroup
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.ScalpFlow
import dev.chieppa.squash.recipe.model.RecipeHair
import dev.chieppa.squash.recipe.model.RecipeLockGroup
import dev.chieppa.squash.recipe.model.RecipeParting
import dev.chieppa.squash.recipe.model.RecipeScalpFlow

/**
 * [character]'s lock groups as recipe hair, one entry for each head and mass they grow on, every group with each of its values, and the
 * first entry on each head with that head's scalp flow where the character gives it one; the body's collision shapes are set up from the
 * body again where the character holds any.
 */
internal fun exportedHair(character: Character): List<RecipeHair> {
    val flowed = HashSet<PartId>()
    val outlined = HashSet<PartId>()
    return character.lockGroups.groupBy { it.on to it.mass }.entries.mapIndexed { i, (key, groups) ->
        val head = key.first
        RecipeHair(
            on = head.value,
            mass = key.second?.value,
            groups = groups.map(::exported),
            colliders = i == 0 && character.colliders.isNotEmpty(),
            flow = character.scalpFlows.firstOrNull { it.on == head }?.takeIf { flowed.add(head) }?.let(::exported),
            outline = character.hairOutlineOn(head)?.takeIf { outlined.add(head) }?.let(::exported),
        )
    }
}

private fun exported(outline: HairOutline) = RecipeHairOutline(
    views = outline.views.map { view -> RecipeOutlineView(view.view, view.points.map { listOf(it.x, it.y) }, view.tips, view.valleys) },
    fit = outline.fit,
)

private fun exported(flow: ScalpFlow) = RecipeScalpFlow(
    whorl = flow.whorl,
    part = flow.parting.let { RecipeParting(it.kind, it.at, it.length, it.clean, it.color?.value) },
    sweep = flow.sweep,
    sweepBack = flow.sweepBack,
    cowlicks = flow.cowlicks,
)

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
    flow = group.flow,
    locks = group.overrides.takeIf { it.isNotEmpty() },
)
