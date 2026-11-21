package dev.chieppa.squash.document

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/** A lock group's id. */
@Serializable
@JvmInline
value class LockGroupId(val value: String)

/** Where on the head a group of locks roots, and the way it grows from there until a flow field says otherwise. */
@Serializable
enum class LockGroupKind {
    @SerialName("fringe") FRINGE,
    @SerialName("left") LEFT,
    @SerialName("right") RIGHT,
    @SerialName("back") BACK,
    @SerialName("crown") CROWN,
    @SerialName("strays") STRAYS,
    @SerialName("top") TOP,
}

/** A lock's cross-section: a flattened ribbon, a rounded clump, or a braid of three strands. */
@Serializable
enum class LockSection {
    @SerialName("ribbon") RIBBON,
    @SerialName("clump") CLUMP,
    @SerialName("braid") BRAID,
}

/** How a lock ends. */
@Serializable
enum class LockTip {
    @SerialName("pointed") POINTED,
    @SerialName("round") ROUND,
    @SerialName("forked") FORKED,
    @SerialName("flicked") FLICKED,
}

/** The fewest and most locks a group holds. */
const val LEAST_LOCKS = 1
const val MOST_LOCKS = 48

/** The fewest and most bones a lock's chain holds. */
const val LEAST_LOCK_BONES = 3
const val MOST_LOCK_BONES = 6

/** The most turns a lock curls either way. */
const val MOST_LOCK_CURL = 3f

/** The shortest and longest a lock is, in body units. */
const val SHORTEST_LOCK = 0.005f
const val LONGEST_LOCK = 3f

/** The narrowest and widest a lock is at its root, in body units. */
const val NARROWEST_LOCK = 0.002f
const val WIDEST_LOCK = 0.5f

/**
 * How a lock moves once a solver drives it: how firmly it keeps its rest shape ([stiffness]), how quickly its swing dies ([damping]), how
 * heavy it is ([weight]), how much gravity and wind pull it, how far each bone may turn from its rest in degrees ([angleLimit]), and how
 * firmly it is tied to the locks either side of it in its group ([neighbours], 0 untied).
 */
@Serializable
@Doc("How a lock moves once physics drives it. Nothing moves yet; these are kept with the character for a solver.")
data class LockPhysics(
    @Doc("How firmly it keeps its rest shape, 0 limp to 1 rigid.") @Range(min = 0.0, max = 1.0)
    val stiffness: Float = 0.6f,
    @Doc("How quickly its swing dies, 0 never to 1 at once.") @Range(min = 0.0, max = 1.0)
    val damping: Float = 0.3f,
    @Doc("How heavy it is, 1 a lock of its size.") @Range(min = 0.0, max = 4.0)
    val weight: Float = 1f,
    @Doc("How much gravity pulls it, 1 fully.") @Range(min = 0.0, max = 2.0)
    val gravity: Float = 1f,
    @Doc("How much wind moves it, 1 fully.") @Range(min = 0.0, max = 2.0)
    val wind: Float = 1f,
    @Doc("How far each bone may turn from its rest, in degrees.") @Range(min = 0.0, max = 180.0)
    val angleLimit: Float = 45f,
    @Doc("How firmly it is tied to the locks either side of it in its group, 0 untied to 1 moving as one.") @Range(min = 0.0, max = 1.0)
    val neighbours: Float = 0.3f,
) {
    init {
        require(stiffness in 0f..1f && damping in 0f..1f && neighbours in 0f..1f) { "A lock's stiffness, damping and neighbours are 0 to 1" }
        require(weight in 0f..4f) { "A lock's weight is 0 to 4, not $weight" }
        require(gravity in 0f..2f && wind in 0f..2f) { "A lock's gravity and wind are 0 to 2" }
        require(angleLimit in 0f..180f) { "A lock's angle limit is 0 to 180 degrees, not $angleLimit" }
    }
}

/** A point of a lock's own guide curve: an offset from its root, in body units along the body's axes at rest (x forward, y up, z right). */
@Serializable
@Doc("A point of a lock's guide curve: [x, y, z] from its root, in body units, x forward, y up, z the character's right.")
data class LockPoint(val x: Float, val y: Float, val z: Float)

/**
 * One lock of a group pulled out and set by hand, by its [index] in the group: any value it gives replaces the group's for that lock alone,
 * [guide] replaces its grown curve with a few points of its own, and [hidden] leaves it out.
 */
