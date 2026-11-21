package dev.chieppa.squash.document

import kotlinx.serialization.Serializable

/** An sRGB color, each channel 0 to 1. */
@Serializable
data class SlotColor(val red: Float, val green: Float, val blue: Float) {
    init {
        require(red in 0f..1f && green in 0f..1f && blue in 0f..1f) { "A color's channels run 0 to 1, not ($red, $green, $blue)" }
    }
}

/**
 * A named color. [name] is what the character's parts refer to it by and never changes; [label] is what the person calls it, the name's own
 * reading if none. [material] is the toon finish anything painted in it takes: its shadow tone, its shine, its see-through and its glow;
 * with none, matte, which shades as the look's own two tones.
 */
@Serializable
data class PaletteSlot(val name: SlotName, val color: SlotColor, val label: String? = null, val material: SlotMaterial? = null)

/** A character's named colors, in the order they are listed. */
@Serializable
data class Palette(val slots: List<PaletteSlot> = emptyList()) {
    init {
        require(slots.size == slots.distinctBy { it.name }.size) { "A palette names a slot twice: ${slots.map { it.name.value }}" }
    }

    /** The color of slot [name], or of the blend it names ([blendedSlot]), or null if no slot holds that name. */
    fun color(name: SlotName): SlotColor? = slots.firstOrNull { it.name == name }?.color ?: blendOf(name)?.let { (a, b, share) -> mixed(color(a), color(b), share) }

    /** The material slot [name] sets, or null where it sets none or no slot holds that name; a blend's is its first slot's. */
    fun material(name: SlotName): SlotMaterial? = slots.firstOrNull { it.name == name }?.material ?: blendOf(name)?.let { material(it.first) }

    /** How slot [name]'s material draws, the matte preset's where it sets none. */
    fun look(name: SlotName): MaterialLook = material(name)?.look ?: MaterialLook()

    /** How much of what lies behind it a part coloured in slot [name] hides: its material's see-through, or 1 when it has none or no slot holds that name. */
    fun opacity(name: SlotName): Float = look(name).seeThrough ?: 1f

    /**
     * The shadow tone slot [name]'s material gives it, its own colour or its shadow slot's, or null if it gives none or no slot holds that
     * name; a blend's is its slots' shadows blended.
     */
    fun shadow(name: SlotName): SlotColor? = slots.firstOrNull { it.name == name }?.material?.look?.let { it.shadow ?: it.shadowSlot?.takeIf { other -> other != name }?.let(::color) }
        ?: blendOf(name)?.let { (a, b, share) -> shadow(a)?.let { sa -> shadow(b)?.let { sb -> mixed(sa, sb, share) } } }
}

private const val BLEND_TOWARD = ">"
private const val BLEND_SHARE = "@"

/** A slot name that colours as [base]'s colour carried [share] of the way, 0 to 1, toward [toward]'s, with no slot of its own. */
fun blendedSlot(base: SlotName, toward: SlotName, share: Float): SlotName {
    require(share in 0f..1f) { "A blend's share runs 0 to 1, not $share" }
    return SlotName("${base.value}$BLEND_TOWARD${toward.value}$BLEND_SHARE$share")
}

/** The two slots and share [name] blends, or null when it names no blend. */
private fun blendOf(name: SlotName): Triple<SlotName, SlotName, Float>? {
    val toward = name.value.indexOf(BLEND_TOWARD)
    val at = name.value.lastIndexOf(BLEND_SHARE)
    if (toward <= 0 || at <= toward + 1) return null
    val share = name.value.substring(at + 1).toFloatOrNull()?.takeIf { it in 0f..1f } ?: return null
    return Triple(SlotName(name.value.substring(0, toward)), SlotName(name.value.substring(toward + 1, at)), share)
}

private fun mixed(a: SlotColor?, b: SlotColor?, share: Float): SlotColor? {
    if (a == null || b == null) return a ?: b
    return SlotColor(a.red + (b.red - a.red) * share, a.green + (b.green - a.green) * share, a.blue + (b.blue - a.blue) * share)
}
