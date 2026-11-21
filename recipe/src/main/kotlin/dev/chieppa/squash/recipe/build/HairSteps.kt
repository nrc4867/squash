package dev.chieppa.squash.recipe.build

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.character.hair.Scalp
import dev.chieppa.squash.character.hair.autoColliders
import dev.chieppa.squash.character.hair.headReach
import dev.chieppa.squash.character.hair.fittedMass
import dev.chieppa.squash.character.hair.presetFlow
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.recipe.model.RecipeMassFit
import dev.chieppa.squash.character.hair.presetGroups
import dev.chieppa.squash.document.Parting
import dev.chieppa.squash.document.ScalpFlow
import dev.chieppa.squash.document.SetScalpFlow
import dev.chieppa.squash.recipe.model.RecipeScalpFlow
import dev.chieppa.squash.document.LockGroup
import dev.chieppa.squash.document.LockGroupId
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.SetColliders
import dev.chieppa.squash.document.SetLockGroup
import dev.chieppa.squash.recipe.model.RecipeLockGroup
import dev.chieppa.squash.recipe.model.RecipeHairOutline
import dev.chieppa.squash.character.hair.fit.UnreachedTip
import dev.chieppa.squash.character.hair.fit.hairFit
import java.util.Locale
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.HairOutline
import dev.chieppa.squash.document.HairOutlineView
import dev.chieppa.squash.document.OutlineView
import dev.chieppa.squash.document.SetHairOutline

/** The body as built so far, every part bare, assembled once for the hair to measure its heads on. */
private fun BuildContext.bareBody(): AssembledCharacter {
    val character = document.character
    val library = TemplateLibrary(templates.parts, templates.plans)
    return assembleNow(assemblyParts(character), { character.resolved(it) ?: PartData() }, plan, library)
}

/**
 * The hair: on each head, its preset's groups sized to the head, each group the recipe names changing the preset's of that id or added
 * after them, and the preset's scalp flow with what the recipe's flow changes; and once, the body's collision shapes where any hair asks
 * for them.
 */
internal fun BuildContext.hair() {
    if (recipe.hair.isEmpty()) return
    val body by lazy { bareBody() }
    val taken = HashSet<String>()
    val fits = ArrayList<Pair<PartId, String>>()
    recipe.hair.forEachIndexed { i, hair ->
        val path = "$.hair[$i]"
        val on = partOf("$path.on", hair.on)
        if (hair.mass != null && hair.fitMass != null) fail("$path.fitMass", "give mass or fitMass, not both")
        val mass = hair.mass?.let { partOf("$path.mass", it) } ?: hair.fitMass?.let { fit -> fitted("$path.fitMass", on, body, fit) }
        val color = hair.color?.let { slot("$path.color", it) }
        val band = hair.band?.let { slot("$path.band", it) }
        val prefix = if (i == 0) "" else "${hair.on}-"
        val preset = hair.preset?.let { preset ->
            val reach = hair.reach ?: run {
                val head = body.part(on) ?: fail("$path.on", "${hair.on} builds nothing to grow hair on")
                headReach(Scalp.of(head, mass?.let(body::part)))
            }
            presetGroups(preset, on, mass, reach, prefix, color, band)
        }.orEmpty()
        val named = hair.groups.withIndex().associateBy { it.value.id }
        val fromPreset = preset.mapNotNull { group ->
            val change = named[group.id.value.removePrefix(prefix)] ?: named[group.id.value] ?: return@mapNotNull group
            if (change.value.removed) null else changed("$path.groups[${change.index}]", group, change.value)
        }
        val presetIds = preset.map { it.id.value }.toSet()
        val added = hair.groups.withIndex().filter { (_, g) -> (prefix + g.id) !in presetIds && g.id !in presetIds }.map { (j, g) ->
            val at = "$path.groups[$j]"
            if (g.removed) fail("$at.removed", "no preset group \"${g.id}\" to leave out; the preset's groups are ${preset.joinToString { it.id.value }.ifEmpty { "none" }}")
            val kind = g.kind ?: fail("$at.kind", "a group the preset does not give needs its kind: fringe, left, right, back, crown, strays or top")
            changed(at, guard(at) { LockGroup(LockGroupId(g.id), kind, on, mass, color = color) }, g)
        }
        for (group in fromPreset + added) {
            if (!taken.add(group.id.value)) fail(path, "a lock group is already called \"${group.id.value}\"")
            send(path, SetLockGroup(group.id, group))
        }
        val base = hair.preset?.let { presetFlow(it, on) }
        val flow = hair.flow?.let { flowed("$path.flow", base ?: ScalpFlow(on), it) } ?: base
        flow?.let { send("$path.flow", SetScalpFlow(on, it)) }
        hair.outline?.let { given ->
            send("$path.outline", SetHairOutline(on, outlined("$path.outline", on, given)))
            if (given.fit) fits += on to "$path.outline"
        }
    }
    if (recipe.hair.any { it.colliders }) send("$.hair", SetColliders(autoColliders(body)))
    if (fits.isEmpty()) return
    val massed = bareBody()
    for ((on, path) in fits) {
        val fit = guard(path) { hairFit(document.character, massed, on) } ?: continue
        fit.commands.forEach { send("$path.fit", it) }
        for (tip in fit.unreached) {
            val at = String.format(Locale.ROOT, "%.3f,%.3f", tip.at.x, tip.at.y)
            warn("$path.fit", "the ${tip.view.name.lowercase()} outline's tip at $at reached by no lock: ${UNREACHED_WHY.getValue(tip.why)}")
        }
    }
}

