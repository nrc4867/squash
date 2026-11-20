package dev.chieppa.squash.character.hair

import dev.chieppa.squash.document.LockGroup
import dev.chieppa.squash.document.LockGroupId
import dev.chieppa.squash.document.LockGroupKind
import dev.chieppa.squash.document.LockPhysics
import dev.chieppa.squash.document.LockSection
import dev.chieppa.squash.document.LockTip
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SlotName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A hairstyle made of lock groups, set up in one step. */
@Serializable
enum class HairPreset {
    @SerialName("shortSpiky") SHORT_SPIKY,
    @SerialName("bob") BOB,
    @SerialName("longStraight") LONG_STRAIGHT,
    @SerialName("ponytail") PONYTAIL,
    @SerialName("sideSwept") SIDE_SWEPT,
    @SerialName("curly") CURLY,
}

/**
 * One group of a preset, its sizes as shares of the head's reach from its middle: [length], [width] and [thickness]; everything else as a
 * [LockGroup] holds it.
 */
private class PresetGroup(
    val name: String,
    val kind: LockGroupKind,
    val count: Int,
    val length: Float,
    val width: Float,
    val thickness: Float,
    val rise: Float,
    val fall: Float,
    val taper: Float = 0.35f,
    val section: LockSection = LockSection.CLUMP,
    val tip: LockTip = LockTip.POINTED,
    val sweep: Float = 0f,
    val curl: Float = 0f,
    val wave: Float = 0f,
    val twist: Float = 0f,
    val spread: Float = 1f,
    val height: Float = 0f,
    val around: Float = 0f,
    val layer: Int = 0,
    val lengthJitter: Float = 0.2f,
    val tie: Float = 0f,
    val banded: Boolean = false,
    val seed: Int = 1,
    val physics: LockPhysics = LockPhysics(),
)

private val SOFT = LockPhysics(stiffness = 0.35f, damping = 0.3f, angleLimit = 70f)
private val SPRINGY = LockPhysics(stiffness = 0.85f, damping = 0.4f, angleLimit = 25f, neighbours = 0.15f)
private val SWINGING = LockPhysics(stiffness = 0.25f, damping = 0.2f, weight = 1.4f, angleLimit = 90f, neighbours = 0.5f)

