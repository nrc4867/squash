package dev.chieppa.squash.character.hair

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.BuildStages
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.HairShell
import dev.chieppa.squash.document.LockGroup
import dev.chieppa.squash.document.LockGroupId
import dev.chieppa.squash.document.LockGroupKind
import dev.chieppa.squash.document.LockPhysics
import dev.chieppa.squash.document.LockSection
import dev.chieppa.squash.document.LockTip
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Parting
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.ScalpFlow
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.lockGroupPartId
import dev.chieppa.squash.geom.AxisSpline
import dev.chieppa.squash.geom.BandBuilder
import dev.chieppa.squash.geom.BraidBuilder
import dev.chieppa.squash.geom.ClumpShape
import dev.chieppa.squash.geom.LockBuilder
import dev.chieppa.squash.geom.LockProfile
import dev.chieppa.squash.geom.MeshBuilder
import dev.chieppa.squash.geom.PartMesh
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.SlottedCut
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.graph.Graph
import dev.chieppa.squash.graph.Node
import kotlinx.coroutines.flow.Flow
import kotlin.math.abs

/** The template a lock group's part takes: a plain lock of role hair. */
const val LOCK_TEMPLATE = "hair"

/** The points round a lock's ring. */
const val LOCK_SEGMENTS = 10

/** The rings along a lock with no curl, and how many each turn of curl adds, at most [MOST_LOCK_RINGS]. */
private const val LOCK_RINGS = 14
private const val RINGS_PER_TURN = 8
private const val MOST_LOCK_RINGS = 30

/** A forked tip's cut back between its points, as a share of the lock's length. */
private const val FORK_NOTCH = 0.14f

/** How far a lock's tip closes over, as a share of its length, by its tip. */
private val TIP_REACH = mapOf(LockTip.POINTED to 0.3f, LockTip.ROUND to 0.15f, LockTip.FORKED to 0.25f, LockTip.FLICKED to 0.2f)

/** How far behind a lock group's contour lines lie, as a share of its locks' thickness: past a neighbour of its group, short of a group laid over it. */
private const val CONTOUR_TUCK = 0.45f

/**
 * A crown's tuck, short of how far every other lock stands off its neighbours: the edge of each lock lying over the next draws, so the
 * locks spiralling out of a whorl read as a spiral.
 */
private const val CROWN_TUCK = 0.12f

/** A band's ring round a tie, as a share of the locks' width, and its thickness, as a share of theirs. */
private const val BAND_RADIUS = 0.55f
private const val BAND_THICKNESS = 1.6f

/** The patch name of lock [index] in its group's mesh. */
fun lockPatch(index: Int): String = "lock.$index"

/** The patch name of a group's band. */
const val BAND_PATCH = "band"

/** The part id a lock group's band is built as. */
fun lockBandPartId(id: LockGroupId): PartId = PartId(lockGroupPartId(id).value + "~band")

/**
 * A lock's chain at rest, in body space: its [joints] from its root to its tip, one more than its bones, and how it moves. It carries its
 * [patch] and any [others] the lock is built of, a braid's further strands, tie and tail.
 */
data class LockChain(val index: Int, val patch: String, val joints: List<Vec3>, val physics: LockPhysics, val others: List<String> = emptyList()) {
    val bones: Int get() = joints.size - 1

    /** Every patch the chain carries. */
    val patches: List<String> get() = listOf(patch) + others
}

/**
 * What a lock group's part carries beside its mesh: the [group], the [head] whose bones carry it, one [chains] entry a lock in the order of
 * its patches, and the [neighbours] each lock is tied to in its group, by place in [chains]; for a group's band, no chain at all. Its
 * contour lines lie [tuck] behind where they are drawn, so a lock's edge over another lock of its group, which lies within that of it,
 * draws no line, while its edge over the face or another group does. Its [layers] are colours laid over its own on whole patches or
 * fading over it, as a braid's ties in the group's band slot and a shell's skin.
 */
class LockPlacing(
    val group: LockGroupId,
    val head: PartId,
    val chains: List<LockChain>,
    val neighbours: List<Pair<Int, Int>> = emptyList(),
    val tuck: Float = 0f,
    val layers: List<SlottedCut<SlotName>> = emptyList(),
)

