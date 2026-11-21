package dev.chieppa.squash.character.hair.fit

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.hair.Scalp
import dev.chieppa.squash.character.hair.ScalpField
import dev.chieppa.squash.character.hair.fittedMassShape
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.HairOutline
import dev.chieppa.squash.document.LockGroup
import dev.chieppa.squash.document.LockOverride
import dev.chieppa.squash.document.LockTip
import dev.chieppa.squash.document.MOST_LOCKS
import dev.chieppa.squash.document.OutlineView
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SetLockGroup
import dev.chieppa.squash.document.SetParameters
import dev.chieppa.squash.document.Together
import dev.chieppa.squash.document.hairOutlineOn
import dev.chieppa.squash.document.scalpFlowOn
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3

/** The order a fit takes its views in: a tip reached in an earlier view is not fitted again in a later one. */
private val VIEW_ORDER = listOf(OutlineView.FRONT, OutlineView.BACK, OutlineView.THREE_QUARTER, OutlineView.SIDE)

/** How many times a fit tries more locks for tips no lock reached, keeping them only where fewer tips are left unreached. */
private const val MORE_LOCK_PASSES = 3

/** How far past the fitted mass and the skull, as a share of the front outline's height, hair may reach in a view with no outline drawn. */
private const val MASS_MARGIN = 0.12f

/** The least a fitted group's locks keep of their width at the tip, so no lock ends in a thread. */
private const val LEAST_TAPER = 0.3f

/** A tip of the outline no lock reached: in which [view], [at] where in its flat frame, and [why]. */
data class UnreachedTip(val view: OutlineView, val at: Vec2, val why: Why) {
    enum class Why {
        /** No free lock's root faces the view. */
        NO_LOCK_FACES,

        /** Every lock facing the view went to another tip, and the groups near it can take no more. */
        TOO_FEW_LOCKS,

        /** Only locks that cannot serve it were left: a fringe's for a tip off the face, or locks facing away for one over it. */
        NO_LOCK_SERVES,

        /** No shape the lock sent to it may take, from where it roots, lands its tip there. */
        OUT_OF_REACH,

        /** The lock sent to it was kept short of it to stay inside the views. */
        KEPT_INSIDE,
    }
}

/**
 * What fitting a head's hair to its outline gives: the [commands] that set it, the inner mass's parameters and the lock groups that change;
 * how many locks it [fitted] to tips and [kept] inside the outline, and the tips no lock reached ([unreached]).
 */
class HairFit(val commands: List<DocumentCommand>, val fitted: Int, val kept: Int, val unreached: List<UnreachedTip>) {
    /** How many tips no lock reached. */
    val missed: Int get() = unreached.size

    /** One step that sets it all, or null when nothing changes. */
    val command: DocumentCommand? get() = if (commands.isEmpty()) null else Together(commands)
}

/**
 * The lock hair of [head] fitted to its outline, as [assembled] (the body with the head and its mass, without the locks) builds it, the
 * outline first sized to the head where it is given in a sheet's head lengths: the inner mass's crown and width sized to the outline's
 * envelope seen from the front, its sides and back hung to its hems; then in each view the outline's tips paired with the locks whose
 * roots face that view, each paired lock's root, length, rise, sweep, fall and width searched so its tip lands on its tip and its edges
 * follow the outline; every lock held to a least width for its length and inside every drawn view's outline and, where no outline is
 * drawn, within a margin of the mass and skull. Tied groups and locks with guides of their own are left as they are. A fit owns the
 * length, rise, sweep, fall, root and width overrides of the locks it reaches; it is deterministic, so the same outline fits the same
 * values. Null where the head has no outline, no locks or builds nothing. See HLD: parts, Hair locks.
 */
