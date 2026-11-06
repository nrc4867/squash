package dev.chieppa.squash.screens.references

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.SetTrace
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.Trace
import dev.chieppa.squash.document.TraceOrder
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.edit.Readout
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.edit_percent
import dev.chieppa.squash.uidesign.generated.move_degrees
import dev.chieppa.squash.uidesign.generated.place_flip
import dev.chieppa.squash.uidesign.generated.place_percent
import dev.chieppa.squash.uidesign.generated.place_px
import dev.chieppa.squash.uidesign.generated.place_rotate
import dev.chieppa.squash.uidesign.generated.place_scale
import dev.chieppa.squash.uidesign.generated.place_snap_ground
import dev.chieppa.squash.uidesign.generated.place_x
import dev.chieppa.squash.uidesign.generated.place_y
import dev.chieppa.squash.uidesign.generated.shell_view_front
import dev.chieppa.squash.uidesign.generated.shell_view_side
import dev.chieppa.squash.uidesign.generated.shell_view_top
import dev.chieppa.squash.uidesign.generated.trace_behind
import dev.chieppa.squash.uidesign.generated.trace_lock
import dev.chieppa.squash.uidesign.generated.trace_none
import dev.chieppa.squash.uidesign.generated.trace_opacity
import dev.chieppa.squash.uidesign.generated.trace_order
import dev.chieppa.squash.uidesign.generated.trace_over
import dev.chieppa.squash.uidesign.generated.trace_place
import dev.chieppa.squash.uidesign.generated.trace_show
import dev.chieppa.stretch.core.History
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.ListRow
import dev.chieppa.stretch.frame.RowIcon
import dev.chieppa.stretch.frame.RowText
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.NumberField
import dev.chieppa.stretch.ui.Segmented
import dev.chieppa.stretch.ui.Slider
import dev.chieppa.stretch.ui.Toggle
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

private const val PERCENT = 100f

/** The References drawer's trace rows, in the board's order. */
private val TRACE_ROWS = listOf(View.SIDE, View.FRONT, View.TOP)

object TraceTags {
    const val SHOW = "trace.show"
    const val OPACITY = "trace.opacity"
    const val ORDER = "trace.order"
    const val LOCK = "trace.lock"
    const val PLACE = "trace.place"
    const val SCALE = "place.scale"
    const val X = "place.x"
    const val Y = "place.y"
    const val ROTATE = "place.rotate"
    const val FLIP = "place.flip"
    const val SNAP_GROUND = "place.snapGround"
    const val PLACE_OPACITY = "place.opacity"

    fun row(view: View) = "trace.row.${view.name.lowercase()}"
}

/** One drag on a control is one undo step: the values it passes through are previews of a single command. */
private class TraceDrag(private val history: History<SquashDocument>) {
    private var gesture by mutableStateOf<History<SquashDocument>.Transaction?>(null)

    fun update(command: SetTrace) = (gesture ?: history.begin().also { gesture = it }).update(command)

    fun finish() {
        gesture?.commit()
        gesture = null
    }
}

@Composable
private fun rememberTraceDrag(session: CharacterSession): TraceDrag {
    val drag = remember(session) { TraceDrag(session.history) }
    DisposableEffect(drag) { onDispose { drag.finish() } }
    return drag
}

@Composable
private fun viewName(view: View): String = stringResource(
    when (view) {
        View.SIDE -> Res.string.shell_view_side
        View.FRONT -> Res.string.shell_view_front
        View.TOP -> Res.string.shell_view_top
    },
)

/**
 * The References drawer's traces: the character's views, each with the image traced in it or none, and the picked view's trace shown or not, at
 * an opacity, behind or over the character, locked or free, with Place image, which frees it and arms the tool that moves it.
 */
