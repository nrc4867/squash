package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.build.AxisCurveParams
import dev.chieppa.squash.character.build.CurveFit
import dev.chieppa.squash.character.build.MAX_SPIRAL_TURNS
import dev.chieppa.squash.character.build.sweeps
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.View
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.curve_add_place
import dev.chieppa.squash.uidesign.generated.curve_along
import dev.chieppa.squash.uidesign.generated.curve_ignored
import dev.chieppa.squash.uidesign.generated.curve_narrowed
import dev.chieppa.squash.uidesign.generated.curve_pitch
import dev.chieppa.squash.uidesign.generated.curve_place
import dev.chieppa.squash.uidesign.generated.curve_remove_place
import dev.chieppa.squash.uidesign.generated.curve_shrink
import dev.chieppa.squash.uidesign.generated.curve_side
import dev.chieppa.squash.uidesign.generated.curve_title
import dev.chieppa.squash.uidesign.generated.curve_turns
import dev.chieppa.squash.uidesign.generated.curve_up
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.Chip
import dev.chieppa.stretch.ui.ChipKind
import dev.chieppa.stretch.ui.IconButton
import dev.chieppa.stretch.ui.NumberField
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val PERCENT = 100f
private const val PLACE_STEP = 0.05f
private const val PITCH_LIMIT = 1f
private const val SHRINK_MIN = 0.05f
private const val SHRINK_MAX = 2f
private const val SPIRAL_STEP = 0.05f
private val PLACE_FIELD_WIDTH = 56.dp

/**
 * Shape's Curve section, for a part whose template sweeps along its axis: its spiral's turns (none at 0), pitch and shrink, else its
 * curve's places, along, up and side each, with a place added or the last removed. Editing a template's places makes them the part's
 * own. A curve the template ignores, or one bent tighter than the part is thick, is flagged with a warning chip. Every change is one
 * command, to this side alone with [thisSide].
 */
@Composable
internal fun CurveSection(
    session: CharacterSession,
    part: PartId,
    template: PartTemplate,
    params: Map<String, Float>,
    outlines: Map<View, OutlineCurve>,
    thisSide: Boolean,
) {
    val fit = remember(template, params, outlines) { CurveFit.of(template, params, outlines) }
    if (!template.sweeps && fit?.ignored != true) return
    val rows = CurveRows.of(template, params)
    DrawerSection(stringResource(Res.string.curve_title)) {
        Column(Modifier.fillMaxWidth().testTag(ShellTags.CURVE), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CurveWarning(fit)
            if (!template.sweeps) return@Column
            val turns = rows.spiral?.turns ?: 0f
            SpiralControl(session, part, AxisCurveParams.SPIRAL_TURNS, turns, -MAX_SPIRAL_TURNS, MAX_SPIRAL_TURNS, thisSide, stringResource(Res.string.curve_turns))
            val spiral = rows.spiral
            if (spiral != null) {
                SpiralControl(session, part, AxisCurveParams.SPIRAL_PITCH, spiral.pitch, -PITCH_LIMIT, PITCH_LIMIT, thisSide, stringResource(Res.string.curve_pitch))
                SpiralControl(session, part, AxisCurveParams.SPIRAL_SHRINK, spiral.shrink, SHRINK_MIN, SHRINK_MAX, thisSide, stringResource(Res.string.curve_shrink))
                return@Column
            }
            rows.places.forEachIndexed { i, place ->
                key(part, i) { PlaceRow(i, listOf(place.x, place.y, place.z)) { axis, value -> session.send(curveCommand(part, params, rows.places.withPlace(i, axis, value), thisSide)) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Spacer(Modifier.weight(1f))
                IconButton(
                    SquashIcons.RemoveCurvePlace,
                    stringResource(Res.string.curve_remove_place),
                    { session.send(curveCommand(part, params, rows.places.dropLast(1), thisSide)) },
                    Modifier.testTag(ShellTags.CURVE_REMOVE),
                    enabled = rows.canRemove,
                )
                IconButton(
                    SquashIcons.AddCurvePlace,
                    stringResource(Res.string.curve_add_place),
                    { session.send(curveCommand(part, params, rows.places.withPlaceAdded(), thisSide)) },
                    Modifier.testTag(ShellTags.CURVE_ADD),
                    enabled = rows.canAdd,
                )
            }
        }
    }
}

/** A warning chip where the curve is ignored, or the share its rings narrow to where it is clipped; nothing where it lands whole. */
@Composable
private fun CurveWarning(fit: CurveFit?) {
    val text = when {
        fit == null -> return
        fit.ignored -> stringResource(Res.string.curve_ignored)
        fit.clipped -> stringResource(Res.string.curve_narrowed, (fit.hold * PERCENT).roundToInt())
        else -> return
    }
    Chip(text, Modifier.testTag(ShellTags.CURVE_WARNING), kind = ChipKind.Warn, mono = true)
}

/** One spiral number as a slider between [min] and [max], a drag one undo step. */
@Composable
private fun SpiralControl(session: CharacterSession, part: PartId, name: String, value: Float, min: Float, max: Float, thisSide: Boolean, label: String) {
    ParameterControl(session, part, ParameterRow(name, value, isFlag = false, min = min, max = max, step = SPIRAL_STEP, unit = null), thisSide, hidden = null, label = label)
}

/** Place [index]'s along, up and side, each a number field sending [onChange] once per edit. */
@Composable
private fun PlaceRow(index: Int, values: List<Float>, onChange: (PlaceAxis, Float) -> Unit) {
    val labels = listOf(stringResource(Res.string.curve_along), stringResource(Res.string.curve_up), stringResource(Res.string.curve_side))
    SettingRow(stringResource(Res.string.curve_place, index + 1)) {
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PlaceAxis.entries.forEachIndexed { a, axis ->
                NumberField(
                    values[a],
                    { typed -> if (typed != values[a]) onChange(axis, typed) },
                    labels[a],
                    Modifier.testTag(ShellTags.curvePlace(index, axis.name.lowercase())),
                    step = PLACE_STEP,
                    width = PLACE_FIELD_WIDTH,
                )
            }
        }
    }
}
