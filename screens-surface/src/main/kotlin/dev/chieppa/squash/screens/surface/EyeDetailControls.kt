package dev.chieppa.squash.screens.surface

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.EyeLook
import dev.chieppa.squash.document.MOST_CLUMPS
import dev.chieppa.squash.document.PaintedEye
import dev.chieppa.squash.document.SetPaintedEye
import dev.chieppa.squash.document.styled
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.face_detail_clump_length
import dev.chieppa.squash.uidesign.generated.face_detail_clump_width
import dev.chieppa.squash.uidesign.generated.face_detail_clumps
import dev.chieppa.squash.uidesign.generated.face_detail_flat_top
import dev.chieppa.squash.uidesign.generated.face_detail_flick
import dev.chieppa.squash.uidesign.generated.face_detail_flick_lift
import dev.chieppa.squash.uidesign.generated.face_detail_iris_light
import dev.chieppa.squash.uidesign.generated.face_detail_iris_rim
import dev.chieppa.squash.uidesign.generated.face_detail_iris_shade
import dev.chieppa.squash.uidesign.generated.face_detail_lid_shadow
import dev.chieppa.squash.uidesign.generated.face_detail_lid_weight
import dev.chieppa.squash.uidesign.generated.face_detail_lower_lid
import dev.chieppa.squash.uidesign.generated.face_detail_lower_reach
import dev.chieppa.squash.uidesign.generated.face_detail_outer_corner
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.Segmented
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** How far the flick slider reaches past the back corner, in the opening's half widths. */
private val FLICK_RANGE = 0f..1.5f

/** How far the flick's tip moves, in the opening's heights: below the corner to well above it. */
private val FLICK_LIFT_RANGE = -1f..2f

/** How long a lash clump may be, in the opening's heights. */
private val CLUMP_LENGTH_RANGE = 0f..2f

/** How thick a lash may be at its root, in the opening's heights. */
private val CLUMP_WIDTH_RANGE = 0.01f..0.3f

/** How deep the lid's shadow may reach, a share of the opening's height. */
private val SHADOW_RANGE = 0f..0.5f

/** How wide the iris's rim may be, a share of its half width. */
private val RIM_RANGE = 0f..0.5f

/** One detail slider: its label, its test tag's name, its range, how it reads the look and how it changes it. */
private class DetailSlider(
    val label: StringResource,
    val name: String,
    val range: ClosedFloatingPointRange<Float>,
    val read: (EyeLook) -> Float,
    val write: (EyeLook, Float) -> EyeLook,
)

private val LID_SLIDERS = listOf(
    DetailSlider(Res.string.face_detail_lid_weight, "lid-weight", 0f..1f, { it.lidMass.weight }) { l, v -> l.copy(lidMass = l.lidMass.copy(weight = v)) },
    DetailSlider(Res.string.face_detail_flick, "flick", FLICK_RANGE, { it.lidMass.flick }) { l, v -> l.copy(lidMass = l.lidMass.copy(flick = v)) },
    DetailSlider(Res.string.face_detail_flick_lift, "flick-lift", FLICK_LIFT_RANGE, { it.lidMass.flickLift }) { l, v -> l.copy(lidMass = l.lidMass.copy(flickLift = v)) },
    DetailSlider(Res.string.face_detail_lower_lid, "lower-lid", 0f..1f, { it.lidMass.lower }) { l, v -> l.copy(lidMass = l.lidMass.copy(lower = v)) },
    DetailSlider(Res.string.face_detail_lower_reach, "lower-reach", 0f..1f, { it.lidMass.lowerExtent }) { l, v -> l.copy(lidMass = l.lidMass.copy(lowerExtent = v)) },
)

private val CLUMP_SLIDERS = listOf(
    DetailSlider(Res.string.face_detail_clump_length, "clump-length", CLUMP_LENGTH_RANGE, { it.lashClumps.length }) { l, v -> l.copy(lashClumps = l.lashClumps.copy(length = v)) },
    DetailSlider(Res.string.face_detail_clump_width, "clump-width", CLUMP_WIDTH_RANGE, { it.lashClumps.width }) { l, v -> l.copy(lashClumps = l.lashClumps.copy(width = v)) },
)

private val TONE_SLIDERS = listOf(
    DetailSlider(Res.string.face_detail_iris_shade, "iris-shade", 0f..1f, { it.irisShade.upper }) { l, v -> l.copy(irisShade = l.irisShade.copy(upper = v)) },
    DetailSlider(Res.string.face_detail_iris_light, "iris-light", 0f..1f, { it.irisShade.lower }) { l, v -> l.copy(irisShade = l.irisShade.copy(lower = v)) },
    DetailSlider(Res.string.face_detail_iris_rim, "iris-rim", RIM_RANGE, { it.irisShade.rim }) { l, v -> l.copy(irisShade = l.irisShade.copy(rim = v)) },
    DetailSlider(Res.string.face_detail_lid_shadow, "lid-shadow", SHADOW_RANGE, { it.lidShadow.depth }) { l, v -> l.copy(lidShadow = l.lidShadow.copy(depth = v)) },
    DetailSlider(Res.string.face_detail_flat_top, "flat-top", 0f..1f, { it.topFlatness }) { l, v -> l.copy(topFlatness = v) },
    DetailSlider(Res.string.face_detail_outer_corner, "outer-corner", 0f..1f, { it.outerCorner }) { l, v -> l.copy(outerCorner = v) },
)

/**
 * The painted eye's detail under More: its lid mass, lash clumps, iris tones, lid shadow and opening shape, each slider a drag that is one
 * step of undo, each change made to its look and its front look alike.
 */
@Composable
internal fun EyeDetailControls(session: CharacterSession, eye: PaintedEye, drag: CommandDrag) {
    DetailSliders(LID_SLIDERS, eye, drag)
    val clumps = stringResource(Res.string.face_detail_clumps)
    SettingRow(clumps) {
        Segmented(
            (0..MOST_CLUMPS).toList(),
            eye.look.lashClumps.count,
            { count -> session.send(SetPaintedEye(eye.id, eye.styled { it.copy(lashClumps = it.lashClumps.copy(count = count)) })) },
            { it.toString() },
            Modifier.weight(1f).testTag(PaintedEyeTags.detail("clumps")),
            fill = true,
        )
    }
    DetailSliders(CLUMP_SLIDERS, eye, drag)
    DetailSliders(TONE_SLIDERS, eye, drag)
}

/** Each of [sliders] over [eye]'s look, its drags sent through [drag]. */
@Composable
private fun DetailSliders(sliders: List<DetailSlider>, eye: PaintedEye, drag: CommandDrag) = sliders.forEach { slider ->
    ControlSlider(slider.label, slider.read(eye.look), PaintedEyeTags.detail(slider.name), drag::finish, slider.range) { value ->
        drag.update(SetPaintedEye(eye.id, eye.styled { slider.write(it, value) }))
    }
}
