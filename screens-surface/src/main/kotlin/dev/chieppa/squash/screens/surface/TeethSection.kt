package dev.chieppa.squash.screens.surface

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.character.model.MouthStyle
import dev.chieppa.squash.character.model.TeethPreset
import dev.chieppa.squash.character.mouth.MouthDentition
import dev.chieppa.squash.character.mouth.shaped.buildsJawOpen
import dev.chieppa.squash.character.mouth.mouthStyleOf
import dev.chieppa.squash.character.mouth.teethPresetOf
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.JawName
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.MouthTeeth
import dev.chieppa.squash.document.SetMouthTeeth
import dev.chieppa.squash.document.SetTongueLevel
import dev.chieppa.squash.document.SetTongueTip
import dev.chieppa.squash.document.TongueTip
import dev.chieppa.squash.document.ShownSlot
import dev.chieppa.squash.document.TeethLevel
import dev.chieppa.squash.document.ToothSlotChange
import dev.chieppa.squash.document.TongueLevel
import dev.chieppa.squash.document.addingRow
import dev.chieppa.squash.document.addingSlot
import dev.chieppa.squash.document.atLevel
import dev.chieppa.squash.document.changingSlot
import dev.chieppa.squash.document.removingSlot
import dev.chieppa.squash.document.resettingSlot
import dev.chieppa.squash.screens.lines.PickedGroup
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.face_over
import dev.chieppa.squash.uidesign.generated.face_teeth
import dev.chieppa.squash.uidesign.generated.face_teeth_add_row
import dev.chieppa.squash.uidesign.generated.face_teeth_add_slot
import dev.chieppa.squash.uidesign.generated.face_teeth_at
import dev.chieppa.squash.uidesign.generated.face_teeth_curve
import dev.chieppa.squash.uidesign.generated.face_teeth_gap
import dev.chieppa.squash.uidesign.generated.face_teeth_length
import dev.chieppa.squash.uidesign.generated.face_teeth_lower
import dev.chieppa.squash.uidesign.generated.face_teeth_preset
import dev.chieppa.squash.uidesign.generated.face_teeth_remove_slot
import dev.chieppa.squash.uidesign.generated.face_teeth_reset
import dev.chieppa.squash.uidesign.generated.face_teeth_slot
import dev.chieppa.squash.uidesign.generated.face_teeth_slot_name
import dev.chieppa.squash.uidesign.generated.face_teeth_upper
import dev.chieppa.squash.uidesign.generated.face_teeth_width
import dev.chieppa.squash.uidesign.generated.face_tongue_level
import dev.chieppa.squash.uidesign.generated.face_tongue_tip
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.Dropdown
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind
import dev.chieppa.stretch.ui.IconButton
import dev.chieppa.stretch.ui.Segmented
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Test tags for the mouth's Teeth section. */
object TeethTags {
    const val PRESET = "face.teeth.preset"
    const val SLOT = "face.teeth.slot"
    const val OVER = "face.teeth.over"
    const val RESET = "face.teeth.reset"
    const val ADD_SLOT = "face.teeth.add-slot"
    const val REMOVE_SLOT = "face.teeth.remove-slot"
    const val ADD_ROW = "face.teeth.add-row"
    const val TONGUE = "face.tongue.level"
    const val TONGUE_TIP = "face.tongue.tip"

    fun level(jaw: JawName) = "face.teeth.level.${jaw.name.lowercase()}"

    fun setting(name: String) = "face.teeth.$name"
}

private val JAW_NAMES: Map<JawName, StringResource> = mapOf(JawName.UPPER to Res.string.face_teeth_upper, JawName.LOWER to Res.string.face_teeth_lower)

/** One slot of a jaw as the section lists it, and [place], where it stands among its row's shown slots. */
private data class JawSlot(val jaw: JawName, val shown: ShownSlot, val place: Int)

/** One of a picked slot's numbers: its label, tag, range, how it reads off the slot as drawn, and how it writes the slot's change. */
private class SlotSetting(
    val label: StringResource,
    val name: String,
    val range: ClosedFloatingPointRange<Float>,
    val read: (SlotValues) -> Float,
    val write: (ToothSlotChange, Float) -> ToothSlotChange,
)

/** A slot's numbers as it draws: its tooth's size and curve, its gap, and where its middle sits. */
private class SlotValues(val length: Float, val width: Float, val curve: Float, val gap: Float, val at: Float)

