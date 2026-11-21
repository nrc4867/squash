package dev.chieppa.squash.screens.surface

import dev.chieppa.squash.document.TeethLevel
import dev.chieppa.squash.document.TongueLevel
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.face_teeth_level_bar
import dev.chieppa.squash.uidesign.generated.face_teeth_level_blocks
import dev.chieppa.squash.uidesign.generated.face_teeth_level_none
import dev.chieppa.squash.uidesign.generated.face_teeth_preset_bird
import dev.chieppa.squash.uidesign.generated.face_teeth_preset_boar
import dev.chieppa.squash.uidesign.generated.face_teeth_preset_cat
import dev.chieppa.squash.uidesign.generated.face_teeth_preset_cow
import dev.chieppa.squash.uidesign.generated.face_teeth_preset_crocodile
import dev.chieppa.squash.uidesign.generated.face_teeth_preset_dog
import dev.chieppa.squash.uidesign.generated.face_teeth_preset_dragon
import dev.chieppa.squash.uidesign.generated.face_teeth_preset_frog
import dev.chieppa.squash.uidesign.generated.face_teeth_preset_horse
import dev.chieppa.squash.uidesign.generated.face_teeth_preset_human
import dev.chieppa.squash.uidesign.generated.face_teeth_preset_rodent
import dev.chieppa.squash.uidesign.generated.face_teeth_preset_shark
import dev.chieppa.squash.uidesign.generated.face_teeth_preset_toon_simple
import dev.chieppa.squash.uidesign.generated.face_teeth_preset_viper
import dev.chieppa.squash.uidesign.generated.face_tongue_flat
import dev.chieppa.squash.uidesign.generated.face_tongue_none
import org.jetbrains.compose.resources.StringResource

/** Each shipped teeth preset's name, by id; a preset not here shows the name its file gives. */
internal val TEETH_PRESET_NAMES: Map<String, StringResource> = mapOf(
    "toon-simple" to Res.string.face_teeth_preset_toon_simple,
    "human" to Res.string.face_teeth_preset_human,
    "rodent" to Res.string.face_teeth_preset_rodent,
    "cat" to Res.string.face_teeth_preset_cat,
    "dog" to Res.string.face_teeth_preset_dog,
    "crocodile" to Res.string.face_teeth_preset_crocodile,
    "dragon" to Res.string.face_teeth_preset_dragon,
    "shark" to Res.string.face_teeth_preset_shark,
    "viper" to Res.string.face_teeth_preset_viper,
    "horse" to Res.string.face_teeth_preset_horse,
    "cow" to Res.string.face_teeth_preset_cow,
    "boar" to Res.string.face_teeth_preset_boar,
    "bird" to Res.string.face_teeth_preset_bird,
    "frog" to Res.string.face_teeth_preset_frog,
)

internal val TEETH_LEVEL_NAMES: Map<TeethLevel, StringResource> = mapOf(
    TeethLevel.NONE to Res.string.face_teeth_level_none,
    TeethLevel.BAR to Res.string.face_teeth_level_bar,
    TeethLevel.BLOCKS to Res.string.face_teeth_level_blocks,
)

internal val TONGUE_LEVEL_NAMES: Map<TongueLevel, StringResource> = mapOf(
    TongueLevel.NONE to Res.string.face_tongue_none,
    TongueLevel.FLAT to Res.string.face_tongue_flat,
)
