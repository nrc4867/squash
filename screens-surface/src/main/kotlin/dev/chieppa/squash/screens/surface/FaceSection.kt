package dev.chieppa.squash.screens.surface

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.GazeCheats
import dev.chieppa.squash.document.GroupRole
import dev.chieppa.squash.document.MarkControls
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.MouthShape
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetGazeCheats
import dev.chieppa.squash.document.SetMarkControls
import dev.chieppa.squash.document.SetMarkGroup
import dev.chieppa.squash.document.Together
import dev.chieppa.squash.document.isEye
import dev.chieppa.squash.document.isMouth
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.lines.PickedGroup
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.screens.sheet.INK_SLOT
import dev.chieppa.squash.screens.surface.paint.SlotChoice
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.face_angles
import dev.chieppa.squash.uidesign.generated.face_camera_bias
import dev.chieppa.squash.uidesign.generated.face_dilation
import dev.chieppa.squash.uidesign.generated.face_eye
import dev.chieppa.squash.uidesign.generated.face_fill
import dev.chieppa.squash.uidesign.generated.face_left
import dev.chieppa.squash.uidesign.generated.face_mouth
import dev.chieppa.squash.uidesign.generated.face_mouth_frown
import dev.chieppa.squash.uidesign.generated.face_mouth_grimace
import dev.chieppa.squash.uidesign.generated.face_mouth_o
import dev.chieppa.squash.uidesign.generated.face_mouth_smile
import dev.chieppa.squash.uidesign.generated.face_new_eyes
import dev.chieppa.squash.uidesign.generated.face_new_mouth
import dev.chieppa.squash.uidesign.generated.face_open
import dev.chieppa.squash.uidesign.generated.face_right
import dev.chieppa.squash.uidesign.generated.face_role_brow
import dev.chieppa.squash.uidesign.generated.face_role_corners
import dev.chieppa.squash.uidesign.generated.face_role_highlight
import dev.chieppa.squash.uidesign.generated.face_role_inner
import dev.chieppa.squash.uidesign.generated.face_role_iris
import dev.chieppa.squash.uidesign.generated.face_role_lashes
import dev.chieppa.squash.uidesign.generated.face_role_lid
import dev.chieppa.squash.uidesign.generated.face_role_lip_line
import dev.chieppa.squash.uidesign.generated.face_role_pupil
import dev.chieppa.squash.uidesign.generated.face_role_teeth
import dev.chieppa.squash.uidesign.generated.face_role_tongue
import dev.chieppa.squash.uidesign.generated.face_role_white
import dev.chieppa.squash.uidesign.generated.face_roles
import dev.chieppa.squash.uidesign.generated.face_section
import dev.chieppa.squash.uidesign.generated.face_shape
import dev.chieppa.squash.uidesign.generated.face_size_compensation
import dev.chieppa.squash.uidesign.generated.face_switches
import dev.chieppa.squash.uidesign.generated.face_switches_value
import dev.chieppa.squash.uidesign.generated.face_target
import dev.chieppa.squash.uidesign.generated.face_test_gaze
import dev.chieppa.squash.uidesign.generated.face_value
import dev.chieppa.squash.uidesign.generated.face_which_eye
import dev.chieppa.squash.uidesign.generated.face_width
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.ListRow
import dev.chieppa.stretch.frame.RowIcon
import dev.chieppa.stretch.frame.RowText
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind
import dev.chieppa.stretch.ui.Chip
import dev.chieppa.stretch.ui.ChipKind
import dev.chieppa.stretch.ui.Segmented
import dev.chieppa.stretch.ui.Slider
import dev.chieppa.stretch.ui.Toggle
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val CHIP_GAP = 4.dp
private const val HUNDREDTHS = 100f
private const val ANGLE_LIMIT = 90

/** How far a dilation slider reaches either way, as a share of the pupil's size. */
private const val MOST_DILATION = 1f

/** How wide a mouth's width control reaches. */
private val WIDTH_RANGE = 0.5f..1.5f

