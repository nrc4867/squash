package dev.chieppa.squash.screens.surface

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.face_degrees
import dev.chieppa.squash.uidesign.generated.face_head_angle
import dev.chieppa.squash.uidesign.generated.face_showing
import dev.chieppa.stretch.frame.Toolbar
import dev.chieppa.stretch.frame.RowText
import dev.chieppa.stretch.ui.Chip
import dev.chieppa.stretch.ui.Slider
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import org.jetbrains.compose.resources.stringResource

private val SLIDER_WIDTH = 200.dp
private const val RIGHT_ANGLE = 90f
private const val DEGREES_TO_RADIANS = (PI / 180.0).toFloat()

/** Test tags for the Head angle control. */
object HeadAngleTags {
    const val SLIDER = "head-angle.slider"
    const val VERSION = "head-angle.version"
}

/**
 * The Head angle control over Single while a face group is picked: how far the head has turned to the view [toViewer], 0 side on to 90 ahead, which [turn] moves, keeping the side the
 * camera is on, the version the view shows, and the version a stroke writes.
 */
@Composable
fun HeadAngleBar(state: AppState, toViewer: Vec3, turn: (Vec3) -> Unit) {
    val open by state.session.collectAsState()
    val assembled = open?.assembly?.output?.collectAsState()?.value
    val picked = state.face.group ?: return
    val head = assembled?.part(picked.part) ?: return
    val angle = headAngle(head, toViewer)
    val forward = PartBuilder.axes(head.template).along
    val side = (Vec3.Y cross forward).normalized().let { if ((it dot toViewer) < 0f) it * -1f else it }
    val label = stringResource(Res.string.face_head_angle)
    val bin = FACE_BINS.binFor(angle)
    val version = stringResource(versionName(state.face.lockedVersion ?: bin))
    Toolbar {
        RowText(label)
        Slider(
            angle,
            { degrees ->
                val radians = degrees * DEGREES_TO_RADIANS
                val ahead = if ((forward dot toViewer).sign < 0f) -1f else 1f
                turn((side * cos(radians) + forward * (ahead * sin(radians))).normalized())
            },
            label,
            Modifier.width(SLIDER_WIDTH).testTag(HeadAngleTags.SLIDER),
            valueRange = 0f..RIGHT_ANGLE,
        )
        RowText(stringResource(Res.string.face_degrees, angle.roundToInt()))
        Chip(stringResource(Res.string.face_showing, version), Modifier.testTag(HeadAngleTags.VERSION))
        FaceVersionChip(state.face.version ?: bin)
    }
}
