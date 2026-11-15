package dev.chieppa.squash.screens.look.drag

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.build.moveSocket
import dev.chieppa.squash.character.build.socketsOn
import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.screens.shell.Stage

/** How many pointer moves a scripted drag makes. */
const val DRAG_FRAMES = 24

/** How far along its parent a socket moves each frame of a Move drag. */
private const val SOCKET_STEP = 0.004f

/** How many degrees a bone turns each frame of a Pose drag. */
private const val BONE_STEP = 2f

/** How much further each play of a drag starts than the play before, a share of its step, so no play repeats another's moves. */
private const val DRIFT = 0.1f

/**
 * One scripted gesture made in [stage]: the commands each pointer move of play [run] sends through one open transaction, as the tools
 * send them, each play a little apart from the others.
 */
class DragScript(val label: String, val stage: Stage, val frames: (run: Int) -> List<DocumentCommand>)

/**
 * A Move drag of [child]'s socket along its parent with the mirror on, as the Move tool sends it: both sides' socket moved together,
 * starting where the socket sits on [placed] and stepping [SOCKET_STEP] along the parent each frame.
 */
fun moveDrag(character: Character, placed: AssembledCharacter, plan: BodyPlan?, child: PartId): DragScript {
    val ref = requireNotNull(character.part(child)?.socket) { "$child sits on no socket" }
    val parent = requireNotNull(placed.part(ref.parent)) { "${ref.parent} is not placed" }
    val spec = socketsOn(plan, parent.template, parent.placement).first { it.name == ref.socket }
    val u = parent.placement[PlacementParams.socketU(ref.socket)] ?: spec.u
    val v = parent.placement[PlacementParams.socketV(ref.socket)] ?: spec.v
    return DragScript("move ${child.value}", Stage.SHAPE) { run ->
        List(DRAG_FRAMES) { i -> moveSocket(character, ref.parent, ref.socket, Side.LEFT, u + SOCKET_STEP * (i + 1 + DRIFT * run), v, parent.mirrored, thisSide = false) }
    }
}

/** A Pose drag turning [part]'s bone [joint] [BONE_STEP] further each frame over the character's own pose, as Drag sends it. */
fun poseDrag(character: Character, part: PartId, joint: String): DragScript {
    val own = character.pose.bones.filterNot { it.part == part && it.joint == joint }
    return DragScript("pose ${part.value} $joint", Stage.POSE) { run ->
        List(DRAG_FRAMES) { i -> SetPose(character.pose.copy(bones = own + BonePose(part, joint, BONE_STEP * (i + 1 + DRIFT * run)))) }
    }
}