/** One lock as built: what it was built from, its guide curve, its [patches], the first its own, a braid's [tie] among them, and its chain. */
class BuiltLock(val spec: LockSpec, val guide: List<Vec3>, val patches: List<Patch>, val chain: LockChain, val tie: Patch? = null) {
    val patch: Patch get() = patches.first()
}

/** The shape of a lock's mesh along its guide, from what [lock] says. */
fun clumpShapeOf(lock: LockSpec): ClumpShape = ClumpShape(
    width = lock.width,
    thickness = lock.thickness,
    taper = lock.taper,
    swell = 0.12f,
    tipRound = if (lock.tip == LockTip.ROUND) 1f else 0f,
    tipReach = TIP_REACH.getValue(lock.tip),
    points = if (lock.tip == LockTip.FORKED) lock.tipPoints else 1,
    notch = if (lock.tip == LockTip.FORKED) FORK_NOTCH else 0f,
    twist = lock.twist,
    wave = lock.wave,
    waves = 2f,
    flare = 0f,
)

/**
 * [lock] built over [scalp]: its guide, cut to its trim where it has one, its patch named for its index, or a braid's strands, tie and
 * tail, and its chain of bones along the guide.
 */
fun buildLock(lock: LockSpec, scalp: Scalp): BuiltLock {
    val grown = guideOf(lock, scalp)
    val cut = lock.trim?.let { trimmed(grown, it) }
    val guide = cut?.guide ?: grown
    val axis = AxisSpline(guide)
    val along = List(4 * guide.size) { axis.pointAt(it.toFloat() / (4 * guide.size - 1)) }
    val joints = evenly(along, lock.bones + 1)
    if (lock.section == LockSection.BRAID) {
        val braid = BraidBuilder.patches(lockPatch(lock.index), guide, lock.out, lock.width, lock.thickness, lock.taper, LOCK_SEGMENTS)
        val patches = braid.all
        return BuiltLock(lock, guide, patches, LockChain(lock.index, patches.first().name, joints, lock.physics, patches.drop(1).map { it.name }), braid.tie)
    }
    val rings = (LOCK_RINGS + abs(lock.curl) * RINGS_PER_TURN).toInt().coerceAtMost(MOST_LOCK_RINGS)
    val profile = if (lock.section == LockSection.RIBBON) LockProfile.RIBBON else LockProfile.CLUMP
    val lying = if (lock.curl != 0f) curlStart(lock.curl) else 1f
    val middle = if (lock.tied) null else scalp.middle
    val whole = clumpShapeOf(lock)
    val shape = if (cut != null && lock.trim?.blunt == true) bluntAt(whole, cut.share, lock.length * cut.share) else whole
    val patch = LockBuilder.patch(lockPatch(lock.index), guide, lock.out, shape, profile, rings, LOCK_SEGMENTS, middle, lying)
    return BuiltLock(lock, guide, listOf(patch), LockChain(lock.index, patch.name, joints, lock.physics))
}

/**
 * Lock groups as parts of their own, kept from one build to the next: a lock built as one before is taken as it was, and a group whose
 * locks and band are all as they were is the same part as before, so editing one lock builds only that lock and editing one group only
 * that group. Kept are only what the last build used.
 */
class LockBuilds(private val template: PartTemplate?) {
    private var locks: Map<Pair<LockSpec, Scalp>, BuiltLock> = emptyMap()
    private var groups: Map<LockGroupId, HeldGroup> = emptyMap()
    private var strips: Map<PartId, HeldStrip> = emptyMap()

    private class HeldGroup(val locks: List<BuiltLock>, val group: LockGroup, val part: PlacedPart?, val band: PlacedPart?)

    private class HeldStrip(val parting: Parting, val scalp: Scalp, val part: PlacedPart)

    private class HeldShell(val shell: ShellSpec, val head: PlacedPart, val part: PlacedPart)

    private var shells: Map<PartId, HeldShell> = emptyMap()

    /** How many locks the last [withLocks] built afresh rather than took as they were. */
    var lastBuilt: Int = 0
        private set

