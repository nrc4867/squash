package dev.chieppa.squash.screens.woman

import dev.chieppa.squash.character.build.GenericParams
import dev.chieppa.squash.document.AddGarment
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.AddGarmentSlot
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.CoverPart
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.FIRST_LAYER
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.GarmentSlot
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PairId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SetBodyPartChange
import dev.chieppa.squash.document.SetGarmentColor
import dev.chieppa.squash.document.SetOutlines
import dev.chieppa.squash.document.SetPaletteSlot
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SlotColor
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.fennec.ref

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
val WAIST_SLOT = GarmentSlotId("waist")

private val SLOTS = listOf(
    TOP_SLOT to "Top", OUTER_SLOT to "Outer", BOTTOM_SLOT to "Bottom", FEET_SLOT to "Feet", HEAD_SLOT to "Head", EYES_SLOT to "Eyes",
    NECK_SLOT to "Neck", HANDS_SLOT to "Hands", FACE_SLOT to "Face", WAIST_SLOT to "Belt",
)

/** The commands that add every slot. */
fun wardrobeSlots(): List<DocumentCommand> = SLOTS.map { (id, name) -> AddGarmentSlot(GarmentSlot(id, name)) }

/** The commands that put [color] in the palette as [slot]. */
fun cloth(slot: SlotName, red: Float, green: Float, blue: Float): DocumentCommand = SetPaletteSlot(slot, SlotColor(red, green, blue))

/** The commands that add a garment in [slot] at [layer], colored [color], covering each of [branches] and changing [changes]. */
fun garment(
    id: GarmentId,
    name: String,
    slot: GarmentSlotId,
    color: SlotName,
    branches: List<GarmentBranch>,
    layer: Int = FIRST_LAYER,
    changes: Map<PartId, BodyPartChange> = emptyMap(),
): List<DocumentCommand> =
    listOf(AddGarment(Garment(id, name, slot, layer = layer)), SetGarmentColor(id, color)) +
        branches.map { CoverPart(id, it) } +
        changes.map { (part, change) -> SetBodyPartChange(id, part, change) }

/** A part [garment] owns, of the generic [template], on [socket]: built only while the garment is worn, riding the bone it sits on. */
fun owned(garment: GarmentId, id: PartId, template: String, socket: SocketRef): DocumentCommand =
    AddParts(listOf(PartInstance(id, ref(template), socket, role = Role.OTHER, garment = garment)))

/** A pair [garment] owns, of the generic [template], each side on its own parent's [socket], or both on one paired socket of one parent. */
fun ownedPair(garment: GarmentId, pair: String, left: Pair<PartId, SocketRef>, right: Pair<PartId, SocketRef>, template: String): DocumentCommand = AddParts(
    listOf(
        PartInstance(left.first, ref(template), left.second, pair = PairId(pair), side = Side.LEFT, role = Role.OTHER, garment = garment),
        PartInstance(right.first, ref(template), right.second, pair = PairId(pair), side = Side.RIGHT, role = Role.OTHER, garment = garment),
    ),
)

/** The commands that draw a part a garment owns in Side and Top at [size], lofted with [rings] and [segments]. */
fun propShape(part: PartId, side: OutlineCurve, top: OutlineCurve, size: Float, rings: Int, segments: Int): List<DocumentCommand> = listOf(
    SetOutlines(part, mapOf(View.SIDE to side, View.TOP to top)),
    SetParameter(part, "size", size),
    GenericParams.rings(part, rings),
    GenericParams.segments(part, segments),
)

/** A part that comes out through a garment's hole with no cuff round it: an armhole, a neckline or a leg out of a skirt. */
val OPEN = BodyPartChange.Through(cuff = false)
