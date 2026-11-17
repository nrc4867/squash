package dev.chieppa.squash.screens.fennec

import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.catalog.plan
import dev.chieppa.squash.character.catalog.swapTemplateCommand
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.screens.shell.shipped

/** How long the arms are against the legs they stand in for. */
const val LIMBS_ARM_SIZE = 0.55f

/** Degrees the arms lean toward their own up, which carries them forward off the chest. */
const val LIMBS_ARM_LEAN = 60f

/** The swap a person makes in Library: [part] and its pair's other side changing to [type], the parts on them kept. */
private fun SquashDocument.swapped(part: PartId, type: String): SquashDocument {
    val instance = checkNotNull(character.part(part))
    val own = checkNotNull(shipped.template(instance.template))
    val to = checkNotNull(shipped.template(TemplateId(type)))
    val command = checkNotNull(character.swapTemplateCommand(instance, own, to, shipped.plan(character.bodyPlan))) { "No seat for $type" }
    return command.apply(this).document
}

/**
 * The fennec with bird legs behind and short arms held forward in front, each pair swapped as Library swaps a template, the arms then
 * shortened and leaned forward by their size and the Move tool's lean.
 */
fun fennecLimbsDocument(): SquashDocument {
    val swapped = fennecDocument().swapped(BACK_LEG_L, "leg-bird").swapped(FRONT_LEG_L, "arm")
    return listOf(SetParameter(FRONT_LEG_L, "size", LIMBS_ARM_SIZE), SetParameter(FRONT_LEG_L, PlacementParams.LEAN, LIMBS_ARM_LEAN))
        .fold(swapped) { document, command -> command.apply(document).document }
}