    /**
     * [character] with each of [shown]'s groups on it, as a part of its own and its band as another, each lock growing as its head's flow
     * in [flows] says, or the default flow where it gives none, the strip of scalp along each such head's parting, and each of [cut]'s
     * short-hair shells over its head; a group or shell whose head is not there builds nothing. A shell is built again only when it or its
     * head changes.
     */
    fun withLocks(character: AssembledCharacter, shown: List<LockGroup>, flows: List<ScalpFlow> = emptyList(), cut: List<ShellSpec> = emptyList()): AssembledCharacter {
        val keptShells = HashMap<PartId, HeldShell>()
        for (spec in cut) {
            val head = character.part(spec.shell.on) ?: continue
            val before = shells[spec.shell.on]
            keptShells[spec.shell.on] = if (before != null && before.shell == spec && before.head === head) before else HeldShell(spec, head, shellPart(spec.shell.on, head, spec))
        }
        shells = keptShells
        val shellParts = cut.mapNotNull { keptShells[it.shell.on]?.part }
        if (shown.isEmpty()) {
            locks = emptyMap()
            groups = emptyMap()
            strips = emptyMap()
            return if (shellParts.isEmpty()) character else AssembledCharacter(character.parts + shellParts, character.sockets)
        }
        val keptLocks = HashMap<Pair<LockSpec, Scalp>, BuiltLock>()
        val keptGroups = HashMap<LockGroupId, HeldGroup>()
        val keptStrips = HashMap<PartId, HeldStrip>()
        val scalps = HashMap<Pair<PlacedPart, PlacedPart?>, Scalp>()
        val fields = HashMap<PartId, ScalpField>()
        var built = 0
        val added = ArrayList<PlacedPart>(shellParts)
        for (group in shown) {
            val head = character.part(group.on) ?: continue
            val mass = group.mass?.let(character::part)
            val scalp = scalps.getOrPut(head to mass) { Scalp.of(head, mass) }
            val field = fields.getOrPut(group.on) { ScalpField(flows.firstOrNull { it.on == group.on } ?: ScalpFlow(group.on)) }
            val line = field.partLine
            if (line != null && group.on !in keptStrips) {
                val before = strips[group.on]
                val held = if (before != null && before.parting == field.flow.parting && before.scalp == scalp) before else HeldStrip(field.flow.parting, scalp, stripPart(group.on, head, mass, line, scalp))
                keptStrips[group.on] = held
                added += held.part
            }
            val made = locksOf(group, scalp, field).map { spec ->
                val key = spec to scalp
                keptLocks[key] ?: (locks[key] ?: buildLock(spec, scalp).also { built++ }).also { keptLocks[key] = it }
            }
            val before = groups[group.id]
            val held = if (before != null && before.group.band == group.band && before.group.color == group.color && before.group.section == group.section && sameLocks(before.locks, made)) {
                before
            } else {
                HeldGroup(made, group, groupPart(group, head, made), bandPart(group, head, made, scalp))
            }
            keptGroups[group.id] = held
            held.part?.let(added::add)
            held.band?.let(added::add)
        }
        locks = keptLocks
        groups = keptGroups
        strips = keptStrips
        lastBuilt = built
        return if (added.isEmpty()) character else AssembledCharacter(character.parts + added, character.sockets)
    }

    private fun sameLocks(a: List<BuiltLock>, b: List<BuiltLock>): Boolean = a.size == b.size && a.indices.all { a[it] === b[it] }

    private fun groupPart(group: LockGroup, head: PlacedPart, made: List<BuiltLock>): PlacedPart? {
        if (made.isEmpty()) return null
        val id = lockGroupPartId(group.id)
        val chains = made.map { it.chain }
        val neighbours = (0 until made.size - 1).map { it to it + 1 }
        val tight = (group.kind == LockGroupKind.CROWN && group.tie == 0f) || group.section == LockSection.BRAID
        val tuck = made.maxOf { it.spec.thickness } * if (tight) CROWN_TUCK else CONTOUR_TUCK
        val placing = LockPlacing(group.id, head.id ?: group.on, chains, neighbours, tuck)
        val band = group.band ?: return placed(id, head, made.flatMap { it.patches }, placing)
        return placed(id, head, made.flatMap { it.patches }, placing) { surface ->
            made.mapNotNull { lock -> lock.tie?.let { wholePatchCut(surface, it.name) }?.let { SlottedCut(band, it) } }
        }
    }

