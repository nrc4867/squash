package dev.chieppa.squash.screens.woman

import dev.chieppa.squash.document.AddGarment
import dev.chieppa.squash.document.AddGarmentSlot
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.CoverPart
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.DEFAULT_GARMENT_FILLET
import dev.chieppa.squash.document.FIRST_LAYER
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.GarmentSlot
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetBodyPartChange
import dev.chieppa.squash.document.SetGarmentColor
import dev.chieppa.squash.document.SetPaletteSlot
import dev.chieppa.squash.document.SlotColor
import dev.chieppa.squash.document.SlotName

/** Her wardrobe's slots, named by where on her a garment goes, in the order the outfits band lists them. */
val TOP_SLOT = GarmentSlotId("top")
val OUTER_SLOT = GarmentSlotId("outer")
val BOTTOM_SLOT = GarmentSlotId("bottom")
val FEET_SLOT = GarmentSlotId("feet")
val HEAD_SLOT = GarmentSlotId("head")
val EYES_SLOT = GarmentSlotId("eyes")
val NECK_SLOT = GarmentSlotId("neck")
val HANDS_SLOT = GarmentSlotId("hands")
val FACE_SLOT = GarmentSlotId("face")

private val SLOTS = listOf(
    TOP_SLOT to "Top", OUTER_SLOT to "Outer", BOTTOM_SLOT to "Bottom", FEET_SLOT to "Feet", HEAD_SLOT to "Head", EYES_SLOT to "Eyes",
    NECK_SLOT to "Neck", HANDS_SLOT to "Hands", FACE_SLOT to "Face",
)

/** The commands that add every slot. */
fun wardrobeSlots(): List<DocumentCommand> = SLOTS.map { (id, name) -> AddGarmentSlot(GarmentSlot(id, name)) }

/** The commands that put [color] in the palette as [slot]. */
fun cloth(slot: SlotName, red: Float, green: Float, blue: Float): DocumentCommand = SetPaletteSlot(slot, SlotColor(red, green, blue))

/** How wide the fillet where a garment's branches meet is drawn, in body units, whatever the length of the branch that comes out. */
private const val JOIN_WIDTH = 0.045f

/**
 * The commands that add a garment in [slot] at [layer], colored [color], covering each of [branches] and changing [changes]. A garment's
 * fillet is a share of the length of the child branch it joins; it is set so the fillet is [JOIN_WIDTH] wide on the shortest of them.
 */
fun garment(
    id: GarmentId,
    name: String,
    slot: GarmentSlotId,
    color: SlotName,
    branches: List<GarmentBranch>,
    layer: Int = FIRST_LAYER,
    changes: Map<PartId, BodyPartChange> = emptyMap(),
): List<DocumentCommand> =
    listOf(AddGarment(Garment(id, name, slot, layer = layer, fillet = filletFor(branches))), SetGarmentColor(id, color)) +
        branches.map { CoverPart(id, it) } +
        changes.map { (part, change) -> SetBodyPartChange(id, part, change) }

/** The fillet share that makes the join [JOIN_WIDTH] wide where the shortest branch on a limb comes out, the default where none does. */
private fun filletFor(branches: List<GarmentBranch>): Float {
    val limbs = branches.filter { it.part in LIMB_LENGTHS }
    val shortest = limbs.minOfOrNull { (it.to - it.from) * LIMB_LENGTHS.getValue(it.part) } ?: return DEFAULT_GARMENT_FILLET
    return (JOIN_WIDTH / shortest).coerceAtMost(DEFAULT_GARMENT_FILLET)
}

/** A part that comes out through a garment's hole with no cuff round it: an armhole, a neckline or a leg out of a skirt. */
val OPEN = BodyPartChange.Through(cuff = false)
