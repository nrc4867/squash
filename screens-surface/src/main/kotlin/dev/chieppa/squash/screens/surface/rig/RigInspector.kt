package dev.chieppa.squash.screens.surface.rig

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.rig.MAX_SEAM_BRUSH
import dev.chieppa.squash.screens.rig.MIN_SEAM_BRUSH
import dev.chieppa.squash.screens.rig.rememberRig
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.screens.surface.Readout
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.edit_percent
import dev.chieppa.squash.uidesign.generated.rig_bone
import dev.chieppa.squash.uidesign.generated.rig_both
import dev.chieppa.squash.uidesign.generated.rig_size
import dev.chieppa.squash.uidesign.generated.rig_size_value
import dev.chieppa.squash.uidesign.generated.rig_strength
import dev.chieppa.squash.uidesign.generated.rig_this_side
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.Chip
import dev.chieppa.stretch.ui.ChipKind
import dev.chieppa.stretch.ui.Segmented
import dev.chieppa.stretch.ui.Slider
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val PERCENT = 100f

/** Test tags for the Rig inspector's tool options. */
object RigInspectorTags {
    const val BONE = "rig.tool.bone"
    const val SIZE = "rig.tool.size"
    const val STRENGTH = "rig.tool.strength"
    const val SIDES = "rig.tool.sides"
}

/** The Rig inspector: the armed tool's options, then the Bones by part with their Reach, then the Seams with their strokes. */
@Composable
fun RigInspector(state: AppState, session: CharacterSession) {
    val tool by state.tool.collectAsState()
    val document by session.document.collectAsState()
    val rig = rememberRig(state)
    val names = rigNames(document.character, state.templates)
    if (tool.rigs) ToolOptions(state, tool, names)
    if (rig == null) return
    BonesSection(state, session, rig, names)
    SeamsSection(state, session, rig, names)
}

/** Paint weight's bone, then Size, Strength and which side a stroke writes, for whichever of the three tools is armed. */
@Composable
private fun ToolOptions(state: AppState, tool: BuildTool, names: RigNames) {
    val rig = state.rig
    val mirror by state.mirror.collectAsState()
    val size = stringResource(Res.string.rig_size)
    val strength = stringResource(Res.string.rig_strength)
    val thisSide = stringResource(Res.string.rig_this_side)
    val both = stringResource(Res.string.rig_both)
    DrawerSection(stringResource(rigToolLabel(tool))) {
        if (tool == BuildTool.PAINT_WEIGHT) {
            SettingRow(stringResource(Res.string.rig_bone)) {
                Spacer(Modifier.weight(1f))
                rig.picked?.let { Chip(names.of(it), Modifier.testTag(RigInspectorTags.BONE), kind = ChipKind.On) }
            }
        }
        SettingRow(size) {
            Slider(rig.brushSize, { rig.brushSize = it }, size, Modifier.weight(1f).testTag(RigInspectorTags.SIZE), MIN_SEAM_BRUSH..MAX_SEAM_BRUSH)
            Readout(stringResource(Res.string.rig_size_value, rig.brushSize.roundToInt()))
        }
        SettingRow(strength) {
            Slider(rig.strength, { rig.strength = it }, strength, Modifier.weight(1f).testTag(RigInspectorTags.STRENGTH))
            Readout(stringResource(Res.string.edit_percent, (rig.strength * PERCENT).roundToInt()))
        }
        Segmented(listOf(true, false), !mirror, { state.mirror.value = !it }, { if (it) thisSide else both }, Modifier.testTag(RigInspectorTags.SIDES), fill = true)
    }
}
