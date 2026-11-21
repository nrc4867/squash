package dev.chieppa.squash.screens.surface

import dev.chieppa.squash.character.face.newMouthCommand

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.MouthShapeName
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.isMouth
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.lines.FaceFeature
import dev.chieppa.squash.screens.lines.PickedGroup
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.face_eyes
import dev.chieppa.squash.uidesign.generated.face_mouth
import dev.chieppa.squash.uidesign.generated.face_new_mouth
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind
import dev.chieppa.stretch.ui.Segmented
import org.jetbrains.compose.resources.stringResource

/** Test tags for the Face inspector. */
object FaceTags {
    const val FEATURE = "face.feature"
    const val NEW_MOUTH = "face.new-mouth"
    const val ROLE_FILL = "face.role-fill"
    const val SHAPES = "face.shapes"
    const val SHAPE_RESET = "face.shapes.reset"

    /** The mouth's own control [name] (open, wide, round, smile, upper-lip, lower-lip, tongue). */
    fun control(name: String) = "face.control.$name"

    /** The picked shape's control [name]. */
    fun shapeControl(name: String) = "face.shapes.control.$name"

    fun shape(shape: MouthShapeName) = "face.shapes.${shape.word}"

    fun over(role: MarkRole) = "face.role-over.${role.name.lowercase()}"

    fun role(role: MarkRole) = "face.role.${role.name.lowercase()}"
    fun roleFill(role: MarkRole) = "face.role-fill.${role.name.lowercase()}"
    fun fillSlot(slot: String) = "face.role-fill.slot.$slot"
}

/** The mark groups [character] holds on [part]. */
internal fun groupsOn(character: Character, part: PartId): List<MarkGroup> =
    character.part(part)?.let { character.resolved(it) }?.surface?.groups.orEmpty()

/** Picks [feature] of the head [holder]: its mouth with the lip line Draw mark starts on, or none while it has none or Face works on its painted eyes. */
internal fun AppState.pickFeature(character: Character, holder: PartId, feature: FaceFeature) {
    val mouth = groupsOn(character, holder).firstOrNull { it.isMouth }.takeIf { feature == FaceFeature.MOUTH }
    face.feature = feature
    face.group = mouth?.let { PickedGroup(holder, it.id) }
    face.role = mouth?.let { MarkRole.LIP_LINE }
}

/**
 * The Face inspector, on the head whatever is selected: Eyes or Mouth. The eyes are its painted eyes (see [PaintedEyeSection]). The mouth
 * shows its controls and its roles as layers in draw order with what each holds; a head with no mouth offers to add one. Above them, how
 * the face meets a turning camera (see [ViewKeyRows]).
 */
@Composable
fun FaceSection(state: AppState, session: CharacterSession) {
    val document by session.document.collectAsState()
    val character = document.character
    val face = state.face
    val holder = character.faceHead(session.templates) ?: return
    val mouth = groupsOn(character, holder).firstOrNull { it.isMouth }
    val eyesName = stringResource(Res.string.face_eyes)
    val mouthName = stringResource(Res.string.face_mouth)
    DrawerSection(divider = false) {
        Segmented(FaceFeature.entries, face.feature, { state.pickFeature(character, holder, it) }, { if (it == FaceFeature.EYES) eyesName else mouthName }, Modifier.testTag(FaceTags.FEATURE), fill = true)
        if (face.feature == FaceFeature.MOUTH && mouth == null) {
            Button(stringResource(Res.string.face_new_mouth), {
                session.send(newMouthCommand(holder))
                state.pickFeature(session.document.value.character, holder, FaceFeature.MOUTH)
            }, Modifier.fillMaxWidth().testTag(FaceTags.NEW_MOUTH), kind = ButtonKind.Quiet, icon = SquashIcons.Marks)
        }
    }
    ViewKeyRows(session, holder)
    if (face.feature == FaceFeature.EYES) return PaintedEyeSection(state, session, character, holder)
    val picked = face.group?.takeIf { it.part == holder } ?: return
    val group = character.groupOf(picked) ?: return
    val speaking by session.mouthPreview.collectAsState()
    MouthShapesSection(session, picked, group)
    if (speaking == null) MouthControls(session, picked, group)
    LayersSection(state, session, character, picked, group)
}
