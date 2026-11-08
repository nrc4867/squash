package dev.chieppa.squash.character.catalog

import dev.chieppa.squash.character.build.socketsOn
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateRef

/** The library's newest version of the template [ref] pins, when it is newer than [ref]'s: what Update from library moves a part to. */
fun TemplateFolder.newerThan(ref: TemplateRef): PartTemplate? = template(ref.id)?.takeIf { it.version > ref.version }

/**
 * The parts sitting on a socket their parent does not offer, as a template version without that socket leaves them. Each stays stored
 * on its socket, so a version or a socket by hand that offers it again seats it. See HLD: data-model, Update from library.
 */
fun unseatedParts(document: SquashDocument, plan: BodyPlan?, templates: TemplateFolder): Set<PartId> {
    val character = document.character
    return character.parts.filter { part ->
        val socket = part.socket ?: return@filter false
        val parent = character.part(socket.parent) ?: return@filter false
        val template = templates.template(parent.template) ?: return@filter false
        socketsOn(plan, template, character.resolvedShape(parent).params).none { it.name == socket.socket }
    }.mapTo(mutableSetOf()) { it.id }
}

/** The joints [pose] sets on [part] that [built], the template as the part builds it, does not have, in the pose's order. */
fun posedJointsMissing(pose: Pose, part: PartId, built: PartTemplate): List<String> {
    val joints = built.blocks.flatMap { it.joints() }.map { it.name }.toSet()
    return pose.bones.filter { it.part == part && it.joint !in joints }.map { it.joint }
}