private val SLOT_SETTINGS = listOf(
    SlotSetting(Res.string.face_teeth_length, "length", 0.01f..0.6f, { it.length }, { c, v -> c.copy(length = v) }),
    SlotSetting(Res.string.face_teeth_width, "width", 0.01f..0.3f, { it.width }, { c, v -> c.copy(width = v) }),
    SlotSetting(Res.string.face_teeth_curve, "curve", -1f..1f, { it.curve }, { c, v -> c.copy(curve = v) }),
    SlotSetting(Res.string.face_teeth_gap, "gap", 0f..0.5f, { it.gap }, { c, v -> c.copy(gap = v) }),
    SlotSetting(Res.string.face_teeth_at, "at", 0f..1.2f, { it.at }, { c, v -> c.copy(at = v) }),
)

/**
 * The mouth's Teeth: its preset, each jaw's level, and the picked slot of either jaw with its length, width, curve, spacing and position,
 * each drag one step of undo, whether it shows over the lips, Reset back to the preset, and Add slot, Remove slot and Add row; then how
 * much tongue it draws. Every choice keeps only what differs from the preset.
 */
@Composable
internal fun TeethSection(session: CharacterSession, picked: PickedGroup, group: MarkGroup) {
    val document by session.document.collectAsState()
    val drag = rememberCommandDrag(session)
    val style = document.character.part(picked.part)?.let { session.templates.mouthStyleOf(it, group) } ?: MouthStyle.PLAIN
    val preset = session.templates.teethPresetOf(group, style)
    val builtOpen = document.character.part(picked.part)?.let { buildsJawOpen(document.character, it, session.templates.find(it.template)) } ?: false
    val dentition = MouthDentition.of(preset, group.teeth, builtOpen)
    val defaults = MouthDentition.of(preset, MouthTeeth(), builtOpen)
    fun send(teeth: MouthTeeth) = session.send(SetMouthTeeth(picked.part, group.id, teeth))
    val slots = JawName.entries.flatMap { jaw ->
        group.teeth.jaw(jaw).shown(preset.jaw(jaw)).flatMap { row -> row.mapIndexed { place, shown -> JawSlot(jaw, shown, place) } }
    }
    var chosen by remember(picked) { mutableStateOf<JawSlot?>(null) }
    val slot = slots.firstOrNull { it.jaw == chosen?.jaw && it.shown.row == chosen?.shown?.row && it.shown.index == chosen?.shown?.index } ?: slots.firstOrNull()
    DrawerSection(stringResource(Res.string.face_teeth)) {
        val presetLabel = stringResource(Res.string.face_teeth_preset)
        val presets = session.templates.teethPresets.sortedBy { it.name }
        val presetNames = presets.associateWith { presetName(it) }
        val levelNames = TeethLevel.entries.associateWith { stringResource(TEETH_LEVEL_NAMES.getValue(it)) }
        val tongueNames = TongueLevel.entries.associateWith { stringResource(TONGUE_LEVEL_NAMES.getValue(it)) }
        SettingRow(presetLabel) {
            Dropdown(presets, preset, { send(group.teeth.copy(preset = it.id)) }, { presetNames[it] ?: it.name }, presetLabel, Modifier.weight(1f).testTag(TeethTags.PRESET), icon = SquashIcons.Teeth)
        }
        JawName.entries.forEach { jaw ->
            SettingRow(stringResource(JAW_NAMES.getValue(jaw))) {
                Segmented(TeethLevel.entries, dentition.jaw(jaw).level, { send(group.teeth.atLevel(jaw, it, defaults.jaw(jaw).level)) }, levelNames::getValue, Modifier.weight(1f).testTag(TeethTags.level(jaw)), compact = true)
            }
        }
        if (slot != null) {
            SlotSettings(slots, slot, dentition, group.teeth, { chosen = it }, ::send, drag::finish) { drag.update(SetMouthTeeth(picked.part, group.id, it)) }
        }
        SettingRow(stringResource(Res.string.face_tongue_level)) {
            Segmented(TongueLevel.entries, group.tongueLevel, { session.send(SetTongueLevel(picked.part, group.id, it)) }, tongueNames::getValue, Modifier.weight(1f).testTag(TeethTags.TONGUE), compact = true)
        }
        if (group.tongueLevel == TongueLevel.SHAPED) {
            val tipNames = TongueTip.entries.associateWith { stringResource(TONGUE_TIP_NAMES.getValue(it)) }
            SettingRow(stringResource(Res.string.face_tongue_tip)) {
                Segmented(TongueTip.entries, group.tongueTip, { session.send(SetTongueTip(picked.part, group.id, it)) }, tipNames::getValue, Modifier.weight(1f).testTag(TeethTags.TONGUE_TIP), compact = true)
            }
        }
    }
}