fun hairFit(character: Character, assembled: AssembledCharacter, head: PartId, outline: HairOutline? = character.hairOutlineOn(head)): HairFit? {
    outline ?: return null
    val headPart = assembled.part(head) ?: return null
    val originals = character.lockGroups.filter { it.on == head && it.tie == 0f }
    if (originals.isEmpty()) return null
    val drawn = drawnOutline(character, headPart, outline)
    val size = HeadView.sizeOf(character, headPart)
    val hairOn = character.lockGroups.filter { it.on == head }.mapNotNull { it.mass }.toSet()
    val occluders = assembled.parts.filter { it.id != null && it.locks == null && it.copies == null && it.role != Role.HAIR && it.id !in hairOn }
    val traced = VIEW_ORDER.mapNotNull { which ->
        drawn.view(which)?.let { FitView.of(HeadView.of(headPart, size, which), FlatOutline.of(it), occluders, if (which == OutlineView.FRONT) FRONT_SLACK else DRAWN_SLACK) }
    }
    val front = traced.first()
    val commands = ArrayList<DocumentCommand>()
    val massId = originals.firstNotNullOfOrNull { it.mass }
    val massPart = massId?.let(assembled::part)
    var massPoints = massPart?.shownPoints()
    if (massId != null && massPart != null) {
        val instance = character.part(massId)
        if (instance != null) {
            val data = character.resolvedShape(instance)
            val thickness = originals.map { it.thickness }.sorted()[originals.size / 2]
            val builds = MassBuilds(massPart, data)
            massPoints = builds.points(emptyMap())
            val least = headPart.box?.let { fittedMassShape(it.size.x, it.size.y) }.orEmpty()
            val behind = traced.firstOrNull { it.head.view == OutlineView.BACK }
            fitMass(front, behind, Scalp.of(headPart, null).middle, builds, massPart.template.defaults + data.params, thickness, least)?.let { refit ->
                massPoints = refit.points
                if (refit.params.isNotEmpty()) commands += SetParameters(massId, refit.params)
            }
        }
    }
    val held = massPoints.orEmpty() + skullOf(headPart, front.head)
    val spikes = held.maxOfOrNull { front.head.flat(it).y }?.let { (front.outline.high.y - it).coerceAtLeast(0f) } ?: 0f
    val around = VIEW_ORDER.filter { drawn.view(it) == null }.mapNotNull { which ->
        FitView.around(HeadView.of(headPart, size, which), held, front.height * MASS_MARGIN, spikes, occluders)
    }
    val views = traced + around
    val scalps = HashMap<PartId?, Scalp>()
    fun scalpOf(group: LockGroup): Scalp = scalps.getOrPut(group.mass) {
        if (group.mass == massId) Scalp.over(headPart, massPoints) else Scalp.of(headPart, group.mass?.let(assembled::part))
    }
    val field = ScalpField(character.scalpFlowOn(head))
    var groups = originals.map { it.copy(overrides = it.overrides.mapNotNull(::withoutFit), taper = it.taper.coerceAtLeast(LEAST_TAPER)) }
    var result = fitLocks(groups, views, ::scalpOf, field)
    for (pass in 0 until MORE_LOCK_PASSES) {
        if (result.short.isEmpty()) break
        val more = groups.map { g -> result.short[g.id]?.let { n -> g.copy(count = (g.count + n).coerceAtMost(MOST_LOCKS)) } ?: g }
        if (more == groups) break
        val tried = fitLocks(more, views, ::scalpOf, field)
        if (tried.unreached.size >= result.unreached.size) break
        groups = more
        result = tried
    }
    for ((original, group) in originals.zip(groups)) {
        val fitted = result.locks.filterKeys { it.first == group.id }
        val overrides = (0 until group.count).mapNotNull { index ->
            val base = group.overrideOf(index)
            fitted[group.id to index]?.over(base, index) ?: base
        }
        val now = group.copy(overrides = overrides)
        if (now != original) commands += SetLockGroup(group.id, now)
    }
    return HairFit(commands, result.fitted, result.kept, result.unreached)
}

/** [head]'s points above its origin as [front] sees it, in body space: the skull, which hair lies over in a view with no outline. */
private fun skullOf(head: PlacedPart, front: HeadView): List<Vec3> {
    val mesh = head.surface.mesh
    return (0 until mesh.vertexCount).map { head.origin + mesh.position(it) }.filter { front.flat(it).y >= 0f }
}

/** [override] with what a fit sets taken out, or null where nothing else is left. */
internal fun withoutFit(override: LockOverride): LockOverride? {
    val left = override.copy(
        length = null, rise = null, sweep = null, fall = null, around = null, up = null, width = null, lie = null,
        tip = override.tip.takeIf { it != LockTip.FLICKED },
    )
    return if (override.guide != null) override else left.takeIf { it != LockOverride(override.index) }
}