/** Why a tip was reached by no lock, as a recipe warning says it. */
private val UNREACHED_WHY = mapOf(
    UnreachedTip.Why.NO_LOCK_FACES to "no lock's root faces that view; add a group on that side",
    UnreachedTip.Why.TOO_FEW_LOCKS to "every lock facing it went to another tip and its group holds the most locks it may",
    UnreachedTip.Why.NO_LOCK_SERVES to "only fringe locks (for a tip off the face) or locks facing away (for a tip over it) were left; add locks to a group there",
    UnreachedTip.Why.OUT_OF_REACH to "no shape the lock sent to it may take lands its tip there; give a group rooted nearer it",
    UnreachedTip.Why.KEPT_INSIDE to "the lock sent to it was kept short of it to stay inside the outlines",
)

/** [given] as the document holds it, failing at [path] when a view or a point is out of its range. */
private fun BuildContext.outlined(path: String, on: PartId, given: RecipeHairOutline): HairOutline = guard(path) {
    if (given.views.none { it.view == OutlineView.FRONT }) fail("$path.views", "an outline needs its front view")
    HairOutline(
        on,
        given.views.mapIndexed { i, view ->
            val points = view.points.mapIndexed { j, p ->
                if (p.size != 2) fail("$path.views[$i].points[$j]", "a point is [x, y]")
                CurvePoint(p[0], p[1])
            }
            guard("$path.views[$i]") { HairOutlineView(view.view, points, view.tips, view.valleys, view.heads, view.eyes) }
        },
        given.fit,
    )
}

/** A mass fitted over head [on] as [body] builds it, added to the document; its id. */
private fun BuildContext.fitted(path: String, on: PartId, body: AssembledCharacter, fit: RecipeMassFit): PartId {
    val head = body.part(on) ?: fail(path, "${on.value} builds nothing to fit a mass over")
    val slot = fit.color?.let { slot("$path.color", it) }
    val made = guard(path) { fittedMass(document.character, head, on, fit.id?.let(::PartId), fit.params, slot) } ?: fail(path, "${on.value} builds no box to fit a mass to")
    if (fit.id != null && made.id.value != fit.id) fail("$path.id", "a part is already called \"${fit.id}\"")
    made.commands.forEach { send(path, it) }
    return made.id
}

/** [flow] with what [change] sets, failing at [path] when a value is out of its range. */
private fun BuildContext.flowed(path: String, flow: ScalpFlow, change: RecipeScalpFlow): ScalpFlow = guard(path) {
    flow.copy(
        whorl = change.whorl ?: flow.whorl,
        parting = change.part?.let { part ->
            Parting(part.kind, part.at, part.length, part.clean, part.color?.let { slot("$path.part.color", it) })
        } ?: flow.parting,
        sweep = change.sweep ?: flow.sweep,
        sweepBack = change.sweepBack ?: flow.sweepBack,
        cowlicks = change.cowlicks ?: flow.cowlicks,
    )
}

/** [group] with what [change] sets, failing at [path] when a value is out of its range. */
private fun BuildContext.changed(path: String, group: LockGroup, change: RecipeLockGroup): LockGroup = guard(path) {
    group.copy(
        kind = change.kind ?: group.kind,
        count = change.count ?: group.count,
        spread = change.spread ?: group.spread,
        around = change.around ?: group.around,
        height = change.height ?: group.height,
        length = change.length ?: group.length,
        lengthJitter = change.lengthJitter ?: group.lengthJitter,
        width = change.width ?: group.width,
        thickness = change.thickness ?: group.thickness,
        taper = change.taper ?: group.taper,
        section = change.section ?: group.section,
        tip = change.tip ?: group.tip,
        tipPoints = change.tipPoints ?: group.tipPoints,
        rise = change.rise ?: group.rise,
        sweep = change.sweep ?: group.sweep,
        fall = change.fall ?: group.fall,
        curl = change.curl ?: group.curl,
        wave = change.wave ?: group.wave,
        twist = change.twist ?: group.twist,
        seed = change.seed ?: group.seed,
        layer = change.layer ?: group.layer,
        tie = change.tie ?: group.tie,
        band = change.band?.let { slot("$path.band", it) } ?: group.band,
        color = change.color?.let { slot("$path.color", it) } ?: group.color,
        physics = change.physics ?: group.physics,
        flow = change.flow ?: group.flow,
        overrides = change.locks?.sortedBy { it.index } ?: group.overrides,
    )
}
