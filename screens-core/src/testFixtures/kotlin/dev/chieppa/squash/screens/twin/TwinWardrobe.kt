package dev.chieppa.squash.screens.twin

import dev.chieppa.squash.character.build.GenericParams
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.FIRST_LAYER
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetPaletteSlot
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SurfaceWork
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.woman.garment
import dev.chieppa.squash.screens.woman.wardrobeSlots
import dev.chieppa.squash.document.AddGarmentSlot
import dev.chieppa.squash.document.GarmentSlot

/** The woman's slots and one for a shoe's straps, worn over the sole that the feet's slot holds. */
val STRAPS_SLOT = GarmentSlotId("straps")

/** The commands that add every slot. */
fun twinSlots(): List<DocumentCommand> = wardrobeSlots() + AddGarmentSlot(GarmentSlot(STRAPS_SLOT, "Straps"))

/** Layers counted out from the skin: under the waistband, the waistband, over both, and a belt over everything at the waist. */
const val UNDER_LAYER = 1
const val WAIST_LAYER = 2
const val OVER_LAYER = 3
const val BELT_LAYER = 4

/** The twin's garment: the woman's helper, its joins sized by the twin's own limb lengths. */
internal fun twinGarment(
    id: GarmentId,
    name: String,
    slot: GarmentSlotId,
    color: SlotName,
    branches: List<GarmentBranch>,
    layer: Int = FIRST_LAYER,
    changes: Map<PartId, BodyPartChange> = emptyMap(),
): List<DocumentCommand> = garment(id, name, slot, color, branches, layer, changes, LIMB_LENGTHS)

/** The commands that put the reference colour [hex] in the palette as [slot]. */
internal fun cloth(slot: SlotName, hex: Int): DocumentCommand = SetPaletteSlot(slot, color(hex))

/** Up an arm hanging at rest, as a share of its drawn length, of a place [above] metres above the wrist along it. */
internal fun armU(above: Float) = (0.497f - above) / ARM_LENGTH

/** Down a leg from where it leaves the pelvis, as a share of its drawn length, of a place [height] metres above the ground at rest. */
internal fun legU(height: Float) = (LEG_TOP_HEIGHT - height) / LEG_LENGTH
private const val LEG_TOP_HEIGHT = 0.746f

/**
 * How far a trouser leg runs on up into the pelvis before the leg's start, as a share of the leg, short of hanging free: the leg leaves
 * the pelvis's underside, so a leg branch from the leg's own start leaves the hip strip bare under the waist.
 */
internal const val INTO_PELVIS = -0.08f

private const val SHOE_RINGS = 48

/** The foot swapped for a closed blob drawn as the shoe, at the foot's own size, in [color], for as long as the shoe is worn. */
internal fun shod(side: OutlineCurve, top: OutlineCurve, color: SlotName) = BodyPartChange.Swap(
    TemplateRef(TemplateId("blob"), 1),
    PartData(outlines = mapOf(View.SIDE to side, View.TOP to top), params = mapOf("size" to FOOT_SIZE, GenericParams.RINGS to SHOE_RINGS.toFloat()), surface = SurfaceWork(baseSlot = color)),
)

/** A part that comes out through a garment's hole with no cuff round it: an armhole or a neckline. */
internal val OPEN = BodyPartChange.Through(cuff = false)
