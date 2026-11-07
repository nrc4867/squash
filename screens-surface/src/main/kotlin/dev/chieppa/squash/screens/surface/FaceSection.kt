package dev.chieppa.squash.screens.surface

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.isEye
import dev.chieppa.squash.document.isMouth
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.lines.FaceFeature
import dev.chieppa.squash.screens.lines.PickedGroup
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.face_both
import dev.chieppa.squash.uidesign.generated.face_eyes
import dev.chieppa.squash.uidesign.generated.face_left
import dev.chieppa.squash.uidesign.generated.face_mouth
import dev.chieppa.squash.uidesign.generated.face_new_eyes
import dev.chieppa.squash.uidesign.generated.face_new_mouth
import dev.chieppa.squash.uidesign.generated.face_right
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind
import dev.chieppa.stretch.ui.Segmented
import org.jetbrains.compose.resources.stringResource

/** Test tags for the Face inspector. */
object FaceTags {
    const val FEATURE = "face.feature"
    const val WHICH_EYE = "face.which-eye"
    const val OPEN = "face.open"
    const val SHAPE = "face.shape"
    const val DILATION = "face.dilation"
    const val WIDTH = "face.width"
    const val BIAS = "face.bias"
    const val SIZE = "face.size"
    const val NEW_EYES = "face.new-eyes"
    const val NEW_MOUTH = "face.new-mouth"
    const val ROLE_FILL = "face.role-fill"

    fun role(role: MarkRole) = "face.role.${role.name.lowercase()}"
    fun roleFill(role: MarkRole) = "face.role-fill.${role.name.lowercase()}"
    fun fillSlot(slot: String) = "face.role-fill.slot.$slot"
}

/** Which eyes an eye's work writes: both, or one alone. */
private enum class WhichEye { BOTH, LEFT, RIGHT }

/** The mark groups [character] holds on [part]. */
internal fun groupsOn(character: Character, part: PartId): List<MarkGroup> =
    character.part(part)?.let { character.resolved(it) }?.surface?.groups.orEmpty()

/** The role Draw mark starts on in [group]: the lid or the lip line. */
internal fun firstRole(group: MarkGroup): MarkRole = if (group.isEye) MarkRole.LID else MarkRole.LIP_LINE

/** Picks [feature] of the head [holder]: its first eye or its mouth, or none while it has none, with the role Draw mark starts on. */
internal fun AppState.pickFeature(character: Character, holder: PartId, feature: FaceFeature) {
    val groups = groupsOn(character, holder)
    val group = if (feature == FaceFeature.EYES) groups.firstOrNull { it.isEye } else groups.firstOrNull { it.isMouth }
    face.feature = feature
    face.group = group?.let { PickedGroup(holder, it.id) }
    face.role = group?.let(::firstRole)
}

/**
 * The Face inspector, on the head whatever is selected: Eyes or Mouth, which eye an eye's work writes, the feature's controls, its roles as layers in
 * draw order with what each holds, and for the eyes the gaze. A head with no eyes or no mouth offers to add them.
 */
@Composable
fun FaceSection(state: AppState, session: CharacterSession) {
    val document by session.document.collectAsState()
    val character = document.character
    val face = state.face
    val holder = character.faceHead(session.templates) ?: return
    val groups = groupsOn(character, holder)
    val eyes = groups.filter { it.isEye }
    val mouth = groups.firstOrNull { it.isMouth }
    val eyesName = stringResource(Res.string.face_eyes)
    val mouthName = stringResource(Res.string.face_mouth)
    DrawerSection(divider = false) {
        Segmented(FaceFeature.entries, face.feature, { state.pickFeature(character, holder, it) }, { if (it == FaceFeature.EYES) eyesName else mouthName }, Modifier.testTag(FaceTags.FEATURE), fill = true)
        when {
            face.feature == FaceFeature.EYES && eyes.isEmpty() ->
                Button(stringResource(Res.string.face_new_eyes), {
                    newEyesCommand(character, holder)?.let(session::send)
                    state.pickFeature(session.document.value.character, holder, FaceFeature.EYES)
                }, Modifier.fillMaxWidth().testTag(FaceTags.NEW_EYES), kind = ButtonKind.Quiet, icon = SquashIcons.Marks)
            face.feature == FaceFeature.MOUTH && mouth == null ->
                Button(stringResource(Res.string.face_new_mouth), {
                    session.send(newMouthCommand(holder))
                    state.pickFeature(session.document.value.character, holder, FaceFeature.MOUTH)
                }, Modifier.fillMaxWidth().testTag(FaceTags.NEW_MOUTH), kind = ButtonKind.Quiet, icon = SquashIcons.Marks)
            face.feature == FaceFeature.EYES && eyes.size > 1 -> WhichEyeControl(state, holder, eyes)
        }
    }
    val picked = face.group?.takeIf { it.part == holder } ?: return
    val group = character.groupOf(picked) ?: return
    if (group.isEye) EyeControls(state, session, picked, group, eyes) else MouthControls(session, picked, group)
    LayersSection(state, session, character, picked, group)
    if (group.isEye) GazeSection(session, character)
}

@Composable
private fun WhichEyeControl(state: AppState, holder: PartId, eyes: List<MarkGroup>) {
    val face = state.face
    val names = mapOf(WhichEye.BOTH to Res.string.face_both, WhichEye.LEFT to Res.string.face_left, WhichEye.RIGHT to Res.string.face_right).mapValues { stringResource(it.value) }
    val chosen = when {
        face.bothEyes -> WhichEye.BOTH
        face.group?.id == eyes.getOrNull(1)?.id -> WhichEye.RIGHT
        else -> WhichEye.LEFT
    }
    Segmented(WhichEye.entries, chosen, { which ->
        face.bothEyes = which == WhichEye.BOTH
        val eye = if (which == WhichEye.RIGHT) eyes[1] else eyes[0]
        face.group = PickedGroup(holder, eye.id)
        if (face.role == null) face.role = firstRole(eye)
    }, { names.getValue(it) }, Modifier.testTag(FaceTags.WHICH_EYE), fill = true)
}

