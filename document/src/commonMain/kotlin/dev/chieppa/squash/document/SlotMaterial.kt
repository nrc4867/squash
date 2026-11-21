package dev.chieppa.squash.document

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The fewest and most flat tones a material shades in. */
const val LEAST_STEPS = 1
const val MOST_STEPS = 3

/** The widest halo a glow draws, in pixels. */
const val MOST_HALO = 32f

/** A toon finish a palette slot starts from, before any setting of its own. */
@Serializable
enum class MaterialPreset {
    /** Two tones; most surfaces. */
    @SerialName("matte")
    MATTE,

    /** Two tones; a body's base slot. */
    @SerialName("skin")
    SKIN,

    /** Two tones; a garment's slot. */
    @SerialName("cloth")
    CLOTH,

    /** Two tones and one sharp round spot from the light. */
    @SerialName("plastic")
    PLASTIC,

    /** Three hard tones, a high-contrast band from the light and a bright rim. */
    @SerialName("metal")
    METAL,

    /** See-through, with a streak from the light. */
    @SerialName("glass")
    GLASS,

    /** Its colour whatever the light, with a halo round it. */
    @SerialName("glow")
    GLOW,
}

/** Which way a slot's shadow tone shifts from its colour: by its saturation, warm when saturated and cool when pale, or always one way. */
@Serializable
enum class ShadowShift {
    @SerialName("auto")
    AUTO,

    @SerialName("cool")
    COOL,

    @SerialName("warm")
    WARM,
}

/** The shape a shine from the light takes on a surface. */
@Serializable
enum class ShineShape {
    @SerialName("none")
    NONE,

    /** A small sharp round spot where the surface faces halfway between the light and the viewer. */
    @SerialName("spot")
    SPOT,

    /** A broad hard band through that place, stretched across the light, bordered by a darker reflection. */
    @SerialName("band")
    BAND,

    /** A thin hard streak through that place, across the light's way. */
    @SerialName("streak")
    STREAK,
}

/**
 * A light edge along the silhouette on the side turned from the light: [width], 0 to 1, how far in from the silhouette it reaches as a
 * share of the way to the middle of a round form; [color], the slot's colour lifted toward white where null.
 */
@Serializable
data class MaterialRim(val width: Float, val color: SlotColor? = null) {
    init {
        require(width in 0f..1f) { "A rim's width runs 0 to 1, not $width" }
    }
}

/** A shine from the light: its [shape], its [size], 0 to 1, and its [color], near white in the slot's hue where null. */
@Serializable
data class MaterialShine(val shape: ShineShape, val size: Float, val color: SlotColor? = null) {
    init {
        require(size in 0f..1f) { "A shine's size runs 0 to 1, not $size" }
    }
}

/** A glow: its [strength], 0 to 1, how far its middle lifts toward white, and its [halo], how wide a soft ring it draws round itself, in pixels. */
@Serializable
data class MaterialGlow(val strength: Float, val halo: Float = 0f) {
    init {
        require(strength in 0f..1f) { "A glow's strength runs 0 to 1, not $strength" }
        require(halo in 0f..MOST_HALO) { "A glow's halo runs 0 to $MOST_HALO pixels, not $halo" }
    }
}

/**
 * A palette slot's toon material: its [preset] and any setting it changes from it, each null where the preset's holds. [steps], 1 to 3,
 * is how many flat tones it shades in. Its shadow is [shadow], a colour of its own, else [shadowSlot]'s colour, else its colour shifted
 * [shadowDepth] darker by [shadowShift]. [rim] is a light edge on the silhouette, [shine] a shine from the light, [seeThrough], 0 to 1,
 * how much of what lies behind a part or cloth in it the part hides, and [glow] a glow that ignores the light.
 */
@Serializable
data class SlotMaterial(
    val preset: MaterialPreset = MaterialPreset.MATTE,
    val steps: Int? = null,
    val shadow: SlotColor? = null,
    val shadowSlot: SlotName? = null,
    val shadowDepth: Float? = null,
    val shadowShift: ShadowShift? = null,
    val rim: MaterialRim? = null,
    val shine: MaterialShine? = null,
    val seeThrough: Float? = null,
    val glow: MaterialGlow? = null,
) {
    init {
        require(steps == null || steps in LEAST_STEPS..MOST_STEPS) { "A material shades in $LEAST_STEPS to $MOST_STEPS tones, not $steps" }
        require(shadowDepth == null || shadowDepth in 0f..1f) { "A shadow's depth runs 0 to 1, not $shadowDepth" }
        require(seeThrough == null || seeThrough in 0f..1f) { "A see-through opacity runs 0 to 1, not $seeThrough" }
    }

    /** Every setting, the preset's where this sets none. */
    val look: MaterialLook
        get() {
            val base = preset.look
            return MaterialLook(
                steps = steps ?: base.steps,
                shadow = shadow ?: base.shadow,
                shadowSlot = shadowSlot ?: base.shadowSlot,
                shadowDepth = shadowDepth ?: base.shadowDepth,
                shadowShift = shadowShift ?: base.shadowShift,
                rim = rim ?: base.rim,
                shine = shine ?: base.shine,
                seeThrough = seeThrough ?: base.seeThrough,
                glow = glow ?: base.glow,
            )
        }

    /** Whether this sets nothing but its preset, so a save need hold only the preset's name. */
    val presetOnly: Boolean get() = this == SlotMaterial(preset)
}

/** Each preset's settings. Matte, skin and cloth shade as a slot with no material does. */
val MaterialPreset.look: MaterialLook
    get() = when (this) {
        MaterialPreset.MATTE, MaterialPreset.SKIN, MaterialPreset.CLOTH -> MaterialLook()
        MaterialPreset.PLASTIC -> MaterialLook(shine = MaterialShine(ShineShape.SPOT, PLASTIC_SPOT))
        MaterialPreset.METAL -> MaterialLook(steps = MOST_STEPS, shadowDepth = METAL_DEPTH, shine = MaterialShine(ShineShape.BAND, METAL_BAND), rim = MaterialRim(METAL_RIM))
        MaterialPreset.GLASS -> MaterialLook(shine = MaterialShine(ShineShape.STREAK, GLASS_STREAK), seeThrough = GLASS_OPACITY, rim = MaterialRim(GLASS_RIM))
        MaterialPreset.GLOW -> MaterialLook(steps = LEAST_STEPS, glow = MaterialGlow(GLOW_STRENGTH, GLOW_HALO))
    }

private const val PLASTIC_SPOT = 0.38f
private const val METAL_DEPTH = 0.45f
private const val METAL_BAND = 0.4f
private const val METAL_RIM = 0.1f
private const val GLASS_STREAK = 0.25f
private const val GLASS_OPACITY = 0.3f
private const val GLASS_RIM = 0.08f
private const val GLOW_STRENGTH = 0.6f
private const val GLOW_HALO = 16f

/**
 * Every setting of a material as it draws (see [SlotMaterial]); [shadowDepth] null takes the character's shading's depth, and a [rim],
 * [shine], [seeThrough] or [glow] that is null is not drawn.
 */
data class MaterialLook(
    val steps: Int = 2,
    val shadow: SlotColor? = null,
    val shadowSlot: SlotName? = null,
    val shadowDepth: Float? = null,
    val shadowShift: ShadowShift = ShadowShift.AUTO,
    val rim: MaterialRim? = null,
    val shine: MaterialShine? = null,
    val seeThrough: Float? = null,
    val glow: MaterialGlow? = null,
)