/** Test tags for the Marks drawer's Face section. */
object FaceTags {
    const val EYE = "face.eye"
    const val MOUTH = "face.mouth"
    const val WHICH_EYE = "face.which-eye"
    const val OPEN = "face.open"
    const val SHAPE = "face.shape"
    const val DILATION = "face.dilation"
    const val WIDTH = "face.width"
    const val MIRROR = "face.mirror"
    const val GAZE = "face.gaze"
    const val BIAS = "face.bias"
    const val SIZE = "face.size"
    const val NEW_EYES = "face.new-eyes"
    const val NEW_MOUTH = "face.new-mouth"
    const val ROLE_FILL = "face.role-fill"

    fun role(role: MarkRole) = "face.role.${role.name.lowercase()}"
    fun fillSlot(slot: String) = "face.role-fill.$slot"
}

private val ROLE_NAMES: Map<MarkRole, StringResource> = mapOf(
    MarkRole.LID to Res.string.face_role_lid,
    MarkRole.WHITE to Res.string.face_role_white,
    MarkRole.IRIS to Res.string.face_role_iris,
    MarkRole.PUPIL to Res.string.face_role_pupil,
    MarkRole.HIGHLIGHT to Res.string.face_role_highlight,
    MarkRole.BROW to Res.string.face_role_brow,
    MarkRole.LASHES to Res.string.face_role_lashes,
    MarkRole.LIP_LINE to Res.string.face_role_lip_line,
    MarkRole.INNER to Res.string.face_role_inner,
    MarkRole.TEETH to Res.string.face_role_teeth,
    MarkRole.TONGUE to Res.string.face_role_tongue,
    MarkRole.CORNERS to Res.string.face_role_corners,
)

/** Roles in the order the drawer lists them, which is not their draw order. */
private val EYE_ROLE_ORDER = listOf(MarkRole.LID, MarkRole.WHITE, MarkRole.IRIS, MarkRole.PUPIL, MarkRole.HIGHLIGHT, MarkRole.BROW, MarkRole.LASHES)
private val MOUTH_ROLE_ORDER = listOf(MarkRole.LIP_LINE, MarkRole.INNER, MarkRole.TEETH, MarkRole.TONGUE, MarkRole.CORNERS)

private val MOUTH_SHAPE_NAMES = mapOf(
    MouthShape.SMILE to Res.string.face_mouth_smile,
    MouthShape.FROWN to Res.string.face_mouth_frown,
    MouthShape.O to Res.string.face_mouth_o,
    MouthShape.GRIMACE to Res.string.face_mouth_grimace,
)

/** The mark groups [character] holds on [part]. */
private fun groupsOn(character: Character, part: PartId): List<MarkGroup> =
    character.part(part)?.let { character.resolved(it) }?.surface?.groups.orEmpty()

/** The part a face row shows: the picked group's, else the first holding a group, else the selected one. */
private fun faceHolder(character: Character, picked: PickedGroup?, selected: PartId?): PartId? =
    picked?.part ?: character.parts.firstOrNull { groupsOn(character, it.id).isNotEmpty() }?.id ?: selected

/**
 * The Marks drawer's Face section: an Eye row and a Mouth row. Picking one arms the face tools and opens its controls, its roles, the test gaze and what its expression poses
 * below; an eye's controls are that eye's, and with Mirror on both eyes'.
 */
