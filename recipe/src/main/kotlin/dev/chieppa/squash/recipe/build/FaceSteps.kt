package dev.chieppa.squash.recipe.build

import dev.chieppa.squash.character.build.FaceMap
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.ExpressionId
import dev.chieppa.squash.document.ExpressionSet
import dev.chieppa.squash.document.EyeLook
import dev.chieppa.squash.document.FacePose
import dev.chieppa.squash.document.PaintedEye
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetExpression
import dev.chieppa.squash.document.SetPaintedEye
import dev.chieppa.squash.document.SlotName

/** The anchor at [place] on [part]'s face seen from straight ahead, x to the character's left and y up. */
internal fun BuildContext.faceAnchor(path: String, part: PartId, place: Pair<Float, Float>): Anchor {
    val map = faceMaps.getOrPut(part) { FaceMap.of(part, templateOf(part), document.character.resolvedShape(checkNotNull(document.character.part(part)))) }
        ?: fail(path, "${part.value} has no face to place on: its template builds no body patch")
    return map.at(place.first, place.second) ?: fail(path, "(${place.first}, ${place.second}) lies off ${part.value}'s face as drawn; tools/recipe.sh probe <recipe> part=${part.value} prints its face's reach")
}

/** The painted eyes, each on its head. */
internal fun BuildContext.eyes() = recipe.eyes.forEachIndexed { i, eye ->
    val path = "$.eyes[$i]"
    val head = partOf("$path.on", eye.on)
    val anchor = when {
        eye.face != null && (eye.u != null || eye.v != null) -> fail(path, "place an eye by face or by u and v, not both")
        eye.face != null -> faceAnchor("$path.face", head, pair(path, "face", eye.face, "[x, y]"))
        eye.u != null && eye.v != null -> Anchor(head, eye.patch, eye.u, eye.v)
        else -> fail(path, "place an eye by face [x, y] or by both u and v")
    }
    checkSlots("$path.look", eye.look)
    eye.front?.let { checkSlots("$path.front", it) }
    send(path, SetPaintedEye(eye.id, guard(path) { PaintedEye(eye.id, anchor, eye.look, eye.mirrored, eye.front, eye.fromFront, eye.gazeForward, eye.gazeUp) }))
}

private fun BuildContext.checkSlots(path: String, look: EyeLook) {
    listOf("white" to look.white, "iris" to look.iris, "pupil" to look.pupil, "highlight" to look.highlight, "ink" to look.ink)
        .forEach { (field, slot: SlotName?) -> slot?.let { slot("$path.$field", it.value) } }
}

/** The named expressions. */
internal fun BuildContext.expressions() = recipe.expressions.forEachIndexed { i, expression ->
    val path = "$.expressions[$i]"
    val joints = expression.joints.mapIndexed { j, joint -> joint.copy(part = partOf("$path.joints[$j].part", joint.part.value)) }
    val id = ExpressionId(expression.id)
    send(path, SetExpression(id, ExpressionSet(id, expression.name, pose = guard("$path.joints") { FacePose(joints) })))
}
