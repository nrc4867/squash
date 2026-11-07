package dev.chieppa.squash.screens.edit

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.CurveLimit
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.View
import dev.chieppa.squash.document.handlesAt
import dev.chieppa.squash.document.isSmooth
import dev.chieppa.squash.document.moved
import dev.chieppa.squash.document.smoothed
import dev.chieppa.squash.geom.StrokeEnds
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.draw.OutlineRowsSection
import dev.chieppa.squash.screens.draw.strokeEnds
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.edit_falloff
import dev.chieppa.squash.uidesign.generated.edit_handle
import dev.chieppa.squash.uidesign.generated.edit_handle_corner
import dev.chieppa.squash.uidesign.generated.edit_handle_smooth
import dev.chieppa.squash.uidesign.generated.edit_percent
import dev.chieppa.squash.uidesign.generated.edit_selected_point
import dev.chieppa.squash.uidesign.generated.edit_snap_ends
import dev.chieppa.squash.uidesign.generated.edit_tool_oversketch
import dev.chieppa.squash.uidesign.generated.edit_x
import dev.chieppa.squash.uidesign.generated.edit_y
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.ui.NumberField
import dev.chieppa.stretch.ui.Segmented
import dev.chieppa.stretch.ui.Slider
import dev.chieppa.stretch.ui.Toggle
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

private const val PERCENT = 100f
private const val COORDINATE_STEP = 0.01f
private val PERCENT_WIDTH = 36.dp

/** Test tags for the drawer's controls. */
object CurveDrawerTags {
    const val X = "curves.x"
    const val Y = "curves.y"
    const val FALLOFF = "curves.falloff"
    const val SNAP = "curves.snap"

    fun outline(view: View) = "curves.outline.${view.name}"
}

private enum class HandleKind { SMOOTH, CORNER }

/** The Outline curves drawer's sections: the selected point, the oversketch's settings and the outlines drawn. */
@Composable
fun OutlineCurvesSections(state: AppState, session: CharacterSession, part: PartInstance, outlines: Map<View, OutlineCurve>) {
    SelectedPointSection(state, session, part, outlines)
    OversketchSection(state)
    OutlineRowsSection(state, outlines, CurveDrawerTags::outline)
}

@Composable
internal fun Readout(text: String, modifier: Modifier = Modifier) {
    BasicText(text, modifier, style = Stretch.type.value.merge(TextStyle(color = Stretch.colors.inkMuted)), maxLines = 1)
}

@Composable
private fun SelectedPointSection(state: AppState, session: CharacterSession, part: PartInstance, outlines: Map<View, OutlineCurve>) {
    val selection = state.curves.selection?.takeIf { it.part == part.id } ?: return
    val curve = outlines[selection.view]?.takeIf { selection.index in it.points.indices } ?: return
    val ends = strokeEnds(selection.view, session.builtTemplate(part).openEnd) as? StrokeEnds.CutAtPlane
    val limit = ends?.let { CurveLimit(it.plane, it.above) }
    val point = curve.points[selection.index]
    fun moveTo(to: CurvePoint) = session.send(SetOutline(part.id, selection.view, curve.moved(selection.index, to, limit), state.thisSide))
    DrawerSection(stringResource(Res.string.edit_selected_point)) {
        val x = stringResource(Res.string.edit_x)
        val y = stringResource(Res.string.edit_y)
        SettingRow(x) {
            Spacer(Modifier.weight(1f))
            NumberField(point.x, { moveTo(point.copy(x = it)) }, x, Modifier.testTag(CurveDrawerTags.X), step = COORDINATE_STEP)
        }
        SettingRow(y) {
            Spacer(Modifier.weight(1f))
            NumberField(point.y, { moveTo(point.copy(y = it)) }, y, Modifier.testTag(CurveDrawerTags.Y), step = COORDINATE_STEP)
        }
        HandleRow(state, session, part, selection, curve, limit)
    }
}

/** Smooth or Corner for the selected point's anchor, which an open end, with one handle, has no choice of. */
@Composable
private fun HandleRow(
    state: AppState,
    session: CharacterSession,
    part: PartInstance,
    selection: CurveSelection,
    curve: OutlineCurve,
    limit: CurveLimit?,
) {
    val anchor = curve.owningAnchor(selection.index)
    if (curve.handlesAt(anchor).size < 2) return
    val key = selection.copy(index = anchor)
    val smooth = curve.isSmooth(anchor) && !state.curves.isMarkedCorner(key)
    val labels = mapOf(
        HandleKind.SMOOTH to stringResource(Res.string.edit_handle_smooth),
        HandleKind.CORNER to stringResource(Res.string.edit_handle_corner),
    )
    SettingRow(stringResource(Res.string.edit_handle)) {
        Spacer(Modifier.weight(1f))
        Segmented(
            HandleKind.entries,
            if (smooth) HandleKind.SMOOTH else HandleKind.CORNER,
            { kind ->
                state.curves.markCorner(key, kind == HandleKind.CORNER)
                if (kind == HandleKind.SMOOTH && !curve.isSmooth(anchor)) {
                    session.send(SetOutline(part.id, selection.view, curve.smoothed(anchor, limit), state.thisSide))
                }
            },
            { labels.getValue(it) },
        )
    }
}

@Composable
private fun OversketchSection(state: AppState) {
    val curves = state.curves
    val falloff = stringResource(Res.string.edit_falloff)
    val snap = stringResource(Res.string.edit_snap_ends)
    DrawerSection(stringResource(Res.string.edit_tool_oversketch)) {
        SettingRow(falloff) {
            Slider(curves.falloff, { curves.falloff = it }, falloff, Modifier.weight(1f).testTag(CurveDrawerTags.FALLOFF))
            Readout(stringResource(Res.string.edit_percent, (curves.falloff * PERCENT).roundToInt()), Modifier.width(PERCENT_WIDTH))
        }
        SettingRow(snap) {
            Spacer(Modifier.weight(1f))
            Toggle(curves.snapEnds, { curves.snapEnds = it }, snap, Modifier.testTag(CurveDrawerTags.SNAP))
        }
    }
}
