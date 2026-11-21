package dev.chieppa.squash.screens.surface.paint

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.MaterialPreset
import dev.chieppa.squash.document.SetSlotMaterial
import dev.chieppa.squash.document.SlotMaterial
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.material_cloth
import dev.chieppa.squash.uidesign.generated.material_glass
import dev.chieppa.squash.uidesign.generated.material_glow
import dev.chieppa.squash.uidesign.generated.material_matte
import dev.chieppa.squash.uidesign.generated.material_metal
import dev.chieppa.squash.uidesign.generated.material_plastic
import dev.chieppa.squash.uidesign.generated.material_skin
import dev.chieppa.stretch.ui.IconButton
import dev.chieppa.stretch.ui.Tooltip
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val PRESET_GAP = 2.dp

/** [slot]'s material in [character], or its [defaultPreset] with no setting of its own. */
fun shownMaterial(character: Character, slot: SlotName): SlotMaterial =
    character.palette.material(slot) ?: SlotMaterial(defaultPreset(character, slot))

/** The seven presets as icon buttons, the one [slot] shows on; picking one sets [slot]'s material to it alone. */
@Composable
fun MaterialPresets(session: CharacterSession, character: Character, slot: SlotName, modifier: Modifier = Modifier) {
    val stored = character.palette.material(slot)
    val shown = shownMaterial(character, slot)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(PRESET_GAP)) {
        MaterialPreset.entries.forEach { preset ->
            val name = stringResource(preset.label)
            Tooltip(name) {
                IconButton(preset.icon, name, {
                    val picked = SlotMaterial(preset)
                    if (stored != picked) session.send(SetSlotMaterial(slot, picked))
                }, Modifier.testTag(PaintTags.materialPreset(preset.name.lowercase())), on = preset == shown.preset)
            }
        }
    }
}

/** The preset's name. */
val MaterialPreset.label: StringResource
    get() = when (this) {
        MaterialPreset.MATTE -> Res.string.material_matte
        MaterialPreset.SKIN -> Res.string.material_skin
        MaterialPreset.CLOTH -> Res.string.material_cloth
        MaterialPreset.PLASTIC -> Res.string.material_plastic
        MaterialPreset.METAL -> Res.string.material_metal
        MaterialPreset.GLASS -> Res.string.material_glass
        MaterialPreset.GLOW -> Res.string.material_glow
    }

/** The preset's icon. */
val MaterialPreset.icon: ImageVector
    get() = when (this) {
        MaterialPreset.MATTE -> SquashIcons.MaterialMatte
        MaterialPreset.SKIN -> SquashIcons.MaterialSkin
        MaterialPreset.CLOTH -> SquashIcons.MaterialCloth
        MaterialPreset.PLASTIC -> SquashIcons.MaterialPlastic
        MaterialPreset.METAL -> SquashIcons.MaterialMetal
        MaterialPreset.GLASS -> SquashIcons.MaterialGlass
        MaterialPreset.GLOW -> SquashIcons.MaterialGlow
    }