@Composable
fun TraceDrawer(state: AppState) {
    val session by state.session.collectAsState()
    val open = session ?: return
    val document by open.document.collectAsState()
    val traces = document.character.traces
    val none = stringResource(Res.string.trace_none)
    Column {
        DrawerSection(document.character.name) {
            for (view in TRACE_ROWS) {
                val name = traces[view]?.let { document.references.image(it.image)?.name } ?: none
                ListRow(view == state.references.view, { state.references.view = view }, Modifier.testTag(TraceTags.row(view))) {
                    RowIcon(SquashIcons.References)
                    RowText(viewName(view), Modifier.weight(1f))
                    RowText(name, subtle = true)
                }
            }
        }
        val view = state.references.view
        val trace = traces[view] ?: return@Column
        val name = document.references.image(trace.image)?.name ?: return@Column
        val drag = rememberTraceDrag(open)
        fun set(next: Trace) = open.send(SetTrace(view, next))
        val show = stringResource(Res.string.trace_show)
        val opacity = stringResource(Res.string.trace_opacity)
        val lock = stringResource(Res.string.trace_lock)
        DrawerSection(name) {
            SettingRow(show) {
                Spacer(Modifier.weight(1f))
                Toggle(trace.shown, { set(trace.copy(shown = it)) }, show, Modifier.testTag(TraceTags.SHOW))
            }
            SettingRow(opacity) {
                Slider(trace.opacity, { drag.update(SetTrace(view, trace.copy(opacity = it))) }, opacity, Modifier.weight(1f).testTag(TraceTags.OPACITY), onValueChangeFinished = drag::finish)
                Readout(stringResource(Res.string.edit_percent, (trace.opacity * PERCENT).roundToInt()))
            }
            SettingRow(stringResource(Res.string.trace_order)) {
                Spacer(Modifier.weight(1f))
                val behind = stringResource(Res.string.trace_behind)
                val over = stringResource(Res.string.trace_over)
                Segmented(TraceOrder.entries, trace.order, { set(trace.copy(order = it)) }, { if (it == TraceOrder.BEHIND) behind else over }, Modifier.testTag(TraceTags.ORDER))
            }
            SettingRow(lock) {
                Spacer(Modifier.weight(1f))
                Toggle(trace.locked, { set(trace.copy(locked = it)) }, lock, Modifier.testTag(TraceTags.LOCK))
            }
        }
        DrawerSection(divider = false) {
            Button(stringResource(Res.string.trace_place), {
                if (trace.locked) set(trace.copy(locked = false))
                state.armTool(BuildTool.PLACE_IMAGE)
            }, Modifier.fillMaxWidth().testTag(TraceTags.PLACE), icon = SquashIcons.PlaceImage)
        }
    }
}

/** The References drawer's fields while placing: the placed view's trace by its numbers, Scale, X, Y and Rotate, then Flip, Snap ground and Opacity. */
@Composable
fun PlaceImageDrawer(state: AppState) {
    val session by state.session.collectAsState()
    val open = session ?: return
    val document by open.document.collectAsState()
    val view = state.references.view
    val trace = document.character.traces[view] ?: return
    val name = document.references.image(trace.image)?.name ?: return
    val drag = rememberTraceDrag(open)
    fun set(next: Trace) = open.send(SetTrace(view, next))
    val scale = stringResource(Res.string.place_scale)
    val x = stringResource(Res.string.place_x)
    val y = stringResource(Res.string.place_y)
    val rotate = stringResource(Res.string.place_rotate)
    val flip = stringResource(Res.string.place_flip)
    val snap = stringResource(Res.string.place_snap_ground)
    val opacity = stringResource(Res.string.trace_opacity)
    val px = stringResource(Res.string.place_px)
    Column {
        DrawerSection(name) {
            SettingRow(scale) {
                Spacer(Modifier.weight(1f))
                NumberField(trace.scale * PERCENT, { set(trace.copy(scale = (it / PERCENT).coerceAtLeast(MIN_FIELD_SCALE))) }, scale, Modifier.testTag(TraceTags.SCALE), unit = stringResource(Res.string.place_percent), format = ::whole)
            }
            SettingRow(x) {
                Spacer(Modifier.weight(1f))
                NumberField(trace.x, { set(trace.copy(x = it)) }, x, Modifier.testTag(TraceTags.X), unit = px, format = ::whole)
            }
            SettingRow(y) {
                Spacer(Modifier.weight(1f))
                NumberField(trace.y, { set(trace.copy(y = it)) }, y, Modifier.testTag(TraceTags.Y), unit = px, format = ::whole)
            }
            SettingRow(rotate) {
                Spacer(Modifier.weight(1f))
                NumberField(trace.rotation, { set(trace.copy(rotation = it)) }, rotate, Modifier.testTag(TraceTags.ROTATE), unit = stringResource(Res.string.move_degrees), format = ::whole)
            }
        }
        DrawerSection(divider = false) {
            SettingRow(flip) {
                Spacer(Modifier.weight(1f))
                Toggle(trace.flip, { set(trace.copy(flip = it)) }, flip, Modifier.testTag(TraceTags.FLIP))
            }
            SettingRow(snap) {
                Spacer(Modifier.weight(1f))
                Toggle(state.references.snapGround, { state.references.snapGround = it }, snap, Modifier.testTag(TraceTags.SNAP_GROUND))
            }
            SettingRow(opacity) {
                Slider(trace.opacity, { drag.update(SetTrace(view, trace.copy(opacity = it))) }, opacity, Modifier.weight(1f).testTag(TraceTags.PLACE_OPACITY), onValueChangeFinished = drag::finish)
                Readout(stringResource(Res.string.edit_percent, (trace.opacity * PERCENT).roundToInt()))
            }
        }
    }
}

/** The smallest Scale the field takes, as a share of the first fit. */
private const val MIN_FIELD_SCALE = 0.05f

private fun whole(value: Float): String = value.roundToInt().toString()
