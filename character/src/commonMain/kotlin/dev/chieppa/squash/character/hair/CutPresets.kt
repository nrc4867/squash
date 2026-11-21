package dev.chieppa.squash.character.hair

import dev.chieppa.squash.document.HairShell
import dev.chieppa.squash.document.LockGroupKind
import dev.chieppa.squash.document.LockPhysics
import dev.chieppa.squash.document.LockSection
import dev.chieppa.squash.document.LockTip
import dev.chieppa.squash.document.LockTrim
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.ShellFade

private val BRUSH = LockPhysics(stiffness = 0.95f, damping = 0.5f, angleLimit = 12f, neighbours = 0.2f)
private val SOFT = LockPhysics(stiffness = 0.35f, damping = 0.3f, angleLimit = 70f)
private val SWINGING = LockPhysics(stiffness = 0.25f, damping = 0.2f, weight = 1.4f, angleLimit = 90f, neighbours = 0.5f)
private val PLAITED = LockPhysics(stiffness = 0.45f, damping = 0.3f, weight = 1.6f, angleLimit = 70f, neighbours = 0f)
private val COILED = LockPhysics(stiffness = 0.9f, damping = 0.5f, angleLimit = 15f, neighbours = 0.6f)

/** The cut a flat top's brush is trimmed to: level over the top of the head. */
private val FLAT = LockTrim(level = 1.32f, blunt = true)

/** A blunt bob's hem at the jaw, a little longer at the back, and its bangs cut straight above the brows. */
private val BOB_HEM = LockTrim(level = -0.62f, tilt = 6f)
private val BANGS = LockTrim(level = 0.42f, tilt = 0f)

