package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Mat3
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.posed

/** The character's parts in a pose, as the rig gives them. See HLD: data-model, The rig. */
interface PosedParts {
    /** Each rigged part's mesh in the pose, in the part's own space at its origin; a part the pose leaves at rest holds its rest mesh itself. */
    val meshes: Map<PartId, RenderMesh>

    /** How the pose turns each rigged part, by the bone at its root. A part at rest may be left out. */
    val turns: Map<PartId, Mat3>
}

/**
 * This character in [pose]: each part the pose moves showing its posed mesh, its patches' points and normals read from it, and turned
 * as its root bone turns. A part the pose leaves at rest stays the same object, and with none moved this is the character itself.
 */
fun AssembledCharacter.posedBy(pose: PosedParts): AssembledCharacter {
    var moved = false
    val shown = parts.map { part ->
        val mesh = part.id?.let(pose.meshes::get)
        if (mesh == null || mesh === part.surface.mesh) {
            part
        } else {
            moved = true
            part.showing(part.surface.posed(mesh), pose.turns[part.id] ?: Mat3.IDENTITY)
        }
    }
    return if (moved) AssembledCharacter(shown, sockets) else this
}

/** The parts of this character that show something other than they do in [rest]: those a pose moves. */
fun AssembledCharacter.movedFrom(rest: AssembledCharacter): Set<PartId> =
    parts.mapNotNullTo(hashSetOf()) { part -> part.id?.takeIf { rest.part(it) !== part } }
