package dev.chieppa.squash.screens.look.drag

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.build.moveSocket
import dev.chieppa.squash.character.build.socketsOn
import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.DEFAULT_EASE
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.EaseKey
import dev.chieppa.squash.document.EaseProfile
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetGarmentFit
import dev.chieppa.squash.document.SetPartEase
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

/** How much a garment's fit or ease grows each frame of a Fit or Ease drag, in body units. */
private const val EASE_STEP = 0.0005f

/** A Fit slider drag on [garment], its fit [EASE_STEP] looser each frame, as Dress's Fit row sends it. */
fun fitDrag(character: Character, garment: GarmentId): DragScript {
    val held = requireNotNull(character.wardrobe.garment(garment)) { "No garment ${garment.value}" }
    val start = held.fit ?: DEFAULT_EASE
    return DragScript("fit ${garment.value}", Stage.DRESS) { run ->
        List(DRAG_FRAMES) { i -> SetGarmentFit(garment, start + EASE_STEP * (i + 1 + DRIFT * run)) }
    }
}

/**
 * An Ease slider drag on [garment]'s profile along [part], its last key [EASE_STEP] looser each frame, as Dress's key row sends it; a
 * profile at the part's fit where it has none.
 */
fun easeDrag(character: Character, garment: GarmentId, part: PartId): DragScript {
    val held = requireNotNull(character.wardrobe.garment(garment)) { "No garment ${garment.value}" }
    val profile = held.eases[part] ?: EaseProfile(listOf(EaseKey(0f, held.fit ?: DEFAULT_EASE), EaseKey(1f, held.fit ?: DEFAULT_EASE)))
    val last = profile.keys.last()
    return DragScript("ease ${garment.value} ${part.value}", Stage.DRESS) { run ->
        List(DRAG_FRAMES) { i ->
            val key = last.copy(ease = last.ease + EASE_STEP * (i + 1 + DRIFT * run))
            SetPartEase(garment, part, EaseProfile(profile.keys.dropLast(1) + key))
        }
    }
}