@Composable
fun FaceSection(state: AppState, session: CharacterSession) {
    val document by session.document.collectAsState()
    val character = document.character
    val selected by state.selectedPart.collectAsState()
    val face = state.face
    val holder = faceHolder(character, face.group, selected) ?: return
    val groups = groupsOn(character, holder)
    val eyes = groups.filter { it.isEye }
    val mouth = groups.firstOrNull { it.isMouth }
    val pickedGroup = face.group?.let { character.groupOf(it) }
    fun pick(group: MarkGroup?) {
        if (group == null || face.group?.id == group.id) {
            face.group = null
            face.role = null
            state.armTool(BuildTool.LINE_WIDTH)
        } else {
            face.group = PickedGroup(holder, group.id)
            face.role = if (group.isEye) MarkRole.LID else MarkRole.LIP_LINE
            state.selectPart(holder)
            if (!state.tool.value.faces) state.armTool(BuildTool.DRAW_MARK)
        }
    }
    DrawerSection(stringResource(Res.string.face_section)) {
        if (eyes.isNotEmpty()) {
            val on = pickedGroup?.isEye == true
            ListRow(on, { pick(eyes.first().takeUnless { on }) }, Modifier.testTag(FaceTags.EYE)) {
                RowIcon(SquashIcons.Marks)
                RowText(stringResource(Res.string.face_eye), Modifier.weight(1f))
                RowText(stringResource(Res.string.face_angles), subtle = true)
            }
        } else {
            Button(stringResource(Res.string.face_new_eyes), { newEyesCommand(character, holder)?.let(session::send) }, Modifier.testTag(FaceTags.NEW_EYES), kind = ButtonKind.Quiet, icon = SquashIcons.Marks)
        }
        if (mouth != null) {
            val on = pickedGroup?.isMouth == true
            ListRow(on, { pick(mouth.takeUnless { on }) }, Modifier.testTag(FaceTags.MOUTH)) {
                RowIcon(SquashIcons.Marks)
                RowText(stringResource(Res.string.face_mouth), Modifier.weight(1f))
                RowText(stringResource(Res.string.face_angles), subtle = true)
            }
        } else {
            Button(stringResource(Res.string.face_new_mouth), { session.send(newMouthCommand(holder)) }, Modifier.testTag(FaceTags.NEW_MOUTH), kind = ButtonKind.Quiet, icon = SquashIcons.Marks)
        }
    }
    val group = pickedGroup ?: return
    val picked = face.group ?: return
    if (group.isEye) EyeControls(state, session, picked, group, eyes) else MouthControls(session, picked, group)
    RolesSection(state, session, character, picked, group)
    if (group.isEye) GazeSection(state, session, character)
}

@Composable
private fun EyeControls(state: AppState, session: CharacterSession, picked: PickedGroup, group: MarkGroup, eyes: List<MarkGroup>) {
    val drag = rememberCommandDrag(session)
    val mirror by state.mirror.collectAsState()
    fun set(change: (MarkControls) -> MarkControls) {
        val both = if (mirror) eyes else listOf(group)
        drag.update(Together(both.map { SetMarkControls(picked.part, it.id, change(it.controls)) }))
    }
    DrawerSection(stringResource(Res.string.face_eye)) {
        if (eyes.size > 1) {
            val left = stringResource(Res.string.face_left)
            val right = stringResource(Res.string.face_right)
            SettingRow(stringResource(Res.string.face_which_eye)) {
                Segmented(eyes, group, { state.face.group = PickedGroup(picked.part, it.id) }, { if (it == eyes.first()) left else right }, Modifier.testTag(FaceTags.WHICH_EYE))
            }
        }
        ControlSlider(Res.string.face_open, group.controls.open, FaceTags.OPEN, drag::finish) { value -> set { it.copy(open = value) } }
        ControlSlider(Res.string.face_shape, group.controls.expressionBlend, FaceTags.SHAPE, drag::finish) { value -> set { it.copy(expressionBlend = value) } }
        ControlSlider(Res.string.face_dilation, group.controls.dilation, FaceTags.DILATION, drag::finish, -MOST_DILATION..MOST_DILATION, shown = { ((1f + it) * HUNDREDTHS).roundToInt() / HUNDREDTHS }) { value -> set { it.copy(dilation = value) } }
        val switches = stringResource(Res.string.face_switches)
        SettingRow(switches) {
            Spacer(Modifier.weight(1f))
            RowText(stringResource(Res.string.face_switches_value, FACE_BINS.edges[0].roundToInt(), FACE_BINS.edges[1].roundToInt().coerceAtMost(ANGLE_LIMIT)), subtle = true)
        }
    }
}

