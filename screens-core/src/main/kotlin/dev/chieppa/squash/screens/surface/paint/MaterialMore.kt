package dev.chieppa.squash.screens.surface.paint

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.LEAST_STEPS
import dev.chieppa.squash.document.MOST_HALO
import dev.chieppa.squash.document.MOST_STEPS
import dev.chieppa.squash.document.MaterialGlow
import dev.chieppa.squash.document.MaterialRim
import dev.chieppa.squash.document.MaterialShine
import dev.chieppa.squash.document.SetSlotMaterial
import dev.chieppa.squash.document.ShadowShift
import dev.chieppa.squash.document.ShineShape
import dev.chieppa.squash.document.SlotMaterial
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.screens.edit.SliderGesture
import dev.chieppa.squash.screens.sheet.objectShading
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.edit_percent
import dev.chieppa.squash.uidesign.generated.material_glow_strength
import dev.chieppa.squash.uidesign.generated.material_halo
import dev.chieppa.squash.uidesign.generated.material_more
import dev.chieppa.squash.uidesign.generated.material_px
import dev.chieppa.squash.uidesign.generated.material_rim
import dev.chieppa.squash.uidesign.generated.material_see_through
import dev.chieppa.squash.uidesign.generated.material_shadow_depth
import dev.chieppa.squash.uidesign.generated.material_shadow_shift
import dev.chieppa.squash.uidesign.generated.material_shift_auto
import dev.chieppa.squash.uidesign.generated.material_shift_cool
import dev.chieppa.squash.uidesign.generated.material_shift_warm
import dev.chieppa.squash.uidesign.generated.material_shine
import dev.chieppa.squash.uidesign.generated.material_shine_band
import dev.chieppa.squash.uidesign.generated.material_shine_none
import dev.chieppa.squash.uidesign.generated.material_shine_size
import dev.chieppa.squash.uidesign.generated.material_shine_spot
import dev.chieppa.squash.uidesign.generated.material_shine_streak
import dev.chieppa.squash.uidesign.generated.material_steps
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.ui.Segmented
import dev.chieppa.stretch.ui.Slider
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val PERCENT = 100f
private val READOUT_WIDTH = 44.dp

/** The size a shine takes when a shape is picked for a material that had none. */
private const val NEW_SHINE_SIZE = 0.3f

/**
 * [slot]'s material settings under a More fold, [open] or not: Steps, Shadow depth and shift, Rim width, Shine and its size, See-through,
 * Glow strength and Halo width. Each shows the setting as the material draws it and writes only that setting; a slider's drag is one undo step.
 */
@Composable
fun MaterialMore(session: CharacterSession, character: Character, slot: SlotName, open: Boolean, onOpen: (Boolean) -> Unit) {
    val material = shownMaterial(character, slot)
    val look = material.look
    val gesture = remember(session) { SliderGesture(session) }
    DisposableEffect(gesture) { onDispose { gesture.done() } }
    fun set(next: SlotMaterial) = session.send(SetSlotMaterial(slot, next))
    fun drag(next: SlotMaterial) = gesture.change(SetSlotMaterial(slot, next))
    Box(Modifier.testTag(PaintTags.MATERIAL_MORE)) {
        DrawerSection(stringResource(Res.string.material_more), divider = false, collapsed = !open, onCollapsedChange = { onOpen(!it) }) {
            SettingRow(stringResource(Res.string.material_steps)) {
                Segmented((LEAST_STEPS..MOST_STEPS).toList(), look.steps, { set(material.copy(steps = it)) }, { it.toString() }, Modifier.weight(1f).testTag(PaintTags.MATERIAL_STEPS), fill = true)
            }
            val depth = look.shadowDepth ?: character.shading.objectShading().shadowDepth
            MaterialSlider(Res.string.material_shadow_depth, depth, PaintTags.MATERIAL_SHADOW_DEPTH, gesture) { drag(material.copy(shadowDepth = it)) }
            val shifts = mapOf(
                ShadowShift.AUTO to stringResource(Res.string.material_shift_auto),
                ShadowShift.COOL to stringResource(Res.string.material_shift_cool),
                ShadowShift.WARM to stringResource(Res.string.material_shift_warm),
            )
            SettingRow(stringResource(Res.string.material_shadow_shift)) {
                Segmented(ShadowShift.entries, look.shadowShift, { set(material.copy(shadowShift = it)) }, shifts::getValue, Modifier.weight(1f).testTag(PaintTags.MATERIAL_SHADOW_SHIFT), fill = true)
            }
            MaterialSlider(Res.string.material_rim, look.rim?.width ?: 0f, PaintTags.MATERIAL_RIM, gesture) { drag(material.copy(rim = MaterialRim(it, look.rim?.color))) }
            ShineRows(look.shine, gesture, ::set, ::drag, material)
            MaterialSlider(Res.string.material_see_through, 1f - (look.seeThrough ?: 1f), PaintTags.MATERIAL_SEE_THROUGH, gesture) { drag(material.copy(seeThrough = 1f - it)) }
            val strength = look.glow?.strength ?: 0f
            val halo = look.glow?.halo ?: 0f
            MaterialSlider(Res.string.material_glow_strength, strength, PaintTags.MATERIAL_GLOW, gesture) { drag(material.copy(glow = MaterialGlow(it, halo))) }
            MaterialSlider(Res.string.material_halo, halo, PaintTags.MATERIAL_HALO, gesture, 0f..MOST_HALO, stringResource(Res.string.material_px, halo.roundToInt())) {
                drag(material.copy(glow = MaterialGlow(strength, it)))
            }
        }
    }
}

@Composable
private fun ShineRows(shine: MaterialShine?, gesture: SliderGesture, set: (SlotMaterial) -> Unit, drag: (SlotMaterial) -> Unit, material: SlotMaterial) {
    val shapes = mapOf(
        ShineShape.NONE to stringResource(Res.string.material_shine_none),
        ShineShape.SPOT to stringResource(Res.string.material_shine_spot),
        ShineShape.BAND to stringResource(Res.string.material_shine_band),
        ShineShape.STREAK to stringResource(Res.string.material_shine_streak),
    )
    val shape = shine?.shape ?: ShineShape.NONE
    val size = shine?.size ?: 0f
    SettingRow(stringResource(Res.string.material_shine)) {
        Segmented(ShineShape.entries, shape, { picked ->
            set(material.copy(shine = MaterialShine(picked, shine?.size?.takeIf { it > 0f } ?: NEW_SHINE_SIZE, shine?.color)))
        }, shapes::getValue, Modifier.weight(1f).testTag(PaintTags.MATERIAL_SHINE), fill = true)
    }
    MaterialSlider(Res.string.material_shine_size, size, PaintTags.MATERIAL_SHINE_SIZE, gesture) { drag(material.copy(shine = MaterialShine(shape, it, shine?.color))) }
}

/** A setting's slider over [range], one undo step a drag, with its value read out as [readout], a percentage where none is given. */
@Composable
private fun MaterialSlider(
    label: StringResource,
    value: Float,
    tag: String,
    gesture: SliderGesture,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    readout: String = stringResource(Res.string.edit_percent, (value * PERCENT).roundToInt()),
    onChange: (Float) -> Unit,
) {
    val name = stringResource(label)
    SettingRow(name) {
        Slider(value, { onChange(it.coerceIn(range)) }, name, Modifier.weight(1f).testTag(tag), range, gesture::done)
        BasicText(readout, Modifier.width(READOUT_WIDTH), style = Stretch.type.value.merge(TextStyle(color = Stretch.colors.inkMuted)), maxLines = 1)
    }
}