/** The short-hair presets and those built from braids and buns, laid out as [presetLayout] gives them. */
internal val CUT_PRESETS: Map<HairPreset, List<PresetGroup>> = mapOf(
    HairPreset.BUZZ_CUT to emptyList(),
    HairPreset.FADE to emptyList(),
    HairPreset.FLAT_TOP to listOf(
        PresetGroup("top", LockGroupKind.TOP, 22, 1.3f, 0.34f, 0.22f, rise = 0f, fall = 0f, taper = 0.95f, tip = LockTip.ROUND, spread = 1.4f, stand = 0.85f, trim = FLAT, flow = 0f, lengthJitter = 0f, physics = BRUSH),
        PresetGroup("crown", LockGroupKind.CROWN, 14, 1.3f, 0.34f, 0.22f, rise = 0f, fall = 0f, taper = 0.95f, tip = LockTip.ROUND, spread = 1.3f, stand = 0.85f, trim = FLAT, flow = 0f, lengthJitter = 0f, layer = 1, physics = BRUSH),
        PresetGroup("fringe", LockGroupKind.FRINGE, 10, 1.2f, 0.32f, 0.22f, rise = 0f, fall = 0f, taper = 0.95f, tip = LockTip.ROUND, spread = 1f, height = 0.6f, stand = 0.8f, trim = FLAT, flow = 0f, lengthJitter = 0f, layer = 1, physics = BRUSH),
        PresetGroup("left", LockGroupKind.LEFT, 7, 1.3f, 0.34f, 0.22f, rise = 0f, fall = 0f, taper = 0.95f, tip = LockTip.ROUND, height = 1f, stand = 0.9f, trim = FLAT, flow = 0f, lengthJitter = 0f, physics = BRUSH),
        PresetGroup("right", LockGroupKind.RIGHT, 7, 1.3f, 0.34f, 0.22f, rise = 0f, fall = 0f, taper = 0.95f, tip = LockTip.ROUND, height = 1f, stand = 0.9f, trim = FLAT, flow = 0f, lengthJitter = 0f, physics = BRUSH),
        PresetGroup("back", LockGroupKind.BACK, 9, 1.3f, 0.34f, 0.22f, rise = 0f, fall = 0f, taper = 0.95f, tip = LockTip.ROUND, height = 1f, spread = 0.8f, stand = 0.9f, trim = FLAT, flow = 0f, lengthJitter = 0f, physics = BRUSH),
    ),
    HairPreset.UNDERCUT to listOf(
        PresetGroup("top", LockGroupKind.TOP, 16, 1.3f, 0.55f, 0.13f, rise = 4f, fall = 0.4f, taper = 0.4f, spread = 0.9f, height = 0.4f, physics = SOFT),
        PresetGroup("crown", LockGroupKind.CROWN, 10, 1.0f, 0.55f, 0.13f, rise = 2f, fall = 0.5f, taper = 0.4f, layer = 1, spread = 1.05f, physics = SOFT),
        PresetGroup("fringe", LockGroupKind.FRINGE, 5, 1.0f, 0.5f, 0.13f, rise = 6f, fall = 0.5f, taper = 0.35f, sweep = -0.2f, spread = 0.4f, height = 1f, layer = 2, physics = SOFT),
    ),
    HairPreset.BLUNT_BOB to listOf(
        PresetGroup("back", LockGroupKind.BACK, 10, 2.2f, 0.85f, 0.12f, rise = 0f, fall = 0.9f, taper = 0.9f, section = LockSection.RIBBON, tip = LockTip.ROUND, spread = 1.15f, lengthJitter = 0f, trim = BOB_HEM, physics = SOFT),
        PresetGroup("left", LockGroupKind.LEFT, 5, 2f, 0.8f, 0.12f, rise = 0f, fall = 0.9f, taper = 0.9f, section = LockSection.RIBBON, tip = LockTip.ROUND, lengthJitter = 0f, trim = BOB_HEM, physics = SOFT),
        PresetGroup("right", LockGroupKind.RIGHT, 5, 2f, 0.8f, 0.12f, rise = 0f, fall = 0.9f, taper = 0.9f, section = LockSection.RIBBON, tip = LockTip.ROUND, lengthJitter = 0f, trim = BOB_HEM, physics = SOFT),
        PresetGroup("fringe", LockGroupKind.FRINGE, 8, 1.2f, 0.52f, 0.1f, rise = 0f, fall = 0.7f, taper = 0.9f, section = LockSection.RIBBON, tip = LockTip.ROUND, layer = 1, lengthJitter = 0f, trim = BANGS, physics = SOFT),
    ),
    HairPreset.BRAID to listOf(
        PresetGroup("braid", LockGroupKind.BACK, 1, 2.6f, 0.62f, 0.46f, rise = 4f, fall = 1f, taper = 0.55f, section = LockSection.BRAID, spread = 0f, height = -0.5f, tie = 1f, banded = true, lengthJitter = 0f, flow = 0f, physics = PLAITED),
    ),
    HairPreset.TWIN_BRAIDS to listOf(
        PresetGroup("braid-l", LockGroupKind.BACK, 1, 2.2f, 0.5f, 0.38f, rise = 15f, fall = 1f, taper = 0.55f, section = LockSection.BRAID, spread = 0f, around = -62f, height = -0.7f, tie = 1f, banded = true, lengthJitter = 0f, flow = 0f, physics = PLAITED),
        PresetGroup("braid-r", LockGroupKind.BACK, 1, 2.2f, 0.5f, 0.38f, rise = 15f, fall = 1f, taper = 0.55f, section = LockSection.BRAID, spread = 0f, around = 62f, height = -0.7f, tie = 1f, banded = true, lengthJitter = 0f, flow = 0f, physics = PLAITED),
    ),
    HairPreset.BUN to listOf(
        PresetGroup("bun", LockGroupKind.CROWN, 4, 1.9f, 0.75f, 0.5f, rise = 70f, fall = 0f, taper = 0.85f, tip = LockTip.ROUND, spread = 0f, height = -0.3f, tie = 1f, lengthJitter = 0f, flow = 0f, coil = 1.2f, physics = COILED),
    ),
    HairPreset.SPACE_BUNS to listOf(
        PresetGroup("bun-l", LockGroupKind.LEFT, 4, 1.3f, 0.6f, 0.42f, rise = 85f, fall = 0f, taper = 0.85f, tip = LockTip.ROUND, spread = 0f, height = 1f, tie = 1f, lengthJitter = 0f, flow = 0f, coil = 1.2f, physics = COILED),
        PresetGroup("bun-r", LockGroupKind.RIGHT, 4, 1.3f, 0.6f, 0.42f, rise = 85f, fall = 0f, taper = 0.85f, tip = LockTip.ROUND, spread = 0f, height = 1f, tie = 1f, lengthJitter = 0f, flow = 0f, coil = 1.2f, physics = COILED),
    ),
)

/** The presets whose locks lie over a fitted inner mass: every one but those whose sides are cut short, which lie on a shell instead. */
fun presetMassed(preset: HairPreset): Boolean = preset !in setOf(HairPreset.BUZZ_CUT, HairPreset.FADE, HairPreset.FLAT_TOP, HairPreset.UNDERCUT)

/**
 * The short-hair shell [preset] lays over head [on], or null: a close buzz for a buzz cut, a high skin fade for a fade, faded sides under
 * a flat top's brush, and shaved sides and back under an undercut's long top.
 */
fun presetShell(preset: HairPreset, on: PartId): HairShell? = when (preset) {
    HairPreset.BUZZ_CUT -> HairShell(on, thickness = 0.014f)
    HairPreset.FADE -> HairShell(on, thickness = 0.02f, fade = ShellFade.HIGH)
    HairPreset.FLAT_TOP -> HairShell(on, thickness = 0.016f, fade = ShellFade.MID, fadeTo = 0.15f)
    HairPreset.UNDERCUT -> HairShell(on, thickness = 0.008f, shade = 0.45f)
    else -> null
}
