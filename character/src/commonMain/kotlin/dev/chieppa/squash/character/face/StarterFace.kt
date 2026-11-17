package dev.chieppa.squash.character.face

import dev.chieppa.squash.character.build.HandLandmarks
import dev.chieppa.squash.character.model.LandmarkSpec
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.MarkGroupId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetMarkGroup
import dev.chieppa.squash.document.mouthGroup

private const val MOUTH_ID = "mouth"

/** The landmark a part's eyes sit about: the first [template] holds by hand, else its own `eye` landmark, or null. */
fun eyeLandmark(template: PartTemplate): LandmarkSpec? {
    val landmarks = template.blocks.flatMap { it.landmarks() }
    return landmarks.firstOrNull { HandLandmarks.idOf(it.name) != null } ?: landmarks.firstOrNull { it.name == EYE_LANDMARK }
}

/** The landmark a template names for its eyes. */
private const val EYE_LANDMARK = "eye"

/** The command adding an empty mouth to [part]. */
fun newMouthCommand(part: PartId): DocumentCommand = SetMarkGroup(part, MarkGroupId(MOUTH_ID), mouthGroup(MarkGroupId(MOUTH_ID), MOUTH_ID))
