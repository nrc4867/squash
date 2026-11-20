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
 * reading if none. [shadow] is the slot's own shadow tone; with none, the look shifts [color] warm for its shadow. [opacity] is how much of
 * what lies behind a part coloured in it the part hides, 0 to 1; whole with none.
 */
@Serializable
data class PaletteSlot(val name: SlotName, val color: SlotColor, val shadow: SlotColor? = null, val label: String? = null, val opacity: Float? = null) {
    init {
        require(opacity == null || opacity in 0f..1f) { "A slot's opacity runs 0 to 1, not $opacity" }
    }
}

/** A character's named colors, in the order they are listed. */
@Serializable
data class Palette(val slots: List<PaletteSlot> = emptyList()) {
    init {
        require(slots.size == slots.distinctBy { it.name }.size) { "A palette names a slot twice: ${slots.map { it.name.value }}" }
    }

    /** The color of slot [name], or of the blend it names ([blendedSlot]), or null if no slot holds that name. */
    fun color(name: SlotName): SlotColor? = slots.firstOrNull { it.name == name }?.color ?: blendOf(name)?.let { (a, b, share) -> mixed(color(a), color(b), share) }

    /** How much of what lies behind it a part coloured in slot [name] hides: its opacity, or 1 when it has none or no slot holds that name. */
    fun opacity(name: SlotName): Float = slots.firstOrNull { it.name == name }?.opacity ?: blendOf(name)?.let { opacity(it.first) } ?: 1f

    /** The shadow tone slot [name] carries of its own, or null if it has none or no slot holds that name; a blend's is its slots' shadows blended. */
    fun shadow(name: SlotName): SlotColor? = slots.firstOrNull { it.name == name }?.shadow
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
