package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.PairId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.TemplateRef

/** What placing a part reads of it from the document. */
data class AssemblyPart(
    val id: PartId,
    val template: TemplateRef,
    val socket: SocketRef?,
    val pair: PairId?,
    val side: Side?,
    val placement: Map<String, Float> = emptyMap(),
    val role: Role? = null,
    /** Where the garments worn put its children's sockets on it as it shows, by the same keys as [placement], when they move any. */
    val wornSockets: Map<String, Float> = emptyMap(),
    /** The garment that owns it, or null for a part of the body. */
    val owner: GarmentId? = null,
    /** The blend of the join at its socket, see [dev.chieppa.squash.document.PartInstance.blend]. */
    val blend: Float? = null,
    /** The palette slots its copies pick among besides its own base, see [dev.chieppa.squash.document.SurfaceWork.shades]. */
    val shades: List<SlotName> = emptyList(),
)
