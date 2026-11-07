package dev.chieppa.squash.screens

import dev.chieppa.squash.character.build.HandJoints
import dev.chieppa.squash.character.build.HandLandmarks
import dev.chieppa.squash.character.build.asBuilt
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.lint.TemplateLint
import dev.chieppa.squash.character.model.Hinge
import dev.chieppa.squash.character.model.JointSpec
import dev.chieppa.squash.character.model.Limits
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.screens.move.JointRef
import dev.chieppa.squash.screens.move.MoveEditState
import kotlin.math.abs

/** The joints [part] builds with, its template's and those placed by hand, in u order. */
fun AppState.jointsOf(part: PartInstance): List<JointSpec> =
    session.value?.builtTemplate(part)?.blocks?.flatMap { it.joints() }?.sortedBy { it.u }.orEmpty()

/** Where a joint placed at [u] on a part with [joints] sits: within the part's ends, or null too near a joint it has. */
fun jointPlace(u: Float, joints: List<JointSpec>): Float? {
    val at = u.coerceIn(HandJoints.GAP, 1f - HandJoints.GAP)
    return at.takeIf { joints.none { joint -> abs(joint.u - at) < HandJoints.GAP } }
}

/**
 * Places a hand-made joint on [part] at [u], snapped onto its axis between its ends and away from its other joints, with the Move
 * tool's hinge, limits and stretch, as one undo step, and picks it. A joint the part's rings cannot take, as the template lint reads
 * its build, is not placed. See HLD: data-model, Joints and landmarks by hand.
 */
fun AppState.addJoint(part: PartId, u: Float) {
    val opened = session.value ?: return
    val character = opened.document.value.character
    val instance = character.part(part) ?: return
    val at = jointPlace(u, jointsOf(instance)) ?: return
    val params = character.resolvedShape(instance).params
    val id = HandJoints.nextId(params)
    val command = HandJoints.add(part, id, at, move.hinge.value, move.limits.value, move.stretch.value, thisSide && instance.pair != null)
    val unplaced = templates.template(instance.template) ?: return
    val placed = params + command.values.mapNotNull { (name, value) -> value?.let { name to it } }
    val onSocket = instance.socket != null
    if (TemplateLint.lint(unplaced.asBuilt(placed, onSocket)).size > TemplateLint.lint(unplaced.asBuilt(params, onSocket)).size) return
    opened.send(command)
    selectedPart.value = part
    move.pickedJoint.value = JointRef(part, HandJoints.name(id))
}

/** Places a hand-made landmark on [part] at [u] and [v], as one undo step. */
fun AppState.addLandmark(part: PartId, u: Float, v: Float) {
    val opened = session.value ?: return
    val character = opened.document.value.character
    val instance = character.part(part) ?: return
    val id = HandLandmarks.nextId(character.resolvedShape(instance).params)
    opened.send(HandLandmarks.add(part, id, u, v, thisSide && instance.pair != null))
    selectedPart.value = part
}

/** Picks joint [joint] of [part], whose hinge, limits and stretch the Move tool's fields then show. */
fun AppState.pickJoint(part: PartId, joint: String) {
    move.pickedJoint.value = JointRef(part, joint)
}

/** The picked joint as its part builds it, if it is still there. */
fun AppState.pickedJointSpec(): JointSpec? {
    val picked = move.pickedJoint.value ?: return null
    val instance = session.value?.document?.value?.character?.part(picked.part) ?: return null
    return jointsOf(instance).firstOrNull { it.name == picked.joint }
}

/** Whether the picked joint was placed by hand, which Delete joint and the fields change. */
fun AppState.pickedJointIsHandMade(): Boolean = move.pickedJoint.value?.let { HandJoints.idOf(it.joint) } != null && pickedJointSpec() != null

/** Clears hand-made joint [id] from [part], one undo step. */
fun AppState.deleteJoint(part: PartId, id: Int) {
    val opened = session.value ?: return
    val instance = opened.document.value.character.part(part) ?: return
    opened.send(HandJoints.delete(part, id, thisSide && instance.pair != null))
    if (move.pickedJoint.value == JointRef(part, HandJoints.name(id))) move.pickedJoint.value = null
}

/** Clears the picked joint when it was placed by hand. */
fun AppState.deletePickedJoint() {
    val picked = move.pickedJoint.value ?: return
    deleteJoint(picked.part, HandJoints.idOf(picked.joint) ?: return)
}

/** Clears hand-made landmark [id] from [part], one undo step. */
fun AppState.deleteLandmark(part: PartId, id: Int) {
    val opened = session.value ?: return
    val instance = opened.document.value.character.part(part) ?: return
    opened.send(HandLandmarks.delete(part, id, thisSide && instance.pair != null))
}

/** Sets the hinge, limits and stretch of the picked hand-made joint, one undo step, or those the next joint takes while none is picked. */
fun AppState.setJoint(hinge: Hinge, limits: Limits, stretch: Boolean) {
    val picked = move.pickedJoint.value
    val id = picked?.let { HandJoints.idOf(it.joint) }
    val instance = picked?.let { session.value?.document?.value?.character?.part(it.part) }
    if (picked == null || id == null || instance == null || pickedJointSpec() == null) {
        move.hinge.value = hinge
        move.limits.value = limits
        move.stretch.value = stretch
        return
    }
    session.value?.send(HandJoints.edit(picked.part, id, hinge, limits, stretch, thisSide && instance.pair != null))
}

/** A joint's hinge, [limits] and [stretch], as the Move tool's joint fields show them. */
data class JointFields(val hinge: Hinge, val limits: Limits, val stretch: Boolean)

/** What the joint fields show: the picked joint's, or the next joint's while none is picked. */
fun AppState.shownJoint(): JointFields {
    val spec = pickedJointSpec() ?: return JointFields(move.hinge.value, move.limits.value, move.stretch.value)
    return JointFields(spec.hinge, spec.limits ?: HandJoints.DEFAULT_LIMITS, spec.stretch)
}

/** Whether [document] still holds the picked joint [joint], which undo can take away. */
fun hasJoint(document: SquashDocument, joint: JointRef): Boolean {
    val part = document.character.part(joint.part) ?: return false
    val id = HandJoints.idOf(joint.joint) ?: return true
    return id in HandJoints.ids(document.character.resolvedShape(part).params)
}

/** Drops the picked socket and joint when [document] no longer holds them, as after an undo. */
fun MoveEditState.follow(document: SquashDocument) {
    if (picked.value?.let { hasHandSocket(document, it) } != true) picked.value = null
    if (pickedJoint.value?.let { hasJoint(document, it) } != true) pickedJoint.value = null
}