@Serializable
@Doc("One lock of a group set by hand, by its index: each value given replaces the group's for that lock alone.")
data class LockOverride(
    @Doc("The lock, by its place in the group from 0.") @Range(min = 0.0)
    val index: Int,
    @Doc("Its length, in body units.") @Range(min = 0.005, max = 3.0)
    val length: Float? = null,
    @Doc("Its width at the root, in body units.") @Range(min = 0.002, max = 0.5)
    val width: Float? = null,
    @Doc("Its thickness at the root, in body units.") @Range(min = 0.002, max = 0.5)
    val thickness: Float? = null,
    @Doc("How many turns it curls, toward its underside; negative the other way.") @Range(min = -3.0, max = 3.0)
    val curl: Float? = null,
    @Doc("How far it waves sideways, a share of its width.") @Range(min = 0.0, max = 2.0)
    val wave: Float? = null,
    @Doc("How far it turns about itself over its length, in degrees.") @Range(min = -360.0, max = 360.0)
    val twist: Float? = null,
    @Doc("Degrees its outer part lifts off the head, where it lies along the head from its root: 0 lies flat all along, 90 stands its end straight out. A tied lock leaves its tie at this angle.") @Range(min = -30.0, max = 90.0)
    val rise: Float? = null,
    @Doc("How far it sweeps sideways as it grows, -1 to 1: positive toward the character's right.") @Range(min = -1.0, max = 1.0)
    val sweep: Float? = null,
    @Doc("How far it falls toward the ground along its length, 0 none to 1 hanging.") @Range(min = 0.0, max = 1.0)
    val fall: Float? = null,
    @Doc("How much of its length lies along the head before it lifts off by its rise, 0.05 to 0.95: low stands it out from near its root, a spike; 0.55 when left out.") @Range(min = 0.05, max = 0.95)
    val lie: Float? = null,
    @Doc("Degrees its root moves round the head from where its group lays it, positive toward the character's right.") @Range(min = -45.0, max = 45.0)
    val around: Float? = null,
    @Doc("Degrees its root moves up the head from where its group lays it.") @Range(min = -45.0, max = 45.0)
    val up: Float? = null,
    val section: LockSection? = null,
    val tip: LockTip? = null,
    @Doc("Its guide curve: a few points from its root, in place of the curve its group grows it along.")
    val guide: List<LockPoint>? = null,
    @Doc("How many bones its chain holds, 3 to 6; by its length when left out.") @Range(min = 3.0, max = 6.0)
    val bones: Int? = null,
    @Doc("How it moves, in place of its group's.")
    val physics: LockPhysics? = null,
    @Doc("Whether it is left out.")
    val hidden: Boolean = false,
) {
    init {
        require(index >= 0) { "A lock's index is 0 or more, not $index" }
        require(bones == null || bones in LEAST_LOCK_BONES..MOST_LOCK_BONES) { "A lock's chain holds $LEAST_LOCK_BONES to $MOST_LOCK_BONES bones, not $bones" }
        require(guide == null || guide.isNotEmpty()) { "A lock's own guide needs at least one point past its root" }
        require(length == null || length in SHORTEST_LOCK..LONGEST_LOCK) { "A lock's length is $SHORTEST_LOCK to $LONGEST_LOCK, not $length" }
        require(width == null || width in NARROWEST_LOCK..WIDEST_LOCK) { "A lock's width is $NARROWEST_LOCK to $WIDEST_LOCK, not $width" }
        require(thickness == null || thickness in NARROWEST_LOCK..WIDEST_LOCK) { "A lock's thickness is $NARROWEST_LOCK to $WIDEST_LOCK, not $thickness" }
        require(curl == null || curl in -MOST_LOCK_CURL..MOST_LOCK_CURL) { "A lock curls at most $MOST_LOCK_CURL turns, not $curl" }
        require(fall == null || fall in 0f..1f) { "A lock's fall is 0 to 1, not $fall" }
        require(lie == null || lie in 0.05f..0.95f) { "A lock lies 0.05 to 0.95 of its length along the head, not $lie" }
        require((around ?: 0f) in -MOST_ROOT_MOVE..MOST_ROOT_MOVE && (up ?: 0f) in -MOST_ROOT_MOVE..MOST_ROOT_MOVE) { "A lock's root moves at most $MOST_ROOT_MOVE degrees either way" }
    }
}

/** The most degrees a lock's root moves either way round or up the head from where its group lays it. */
const val MOST_ROOT_MOVE = 45f

