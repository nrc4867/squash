package dev.chieppa.squash.character.catalog

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.document.AddPart
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.Palette
import dev.chieppa.squash.document.PaletteSlot
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.SurfaceWork
import dev.chieppa.squash.document.Ulid

/** What a part placed on this plan starts holding: the plan's base slot, or nothing when it names none. */
fun BodyPlan.startingData(): PartData = PartData(surface = SurfaceWork(baseSlot = base?.let(::SlotName)))

/** A character to open: its empty document, the commands that place its root part, and that part. */
data class NewCharacter(val document: SquashDocument, val commands: List<DocumentCommand>, val root: PartId)

/** Builds a character from [plan]: no parts in the document, and an [AddPart] for the plan's root. */
fun newCharacter(plan: BodyPlan, templates: TemplateFolder, name: String, id: () -> String = Ulid::next): NewCharacter {
    val template = requireNotNull(templates.template(plan.root.template)) { "No template ${plan.root.template.value}" }
    val root = PartInstance(PartId(id()), template.ref, own = plan.startingData())
    val palette = Palette(plan.palette.map { PaletteSlot(SlotName(it.name), it.color) })
    val character = Character(CharacterId(id()), name, plan.ref, palette = palette)
    return NewCharacter(SquashDocument(character), listOf(AddPart(root)), root.id)
}
