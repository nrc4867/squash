package dev.chieppa.squash.document

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How much of a jaw's teeth a mouth draws. Every level reads the same rows, so a jaw moves between levels without redoing its teeth. */
@Serializable
enum class TeethLevel {
    /** No teeth. */
    @SerialName("none") NONE,

    /** The row's outline filled as one band; slots flagged over still show on their own. */
    @SerialName("bar") BAR,

    /** The band split at each slot, one block per tooth. */
    @SerialName("blocks") BLOCKS,
}

/** How much tongue a mouth draws. */
@Serializable
enum class TongueLevel {
    /** No tongue. */
    @SerialName("none") NONE,

    /** A flat shape: the tongue role's marks, or a plain tongue where its role is filled and has none. */
    @SerialName("flat") FLAT,
}

/** The two jaws of a mouth. */
@Serializable
enum class JawName {
    /** Rides the skull: its teeth hang from the upper lip's line and stay as the mouth opens. */
    @SerialName("upper") UPPER,

    /** Rides the jaw bone: its teeth stand on the lower lip's line and drop with it. */
    @SerialName("lower") LOWER,
}

/** How a tooth's free end is cut. */
@Serializable
enum class ToothTip {
    /** Square across, a little rounded at its corners. */
    @SerialName("flat") FLAT,

    /** Rounded across. */
    @SerialName("round") ROUND,

    /** A point: a fang or a cone. */
    @SerialName("point") POINT,

    /** A slanted cutting edge, long on the side toward the middle. */
    @SerialName("chisel") CHISEL,
}

/** Which sides of the mouth a slot fills. */
@Serializable
enum class SlotSide {
    /** Both, mirrored about the middle. */
    @SerialName("both") BOTH,

    /** The character's left only. */
    @SerialName("left") LEFT,

    /** The character's right only. */
    @SerialName("right") RIGHT,
}

/** A kind of tooth: its size and shape, which each slot holding it may change. Sizes are shares of the mouth's width. */
@Serializable
@Doc("A kind of tooth; sizes are shares of the mouth's width.")
data class ToothType(
    @Doc("How wide the tooth is at its root.") @Range(min = 0.005, max = 0.5)
    val width: Float = DEFAULT_TOOTH_WIDTH,
    @Doc("How far it reaches from the lip's line, past it for the upper jaw and up from it for the lower.") @Range(min = 0.005, max = 1.0)
    val length: Float = DEFAULT_TOOTH_LENGTH,
    @Doc("How its free end is cut: flat, round, point or chisel.")
    val tip: ToothTip = ToothTip.FLAT,
    @Doc("How far its tip hooks toward the middle (above 0) or away from it (below 0), a share of its length.") @Range(min = -1.0, max = 1.0)
    val curve: Float = 0f,
)

/** One place in a row, holding a tooth type, with any of the type's settings changed for this slot. */
@Serializable
@Doc("One place in a row, from the middle outward: a tooth type, how far it sits from the slot before it, and any of the type's settings changed here.")
data class ToothSlot(
    @Doc("The tooth type by name, from the preset's `types`.")
    val type: String,
    @Doc("Space before this slot, after the one nearer the middle, or from the middle for the first; a share of the mouth's width.") @Range(min = 0.0, max = 1.0)
    val gap: Float = 0f,
    @Doc("Where the slot's middle sits along the row, its distance from the mouth's middle as a share of the mouth's width, in place of following the slot before it.") @Range(min = 0.0, max = 1.5)
    val at: Float? = null,
    @Doc("The type's width, changed for this slot.") @Range(min = 0.005, max = 0.5)
    val width: Float? = null,
    @Doc("The type's length, changed for this slot.") @Range(min = 0.005, max = 1.0)
    val length: Float? = null,
    @Doc("The type's curve, changed for this slot.") @Range(min = -1.0, max = 1.0)
    val curve: Float? = null,
    @Doc("The type's tip, changed for this slot.")
    val tip: ToothTip? = null,
    @Doc("Whether it shows over the lips, open or shut, breaking their ink: incisors, fangs, a crocodile's lower teeth.")
    val over: Boolean = false,
    @Doc("Which sides it fills: both, mirrored, unless left or right.")
    val side: SlotSide = SlotSide.BOTH,
)

/** A row of slots from the middle outward. A row after a jaw's first sits behind it. */
@Serializable
@Doc("A row of slots from the middle outward, mirrored to the other side unless a slot says otherwise.")
data class ToothRow(
    val slots: List<ToothSlot> = emptyList(),
)

/** One jaw: the level it draws at and its rows, the first in front. */
@Serializable
@Doc("One jaw: the level it draws at and its rows, the first in front.")
data class Jaw(
    @Doc("How much of this jaw shows: none, bar or blocks.")
    val level: TeethLevel = TeethLevel.NONE,
    val rows: List<ToothRow> = emptyList(),
)

/**
 * A change to one slot of a jaw's preset or added rows, by [row] and [slot] index: each value set replaces the slot's, and [removed]
 * takes the slot out. Only what differs from the preset is kept.
 */