/**
 * A group of hair locks on a head ([on]), rooted on it or on the inner mass [mass] over it where that part is there: [count] locks of the
 * [kind]'s place, spread over [spread] of its default reach, turned [around] degrees round the head and raised [height] up it. Each lock
 * is [length] long, varying by [lengthJitter] either way by [seed], [width] across and [thickness] through at its root, tapering to
 * [taper], with the [section] and [tip] given, [tipPoints] points on a forked tip. It lies along the head from its root and lifts its
 * outer part [rise] degrees off it, sweeps [sweep] sideways, falls toward the ground by [fall], curls [curl] turns, waves [wave] of its
 * width and twists [twist] degrees. A higher [layer] lies over a lower one. With [tie] the roots gather toward one point, a tail's tie, and [band] wraps it in a band. [overrides] set single
 * locks by index. [physics] says how its locks move once a solver drives them. Its locks grow the way the head's [ScalpFlow] says, as
 * far as [flow], and their kind's own way the rest. Each lies [lie] of its length along the head before it lifts, or turns [stand] of the
 * way toward straight up from its root; a [trim] cuts its tip
 * to a plane, and a tied lock [coil]s round its tie into a bun.
 */
@Serializable
@Doc("A group of hair locks on a head: a fringe, a side, the back, the crown, strays or the top, each lock a separate swept part.")
data class LockGroup(
    val id: LockGroupId,
    val kind: LockGroupKind,
    @Doc("The head it grows on, by part id.")
    val on: PartId,
    @Doc("The inner mass its roots sit on where it is there, a hair-volume part on the head, by id.")
    val mass: PartId? = null,
    @Doc("How many locks.") @Range(min = 1.0, max = 48.0)
    val count: Int = 7,
    @Doc("How far round the head its roots reach, a share of its kind's own reach (a fringe's about 96 degrees across the front, a side's about 66, the back's about 116): 1 the kind's own, 0 all at its middle.") @Range(min = 0.0, max = 2.0)
    val spread: Float = 1f,
    @Doc("Degrees its roots turn round the head, positive toward the character's right.") @Range(min = -180.0, max = 180.0)
    val around: Float = 0f,
    @Doc("How far up the head its roots move, -1 to 1 of about 20 degrees (15 for a crown, strays and the top, whose height moves it forward).") @Range(min = -1.0, max = 1.0)
    val height: Float = 0f,
    @Doc("Each lock's length, in body units.") @Range(min = 0.005, max = 3.0)
    val length: Float = 0.12f,
    @Doc("How far lengths vary either way, a share of the length.") @Range(min = 0.0, max = 1.0)
    val lengthJitter: Float = 0.2f,
    @Doc("Each lock's width at its root, in body units.") @Range(min = 0.002, max = 0.5)
    val width: Float = 0.05f,
    @Doc("Each lock's thickness at its root, in body units.") @Range(min = 0.002, max = 0.5)
    val thickness: Float = 0.02f,
    @Doc("What share of its root width a lock keeps toward its tip.") @Range(min = 0.0, max = 1.0)
    val taper: Float = 0.35f,
    val section: LockSection = LockSection.CLUMP,
    val tip: LockTip = LockTip.POINTED,
    @Doc("How many points a forked tip ends in.") @Range(min = 2.0, max = 3.0)
    val tipPoints: Int = 2,
    @Doc("Degrees each lock's outer part lifts off the head, where it lies along the head from its root: 0 lies flat all along, 90 stands its end straight out. A tied lock leaves its tie at this angle.") @Range(min = -30.0, max = 90.0)
    val rise: Float = 10f,
    @Doc("How far the locks sweep sideways as they grow, over the way the scalp's flow grows them, -1 to 1: 1 turns a lock 70 degrees round over its length toward the character's right.") @Range(min = -1.0, max = 1.0)
    val sweep: Float = 0f,
    @Doc("How far the locks fall toward the ground along their length, 0 none to 1 hanging.") @Range(min = 0.0, max = 1.0)
    val fall: Float = 0.5f,
    @Doc("How many turns each lock curls toward its underside; negative the other way.") @Range(min = -3.0, max = 3.0)
    val curl: Float = 0f,
    @Doc("How far each lock waves sideways, a share of its width.") @Range(min = 0.0, max = 2.0)
    val wave: Float = 0f,
    @Doc("How far each lock turns about itself over its length, in degrees.") @Range(min = -360.0, max = 360.0)
    val twist: Float = 0f,
    @Doc("Which picks the jitter: another seed, another layout with the same look.")
    val seed: Int = 1,
    @Doc("Its layer: locks of a higher layer lie over those of a lower one.") @Range(min = 0.0, max = 8.0)
    val layer: Int = 0,
    @Doc("How far its roots gather toward one point, 0 spread over the head to 1 all at one tie.") @Range(min = 0.0, max = 1.0)
    val tie: Float = 0f,
    @Doc("The palette slot of a band round the tie; no band when left out.")
    val band: SlotName? = null,
    @Doc("Its palette slot; the inner mass's base colour, else the head's, when left out.")
    val color: SlotName? = null,
    @Doc("How its locks move once physics drives them.")
    val physics: LockPhysics = LockPhysics(),
    @Doc("How far its locks grow the way the scalp's flow says, 0 its kind's own way (down the head; a crown's swirling out from its middle) to 1 the flow's.") @Range(min = 0.0, max = 1.0)
    val flow: Float = 1f,
    @Doc("How much of each lock's length lies along the head before it lifts off by its rise, 0.05 to 0.95: low stands it out from near its root, a brush or a flat top.") @Range(min = 0.05, max = 0.95)
    val lie: Float = 0.55f,
    @Doc("How far each lock turns from the way it grows toward straight up from its root, 0 following the head to 1 standing straight up: a brush, a flat top.") @Range(min = 0.0, max = 1.0)
    val stand: Float = 0f,
    @Doc("A plane its lock tips are cut to; uncut when left out.")
    val trim: LockTrim? = null,
    @Doc("With tie, how many turns each lock coils round the tie into a bun: about 1.5 a bun; 0 hangs free.") @Range(min = 0.0, max = 3.0)
    val coil: Float = 0f,
    @Doc("Single locks set by hand, by index.")
    val overrides: List<LockOverride> = emptyList(),
) {
    init {
        require(count in LEAST_LOCKS..MOST_LOCKS) { "A lock group holds $LEAST_LOCKS to $MOST_LOCKS locks, not $count" }
        require(spread in 0f..2f) { "A lock group's spread is 0 to 2, not $spread" }
        require(height in -1f..1f) { "A lock group's height is -1 to 1, not $height" }
        require(length in SHORTEST_LOCK..LONGEST_LOCK) { "A lock's length is $SHORTEST_LOCK to $LONGEST_LOCK, not $length" }
        require(width in NARROWEST_LOCK..WIDEST_LOCK && thickness in NARROWEST_LOCK..WIDEST_LOCK) { "A lock's width and thickness are $NARROWEST_LOCK to $WIDEST_LOCK" }
        require(lengthJitter in 0f..1f && taper in 0f..1f && fall in 0f..1f && tie in 0f..1f && flow in 0f..1f) { "A lock group's length jitter, taper, fall, tie and flow are 0 to 1" }
        require(tipPoints in 2..3) { "A forked tip has 2 or 3 points, not $tipPoints" }
        require(curl in -MOST_LOCK_CURL..MOST_LOCK_CURL) { "A lock curls at most $MOST_LOCK_CURL turns, not $curl" }
        require(layer >= 0) { "A lock group's layer is 0 or more, not $layer" }
        require(lie in 0.05f..0.95f) { "A lock lies 0.05 to 0.95 of its length along the head, not $lie" }
        require(coil in 0f..MOST_LOCK_COIL) { "A lock coils 0 to $MOST_LOCK_COIL turns round its tie, not $coil" }
        require(stand in 0f..1f) { "A lock group's stand is 0 to 1, not $stand" }
        require(overrides.map { it.index }.toSet().size == overrides.size) { "Lock group ${id.value} overrides a lock twice" }
    }

    /** The override of the lock at [index], or null when it follows the group. */
    fun overrideOf(index: Int): LockOverride? = overrides.firstOrNull { it.index == index }

    /** This group with [override] in place of what it held for that lock; null [override] at [index] returns that lock to the group. */
    fun withOverride(index: Int, override: LockOverride?): LockGroup {
        require(override == null || override.index == index) { "Override for lock ${override?.index} given for $index" }
        val kept = overrides.filterNot { it.index == index }
        return copy(overrides = if (override == null) kept else (kept + override).sortedBy { it.index })
    }
}

private const val LOCK_PART_PREFIX = "locks/"

/** The part id the lock group [id] is built, rigged, coloured and picked as. */
fun lockGroupPartId(id: LockGroupId): PartId = PartId(LOCK_PART_PREFIX + id.value)

/** The lock group [part] is built as, or null when it names a part of the document. */
fun lockGroupOf(part: PartId): LockGroupId? = part.value.takeIf { it.startsWith(LOCK_PART_PREFIX) }?.let { LockGroupId(it.removePrefix(LOCK_PART_PREFIX)) }

/** The lock group the character holds that shows as [part]; null for any other part. */
fun Character.lockGroupShownAs(part: PartId): LockGroup? = lockGroupOf(part)?.let { id -> lockGroups.firstOrNull { it.id == id } }

/** The lock group [id] of this character, or null. */
fun Character.lockGroup(id: LockGroupId): LockGroup? = lockGroups.firstOrNull { it.id == id }