    private fun shellPart(on: PartId, head: PlacedPart, shell: ShellSpec): PlacedPart {
        val mesh = ShellMesh.of(shell.shell, head)
        val placing = LockPlacing(shellGroupId(on), head.id ?: on, emptyList(), tuck = mesh.tuck)
        val skin = shell.skin ?: return placed(shellPartId(on), head, listOf(mesh.patch), placing)
        return placed(shellPartId(on), head, listOf(mesh.patch), placing) { surface ->
            listOfNotNull(wholePatchCut(surface, SHELL_PATCH, mesh.skin)?.let { SlottedCut(skin, it) })
        }
    }

    private fun bandPart(group: LockGroup, head: PlacedPart, made: List<BuiltLock>, scalp: Scalp): PlacedPart? {
        if (group.band == null || made.isEmpty() || group.section == LockSection.BRAID) return null
        val roots = made.map { it.guide.first() }
        val middle = roots.fold(Vec3.ZERO) { s, p -> s + p } * (1f / roots.size)
        val ahead = made.map { it.guide[1] - it.guide[0] }.fold(Vec3.ZERO) { s, d -> s + d }.let { if (it.length > 0f) it.normalized() else (middle - scalp.middle).normalized() }
        val spread = roots.maxOf { (it - middle).length }
        val width = made.maxOf { it.spec.width }
        val thickness = made.maxOf { it.spec.thickness }
        val reach = made.minOf { it.spec.length } * 0.12f
        val center = middle + ahead * (reach + made.first().spec.sink)
        val patch = BandBuilder.patch(BAND_PATCH, center, ahead, spread + width * BAND_RADIUS, thickness * BAND_THICKNESS)
        return placed(lockBandPartId(group.id), head, listOf(patch), LockPlacing(group.id, head.id ?: group.on, emptyList()))
    }

    private fun stripPart(on: PartId, head: PlacedPart, mass: PlacedPart?, line: PartLine, scalp: Scalp): PlacedPart {
        val strip = PartingStrip.of(line, scalp, head.shownPoints() + mass?.shownPoints().orEmpty())
        return placed(partingPartId(on), head, listOf(strip.patch), LockPlacing(partingGroupId(on), head.id ?: on, emptyList(), tuck = strip.tuck))
    }

    private fun placed(
        id: PartId,
        head: PlacedPart,
        patches: List<Patch>,
        placing: LockPlacing,
        layers: (PartSurface) -> List<SlottedCut<SlotName>> = { emptyList() },
    ): PlacedPart {
        val template = template ?: head.template
        val mesh = PartMesh(id.value, patches.map { patch -> MeshBuilder.build(patch) { 0 } })
        val surface = PartSurface.of(mesh, PartBuilder.axes(template), mirrored = false)
        val laid = layers(surface)
        val withLayers = if (laid.isEmpty()) placing else LockPlacing(placing.group, placing.head, placing.chains, placing.neighbours, placing.tuck, laid)
        return PlacedPart(id, template, null, surface, Vec3.ZERO, role = Role.HAIR, locks = withLayers)
    }
}

/** A head's short-hair [shell] as its build reads it, with the [skin] slot its edge and fade show, the head's base slot. */
data class ShellSpec(val shell: HairShell, val skin: SlotName?)

/** A character's lock hair as its build reads it: its lock [groups], the [flows] over its heads and the short-hair [shells] on them. */
data class LockHair(val groups: List<LockGroup>, val flows: List<ScalpFlow>, val shells: List<ShellSpec> = emptyList())

/** [assembled] with the lock hair [hair] gives on it, each group built by [builds], which keeps what it built before. */
fun Graph.lockGroupsNode(assembled: Node<*, AssembledCharacter>, hair: Flow<LockHair>, builds: LockBuilds): Node<*, AssembledCharacter> = gather(
    hair,
    sourcesOf = { mapOf(Unit to assembled) },
    stage = BuildStages.ASSEMBLY,
) { (shown, built) ->
    builds.withLocks(built.getValue(Unit), shown.groups, shown.flows, shown.shells)
}
