package dev.chieppa.squash.screens.surface.rig

import dev.chieppa.squash.character.build.RigParams
import dev.chieppa.squash.document.AddSeamStrokes
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.ResetSeam
import dev.chieppa.squash.document.SeamStrokeId
import dev.chieppa.squash.document.SeamToTwin
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.Ulid
import dev.chieppa.squash.document.seamStrokes
import dev.chieppa.squash.rig.BoneName
import dev.chieppa.squash.rig.Rig
import dev.chieppa.squash.rig.strayPatches

/** The reach [character] gives the joint [bone] turns at: its part's parameter, or 1 where none is set. */
fun reachOf(character: Character, bone: BoneName): Float =
    character.resolved(bone.part)?.params?.get(RigParams.reach(bone.joint)) ?: 1f

/** The command setting [bone]'s reach to [value], held to the reach's range: on its pair, or with [thisSide] on its side alone. */
fun reachCommand(character: Character, bone: BoneName, value: Float, thisSide: Boolean): SetParameter? {
    val part = character.part(bone.part) ?: return null
    val reach = value.coerceIn(RigParams.MIN_REACH, RigParams.MAX_REACH)
    return SetParameter(part.id, RigParams.reach(bone.joint), reach, thisSide && part.pair != null)
}

/** The seam the Seams buttons act on: the one [picked] in the list, or else the one [bone] is the first bone of. */
fun currentSeam(rig: Rig, picked: PartId?, bone: BoneName?): PartId? =
    picked?.takeIf { rig.seam(it) != null } ?: bone?.let(rig::indexOf)?.let { index -> rig.seams.firstOrNull { it.first == index }?.child }

/** Tidy for [child]'s seam: one command handing each stray patch to the bone on its border, or null when there are none. */
fun tidyCommand(rig: Rig, character: Character, child: PartId, thisSide: Boolean): AddSeamStrokes? {
    val patches = rig.strayPatches(child)
    if (patches.isEmpty()) return null
    val paired = character.part(child)?.pair != null
    return AddSeamStrokes(child, patches.map { it.stroke(SeamStrokeId(Ulid.next())) }, thisSide && paired)
}

/** To twin for [child]'s seam, or null when it has no twin. */
fun toTwinCommand(character: Character, child: PartId): SeamToTwin? =
    character.part(child)?.let(character::twinOf)?.let { SeamToTwin(child) }

/** Reset for [child]'s seam, or null when it holds no stroke. */
fun resetCommand(character: Character, child: PartId, thisSide: Boolean): ResetSeam? {
    if (character.seamStrokes(child).isEmpty()) return null
    return ResetSeam(child, thisSide && character.part(child)?.pair != null)
}