@Composable
private fun MouthControls(session: CharacterSession, picked: PickedGroup, group: MarkGroup) {
    val drag = rememberCommandDrag(session)
    fun set(controls: MarkControls) = drag.update(SetMarkControls(picked.part, group.id, controls))
    DrawerSection(stringResource(Res.string.face_mouth)) {
        ControlSlider(Res.string.face_open, group.controls.open, FaceTags.OPEN, drag::finish) { set(group.controls.copy(open = it)) }
        val names = MOUTH_SHAPE_NAMES.mapValues { stringResource(it.value) }
        SettingRow(stringResource(Res.string.face_shape)) {
            Segmented(MouthShape.entries, group.controls.mouthShape ?: MouthShape.SMILE, {
                session.send(SetMarkControls(picked.part, group.id, group.controls.copy(mouthShape = it.takeUnless { chosen -> chosen == group.controls.mouthShape })))
            }, { names.getValue(it) }, Modifier.testTag(FaceTags.SHAPE))
        }
        ControlSlider(Res.string.face_width, group.controls.width, FaceTags.WIDTH, drag::finish, WIDTH_RANGE) { set(group.controls.copy(width = it)) }
    }
}

@Composable
private fun ControlSlider(
    label: StringResource,
    value: Float,
    tag: String,
    onFinish: () -> Unit,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    shown: (Float) -> Float = { (it * HUNDREDTHS).roundToInt() / HUNDREDTHS },
    onChange: (Float) -> Unit,
) {
    val name = stringResource(label)
    SettingRow(name) {
        Slider(value, onChange, name, Modifier.weight(1f).testTag(tag), valueRange = range, onValueChangeFinished = onFinish)
        RowText(stringResource(Res.string.face_value, shown(value)), subtle = true)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RolesSection(state: AppState, session: CharacterSession, character: Character, picked: PickedGroup, group: MarkGroup) {
    val order = if (group.isEye) EYE_ROLE_ORDER else MOUTH_ROLE_ORDER
    val roles = order.filter { role -> group.roles.any { it.role == role } }
    val shown = state.face.role
    DrawerSection(stringResource(Res.string.face_roles)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(CHIP_GAP), verticalArrangement = Arrangement.spacedBy(CHIP_GAP)) {
            roles.forEach { role ->
                Chip(stringResource(ROLE_NAMES.getValue(role)), Modifier.clickable { state.face.role = role }.testTag(FaceTags.role(role)), kind = if (role == shown) ChipKind.On else ChipKind.Default)
            }
        }
        val held = group.roles.firstOrNull { it.role == shown } ?: return@DrawerSection
        if (shown in CLOSED_ROLES && shown != MarkRole.LID) {
            val fill = stringResource(Res.string.face_fill)
            SettingRow(fill) {
                Spacer(Modifier.weight(1f))
                SlotChoice(character.palette, held.fill ?: INK_SLOT, fill, FaceTags.ROLE_FILL, { FaceTags.fillSlot(it.value) }, { slot ->
                    session.send(SetMarkGroup(picked.part, group.id, group.withRole(held.copy(fill = slot))))
                })
            }
        }
    }
}

private fun MarkGroup.withRole(role: GroupRole) = copy(roles = roles.map { if (it.role == role.role) role else it })

@Composable
private fun GazeSection(state: AppState, session: CharacterSession, character: Character) {
    val drag = rememberCommandDrag(session)
    val cheats: GazeCheats = character.gazeCheats
    val face = state.face
    DrawerSection(stringResource(Res.string.face_test_gaze)) {
        val target = stringResource(Res.string.face_target)
        SettingRow(target) {
            Spacer(Modifier.weight(1f))
            Toggle(face.gazeOn, { face.gazeOn = it }, target, Modifier.testTag(FaceTags.GAZE))
        }
        ControlSlider(Res.string.face_camera_bias, cheats.cameraBias, FaceTags.BIAS, drag::finish) { drag.update(SetGazeCheats(cheats.copy(cameraBias = it))) }
        val size = stringResource(Res.string.face_size_compensation)
        SettingRow(size) {
            Spacer(Modifier.weight(1f))
            Toggle(cheats.sizeCompensation, { session.send(SetGazeCheats(cheats.copy(sizeCompensation = it))) }, size, Modifier.testTag(FaceTags.SIZE))
        }
    }
}
