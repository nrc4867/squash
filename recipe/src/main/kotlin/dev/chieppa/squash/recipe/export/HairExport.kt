package dev.chieppa.squash.recipe.export

import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.HairOutline
import dev.chieppa.squash.document.HairShell
import dev.chieppa.squash.document.hairOutlineOn
import dev.chieppa.squash.document.hairShellOn
import dev.chieppa.squash.recipe.model.RecipeHairShell
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
 * body again where the character holds any. Each short-hair shell goes with the first entry on its head, or an entry of its own on a head
 * with no locks.
 */
internal fun exportedHair(character: Character): List<RecipeHair> {
    val flowed = HashSet<PartId>()
    val outlined = HashSet<PartId>()
    val shelled = HashSet<PartId>()
    val locked = character.lockGroups.groupBy { it.on to it.mass }.entries.mapIndexed { i, (key, groups) ->
        val head = key.first
        RecipeHair(
            on = head.value,
            mass = key.second?.value,
            groups = groups.map(::exported),
            colliders = i == 0 && character.colliders.isNotEmpty(),
            flow = character.scalpFlows.firstOrNull { it.on == head }?.takeIf { flowed.add(head) }?.let(::exported),
            outline = character.hairOutlineOn(head)?.takeIf { outlined.add(head) }?.let(::exported),
            shell = character.hairShellOn(head)?.takeIf { shelled.add(head) }?.let(::exported),
        )
    }
    val bare = character.hairShells.filter { it.on !in shelled }.map { RecipeHair(on = it.on.value, colliders = false, shell = exported(it)) }
    return locked + bare
}

private fun exported(shell: HairShell) = RecipeHairShell(
    hairline = shell.hairline,
    thickness = shell.thickness,
    tone = shell.tone?.value,
    shade = shell.shade,
    soft = shell.soft,
    fade = shell.fade,
    fadeTo = shell.fadeTo,
    steps = shell.steps,
)

private fun exported(outline: HairOutline) = RecipeHairOutline(
    views = outline.views.map { view -> RecipeOutlineView(view.view, view.points.map { listOf(it.x, it.y) }, view.tips, view.valleys, view.heads, view.eyes) },
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
    lie = group.lie,
    trim = group.trim,
    coil = group.coil,
    stand = group.stand,
    locks = group.overrides.takeIf { it.isNotEmpty() },
)