@Serializable
@Doc("A change to one slot, by row and slot index: each value set replaces the slot's; removed takes it out.")
data class ToothSlotChange(
    @Range(min = 0.0, max = 16.0) val row: Int = 0,
    @Range(min = 0.0, max = 64.0) val slot: Int,
    val type: String? = null,
    @Range(min = 0.0, max = 1.0) val gap: Float? = null,
    @Range(min = 0.0, max = 1.5) val at: Float? = null,
    @Range(min = 0.005, max = 0.5) val width: Float? = null,
    @Range(min = 0.005, max = 1.0) val length: Float? = null,
    @Range(min = -1.0, max = 1.0) val curve: Float? = null,
    val tip: ToothTip? = null,
    val over: Boolean? = null,
    val side: SlotSide? = null,
    val removed: Boolean = false,
) {
    /** Whether this changes nothing. */
    val isEmpty: Boolean get() = this == ToothSlotChange(row = row, slot = slot)

    /** [slot] with this change's values over it. */
    fun on(slot: ToothSlot): ToothSlot = slot.copy(
        type = type ?: slot.type,
        gap = gap ?: slot.gap,
        at = at ?: slot.at,
        width = width ?: slot.width,
        length = length ?: slot.length,
        curve = curve ?: slot.curve,
        tip = tip ?: slot.tip,
        over = over ?: slot.over,
        side = side ?: slot.side,
    )
}

/** A slot as a mouth shows it: its [row] and [index], what it is with the mouth's change on it, and [base], what it is without. */
data class ShownSlot(val row: Int, val index: Int, val slot: ToothSlot, val base: ToothSlot)

/** A slot added at the end of a jaw's row [row], after the preset's own and any added before it. */
@Serializable
@Doc("A slot added at the end of a row.")
data class AddedToothSlot(
    @Range(min = 0.0, max = 16.0) val row: Int = 0,
    val slot: ToothSlot,
)

/** What a mouth changes of one jaw of its preset: its level, slots changed or removed, slots and rows added. */
@Serializable
@Doc("What a mouth changes of one jaw of its preset; each left out keeps the preset's.")
data class JawTeeth(
    @Doc("The jaw's level in place of the preset's.")
    val level: TeethLevel? = null,
    @Doc("Rows added behind the preset's.")
    val rows: List<ToothRow> = emptyList(),
    @Doc("Slots added at the end of a row.")
    val added: List<AddedToothSlot> = emptyList(),
    @Doc("Changes to single slots, by row and slot index, counting the preset's slots, then added ones; rows count the preset's, then added ones.")
    val slots: List<ToothSlotChange> = emptyList(),
) {
    val isEmpty: Boolean get() = this == JawTeeth()

    /** [preset] with these changes on it. */
    fun on(preset: Jaw): Jaw = Jaw(level = level ?: preset.level, rows = shown(preset).map { row -> ToothRow(row.map { it.slot }) })

    /** Each row of [preset] with these changes on it, a removed slot left out and every other kept at the index changes name it by. */
    fun shown(preset: Jaw): List<List<ShownSlot>> = (preset.rows + rows).mapIndexed { r, row ->
        val all = row.slots + added.filter { it.row == r }.map { it.slot }
        all.mapIndexedNotNull { s, slot ->
            val change = changeAt(r, s)
            when {
                change == null -> ShownSlot(r, s, slot, slot)
                change.removed -> null
                else -> ShownSlot(r, s, change.on(slot), slot)
            }
        }
    }

    /** The change kept for slot [slot] of row [row], if any. */
    fun changeAt(row: Int, slot: Int): ToothSlotChange? = slots.firstOrNull { it.row == row && it.slot == slot }

    /** These changes with [change] in place of whatever was kept for its slot; an empty change keeps nothing for it. */
    fun withChange(change: ToothSlotChange): JawTeeth {
        val others = slots.filterNot { it.row == change.row && it.slot == change.slot }
        return copy(slots = if (change.isEmpty) others else others + change)
    }

    /** These changes with slot [slot] of row [row] back to the preset's, or to how it was added. */
    fun reset(row: Int, slot: Int): JawTeeth = copy(slots = slots.filterNot { it.row == row && it.slot == slot })

    /** These changes with [slot] added at the end of row [row]. */
    fun add(row: Int, slot: ToothSlot): JawTeeth = copy(added = added + AddedToothSlot(row, slot))

    /** These changes with an empty row added behind the others. */
    fun addRow(): JawTeeth = copy(rows = rows + ToothRow())
}

/**
 * A mouth's teeth: the preset it starts from and what it changes of each jaw, and nothing else, so a later change to the preset still
 * reaches it. See HLD: look, Teeth.
 */
@Serializable
@Doc("A mouth's teeth: a preset by name and what this mouth changes of each jaw.")
data class MouthTeeth(
    @Doc("The teeth preset by name (templates/teeth/<name>.teeth.json); none takes the mouth style's.")
    val preset: String? = null,
    @Doc("What this mouth changes of the upper jaw.")
    val upper: JawTeeth = JawTeeth(),
    @Doc("What this mouth changes of the lower jaw.")
    val lower: JawTeeth = JawTeeth(),
) {
    fun jaw(name: JawName): JawTeeth = when (name) {
        JawName.UPPER -> upper
        JawName.LOWER -> lower
    }

    fun withJaw(name: JawName, jaw: JawTeeth): MouthTeeth = when (name) {
        JawName.UPPER -> copy(upper = jaw)
        JawName.LOWER -> copy(lower = jaw)
    }

    val isEmpty: Boolean get() = this == MouthTeeth()
}

/** How wide and long a tooth type is when it does not say, shares of the mouth's width. */
const val DEFAULT_TOOTH_WIDTH = 0.08f
const val DEFAULT_TOOTH_LENGTH = 0.08f