private val PRESETS: Map<HairPreset, List<PresetGroup>> = mapOf(
    HairPreset.SHORT_SPIKY to listOf(
        PresetGroup("back", LockGroupKind.BACK, 14, 0.5f, 0.42f, 0.15f, rise = 8f, fall = 0.35f, taper = 0.1f, physics = SPRINGY),
        PresetGroup("left", LockGroupKind.LEFT, 6, 0.42f, 0.38f, 0.14f, rise = 5f, fall = 0.55f, taper = 0.1f, physics = SPRINGY),
        PresetGroup("right", LockGroupKind.RIGHT, 6, 0.42f, 0.38f, 0.14f, rise = 5f, fall = 0.55f, taper = 0.1f, physics = SPRINGY),
        PresetGroup("crown", LockGroupKind.CROWN, 12, 0.6f, 0.42f, 0.15f, rise = 16f, fall = 0.3f, taper = 0.1f, layer = 1, spread = 1.7f, physics = SPRINGY),
        PresetGroup("fringe", LockGroupKind.FRINGE, 7, 0.75f, 0.38f, 0.14f, rise = 8f, fall = 0.5f, taper = 0.1f, sweep = 0.1f, layer = 1, physics = SPRINGY),
        PresetGroup("strays", LockGroupKind.STRAYS, 5, 0.42f, 0.32f, 0.12f, rise = 22f, fall = 0.2f, taper = 0.08f, layer = 2, seed = 3, physics = SPRINGY),
    ),
    HairPreset.BOB to listOf(
        PresetGroup("back", LockGroupKind.BACK, 14, 1.45f, 0.55f, 0.14f, rise = 4f, fall = 0.85f, taper = 0.75f, section = LockSection.RIBBON, tip = LockTip.FLICKED, physics = SOFT),
        PresetGroup("left", LockGroupKind.LEFT, 8, 1.4f, 0.55f, 0.14f, rise = 4f, fall = 0.85f, taper = 0.75f, section = LockSection.RIBBON, tip = LockTip.FLICKED, physics = SOFT),
        PresetGroup("right", LockGroupKind.RIGHT, 8, 1.4f, 0.55f, 0.14f, rise = 4f, fall = 0.85f, taper = 0.75f, section = LockSection.RIBBON, tip = LockTip.FLICKED, physics = SOFT),
        PresetGroup("crown", LockGroupKind.CROWN, 9, 1.0f, 0.55f, 0.14f, rise = 2f, fall = 0.6f, taper = 0.6f, section = LockSection.RIBBON, layer = 1, spread = 1.3f, physics = SOFT),
        PresetGroup("fringe", LockGroupKind.FRINGE, 9, 0.65f, 0.42f, 0.12f, rise = 6f, fall = 0.75f, taper = 0.4f, section = LockSection.RIBBON, layer = 1, physics = SOFT),
    ),
    HairPreset.LONG_STRAIGHT to listOf(
        PresetGroup("back", LockGroupKind.BACK, 14, 3.6f, 0.5f, 0.14f, rise = 5f, fall = 0.95f, taper = 0.45f, section = LockSection.RIBBON, physics = SWINGING),
        PresetGroup("left", LockGroupKind.LEFT, 6, 3.2f, 0.48f, 0.14f, rise = 5f, fall = 0.95f, taper = 0.45f, section = LockSection.RIBBON, physics = SWINGING),
        PresetGroup("right", LockGroupKind.RIGHT, 6, 3.2f, 0.48f, 0.14f, rise = 5f, fall = 0.95f, taper = 0.45f, section = LockSection.RIBBON, physics = SWINGING),
        PresetGroup("crown", LockGroupKind.CROWN, 8, 1.0f, 0.5f, 0.14f, rise = 4f, fall = 0.7f, taper = 0.6f, section = LockSection.RIBBON, layer = 1, spread = 1.3f, physics = SOFT),
        PresetGroup("fringe", LockGroupKind.FRINGE, 7, 0.9f, 0.36f, 0.12f, rise = 10f, fall = 0.7f, taper = 0.35f, section = LockSection.RIBBON, layer = 1, physics = SOFT),
    ),
    HairPreset.PONYTAIL to listOf(
        PresetGroup("tail", LockGroupKind.BACK, 9, 2.6f, 0.4f, 0.16f, rise = 35f, fall = 0.8f, taper = 0.3f, spread = 0.35f, height = -0.6f, tie = 1f, banded = true, layer = 1, physics = SWINGING),
        PresetGroup("left", LockGroupKind.LEFT, 3, 0.55f, 0.3f, 0.1f, rise = 5f, fall = 0.85f, taper = 0.3f, height = 0.4f, physics = SOFT),
        PresetGroup("right", LockGroupKind.RIGHT, 3, 0.55f, 0.3f, 0.1f, rise = 5f, fall = 0.85f, taper = 0.3f, height = 0.4f, physics = SOFT),
        PresetGroup("fringe", LockGroupKind.FRINGE, 7, 0.7f, 0.34f, 0.12f, rise = 10f, fall = 0.6f, taper = 0.3f, layer = 1, physics = SOFT),
    ),
    HairPreset.SIDE_SWEPT to listOf(
        PresetGroup("back", LockGroupKind.BACK, 11, 0.7f, 0.46f, 0.14f, rise = 10f, fall = 0.7f, taper = 0.3f, physics = SOFT),
        PresetGroup("left", LockGroupKind.LEFT, 5, 0.6f, 0.4f, 0.13f, rise = 8f, fall = 0.75f, taper = 0.3f, physics = SOFT),
        PresetGroup("right", LockGroupKind.RIGHT, 5, 0.6f, 0.4f, 0.13f, rise = 8f, fall = 0.75f, taper = 0.3f, physics = SOFT),
        PresetGroup("crown", LockGroupKind.CROWN, 8, 0.8f, 0.46f, 0.14f, rise = 8f, fall = 0.5f, taper = 0.3f, layer = 1, spread = 1.3f, physics = SOFT),
        PresetGroup("fringe", LockGroupKind.FRINGE, 7, 1.1f, 0.4f, 0.13f, rise = 15f, fall = 0.5f, taper = 0.25f, sweep = -0.75f, around = 10f, layer = 2, lengthJitter = 0.3f, physics = SOFT),
    ),
    HairPreset.CURLY to listOf(
        PresetGroup("back", LockGroupKind.BACK, 14, 1.3f, 0.32f, 0.26f, rise = 8f, fall = 0.45f, taper = 0.8f, tip = LockTip.ROUND, curl = -0.85f, physics = SPRINGY),
        PresetGroup("left", LockGroupKind.LEFT, 6, 1.2f, 0.3f, 0.24f, rise = 8f, fall = 0.45f, taper = 0.8f, tip = LockTip.ROUND, curl = -0.85f, physics = SPRINGY),
        PresetGroup("right", LockGroupKind.RIGHT, 6, 1.2f, 0.3f, 0.24f, rise = 8f, fall = 0.45f, taper = 0.8f, tip = LockTip.ROUND, curl = -0.85f, physics = SPRINGY),
        PresetGroup("crown", LockGroupKind.CROWN, 10, 1.2f, 0.32f, 0.26f, rise = 10f, fall = 0.4f, taper = 0.8f, tip = LockTip.ROUND, curl = -0.85f, layer = 1, spread = 1.6f, physics = SPRINGY),
        PresetGroup("fringe", LockGroupKind.FRINGE, 7, 1.0f, 0.3f, 0.24f, rise = 8f, fall = 0.45f, taper = 0.8f, tip = LockTip.ROUND, curl = -0.85f, layer = 1, physics = SPRINGY),
    ),
)

/** The names of [preset]'s groups, which are their ids with the prefix a character gives them. */
fun presetGroupNames(preset: HairPreset): List<String> = PRESETS.getValue(preset).map { it.name }

/**
 * The lock groups of [preset] on head [on], their roots on [mass] where there is one, sized to a head reaching [reach] from its middle,
 * coloured [color] and a tie's band [band], each group's id [prefix] and its name.
 */
fun presetGroups(preset: HairPreset, on: PartId, mass: PartId?, reach: Float, prefix: String = "", color: SlotName? = null, band: SlotName? = null): List<LockGroup> =
    PRESETS.getValue(preset).map { g ->
        LockGroup(
            id = LockGroupId(prefix + g.name),
            kind = g.kind,
            on = on,
            mass = mass,
            count = g.count,
            spread = g.spread,
            around = g.around,
            height = g.height,
            length = (g.length * reach).coerceIn(0.005f, 3f),
            lengthJitter = g.lengthJitter,
            width = (g.width * reach).coerceIn(0.002f, 0.5f),
            thickness = (g.thickness * reach).coerceIn(0.002f, 0.5f),
            taper = g.taper,
            section = g.section,
            tip = g.tip,
            rise = g.rise,
            sweep = g.sweep,
            fall = g.fall,
            curl = g.curl,
            wave = g.wave,
            twist = g.twist,
            seed = g.seed,
            layer = g.layer,
            tie = g.tie,
            band = if (g.banded) band ?: color else null,
            color = color,
            physics = g.physics,
        )
    }