/** The picked slot: which it is, its numbers on sliders, each drag [preview]ed then ended by [finish], and its buttons. */
@Composable
private fun SlotSettings(
    slots: List<JawSlot>,
    slot: JawSlot,
    dentition: MouthDentition,
    teeth: MouthTeeth,
    pick: (JawSlot) -> Unit,
    send: (MouthTeeth) -> Unit,
    finish: () -> Unit,
    preview: (MouthTeeth) -> Unit,
) {
    val slotLabel = stringResource(Res.string.face_teeth_slot)
    val names = slots.associateWith { slotName(it) }
    SettingRow(slotLabel) {
        Dropdown(slots, slot, pick, { names.getValue(it) }, slotLabel, Modifier.weight(1f).testTag(TeethTags.SLOT))
    }
    val values = valuesOf(slot, dentition)
    SLOT_SETTINGS.forEach { setting ->
        ControlSlider(setting.label, setting.read(values), TeethTags.setting(setting.name), finish, setting.range) { v ->
            preview(teeth.changingSlot(slot.jaw, slot.shown) { setting.write(it, v) })
        }
    }
    val change = teeth.jaw(slot.jaw).changeAt(slot.shown.row, slot.shown.index)
    Row(Modifier.fillMaxWidth()) {
        IconButton(SquashIcons.MarkOver, stringResource(Res.string.face_over), {
            send(teeth.changingSlot(slot.jaw, slot.shown) { it.copy(over = !slot.shown.slot.over) })
        }, Modifier.testTag(TeethTags.OVER), on = slot.shown.slot.over)
        IconButton(SquashIcons.ToothAdd, stringResource(Res.string.face_teeth_add_slot), { send(teeth.addingSlot(slot.jaw, slot.shown)) }, Modifier.testTag(TeethTags.ADD_SLOT))
        IconButton(SquashIcons.ToothRemove, stringResource(Res.string.face_teeth_remove_slot), { send(teeth.removingSlot(slot.jaw, slot.shown)) }, Modifier.testTag(TeethTags.REMOVE_SLOT))
        IconButton(SquashIcons.TeethRowAdd, stringResource(Res.string.face_teeth_add_row), {
            send(teeth.addingRow(slot.jaw, dentition.jaw(slot.jaw).rows.size, slot.shown.slot))
        }, Modifier.testTag(TeethTags.ADD_ROW))
        Spacer(Modifier.weight(1f))
    }
    Button(
        stringResource(Res.string.face_teeth_reset),
        { send(teeth.resettingSlot(slot.jaw, slot.shown)) },
        Modifier.fillMaxWidth().testTag(TeethTags.RESET),
        kind = ButtonKind.Quiet,
        enabled = change != null,
    )
}

/** [slot]'s numbers as it draws: its tooth with the slot's own settings, its gap, and its middle along the row. */
private fun valuesOf(slot: JawSlot, dentition: MouthDentition): SlotValues {
    val tooth = dentition.toothOf(slot.shown.slot)
    val row = dentition.jaw(slot.jaw).rows.getOrNull(slot.shown.row)?.slots.orEmpty()
    val at = slot.shown.slot.at ?: dentition.middles(row).getOrNull(slot.place) ?: 0f
    return SlotValues(tooth.length, tooth.width, tooth.curve, slot.shown.slot.gap, at)
}

@Composable
private fun slotName(slot: JawSlot): String =
    stringResource(Res.string.face_teeth_slot_name, stringResource(JAW_NAMES.getValue(slot.jaw)), slot.shown.row + 1, slot.shown.index + 1, slot.shown.slot.type)

@Composable
private fun presetName(preset: TeethPreset): String = TEETH_PRESET_NAMES[preset.id]?.let { stringResource(it) } ?: preset.name

