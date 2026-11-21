package dev.chieppa.squash.rig

import dev.chieppa.squash.character.model.SurfaceMethod
import dev.chieppa.squash.document.PartBind
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Role

/** How a part a garment owns is bound, as the document holds it: [bind] null for the rule, [stiff] 0 to 1. */
data class OwnedBind(val bind: PartBind? = null, val stiff: Float = 0f) {
    companion object {
        val RULE = OwnedBind()
    }
}

/**
 * How a part bound to the body holds on to it: the body [parts] it is read on, the [reach] over which their vertices are eased into one
 * surface (see [BodySurface]), and how far each of its vertices stood off that surface at rest, [standoff], in body units, 0 or less for
 * one inside the body. A pose keeps each vertex at least [KEPT_STANDOFF] of its standoff outside the body as posed (see [Rig.posed]).
 */
class BodyHold(val parts: List<PartId>, val reach: Float, val standoff: FloatArray)

/** The share of its rest standoff a bound vertex keeps outside the body in any pose. */
const val KEPT_STANDOFF = 0.75f

/**
 * [rigged] with every part a garment owns bound as [binds] says (see HLD: data-model, A garment can own parts): a part bound to
 * [PartBind.FOLLOW] takes each vertex's bones from the body under it ([BodyWeights]), with its stiffness sharpening them toward the
 * heaviest ([stiffened]); a part bound to [PartBind.SINGLE] keeps the one bone it sits on. A part the document leaves to the rule follows
 * when it goes round the limb under it ([wraps]) and its length crosses a joint there ([spans]); every other rides its one bone. The body
 * is read on the part it sits on, through any owned part between, that part's parent and the parts sitting on it, none of them owned,
 * hair or hard. [checkCancelled] runs between parts.
 */
internal fun boundOwned(parts: List<RiggedPart>, rigged: List<RigPart>, bones: List<Bone>, binds: (PartId) -> OwnedBind, checkCancelled: () -> Unit): List<RigPart> {
    val byId = parts.associateBy { it.id }
    val rigById = rigged.associateBy { it.id }
    return rigged.map { rig ->
        val part = byId[rig.id]
        if (part?.placed?.owner == null) return@map rig
        val bind = binds(rig.id)
        if (bind.bind == PartBind.SINGLE) return@map rig
        checkCancelled()
        val body = bodyUnder(part, byId).mapNotNull(rigById::get)
        if (body.isEmpty()) return@map rig
        if (bind.bind == null && !wraps(rig, body, bones)) return@map rig
        val follow = BodyWeights(body).follow(rig)
        if (bind.bind == null && !spans(follow.weights)) return@map rig
        rig.boundBy(stiffened(follow.weights, bind.stiff), follow.hold)
    }
}

/** The body parts whose weights [part] reads: the body part it sits on, past any owned part between, its parent and its children. */
private fun bodyUnder(part: RiggedPart, byId: Map<PartId, RiggedPart>): List<PartId> {
    var on = part.placed.socket?.parent?.let(byId::get)
    while (on != null && on.placed.owner != null) on = on.placed.socket?.parent?.let(byId::get)
    val base = on ?: return emptyList()
    val parent = base.placed.socket?.parent?.let(byId::get)?.takeIf { it.isBody() }
    val children = byId.values.filter { it.placed.socket?.parent == base.id && it.isBody() && it.placed.template.surface != SurfaceMethod.SEPARATE }
    return listOfNotNull(base, parent).map { it.id } + children.map { it.id }
}

private fun RiggedPart.isBody(): Boolean = placed.owner == null && placed.role != Role.HAIR

private fun RigPart.boundBy(weights: PartWeights, hold: BodyHold): RigPart =
    RigPart(id, rest, indices, weights, mesh, origin, vertices, anchors, role, axis, facing, hold)
