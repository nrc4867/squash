package dev.chieppa.squash.recipe.model

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.squash.document.MOST_HALO
import dev.chieppa.squash.document.MaterialFade
import dev.chieppa.squash.document.MaterialGlow
import dev.chieppa.squash.document.MaterialPreset
import dev.chieppa.squash.document.MaterialRim
import dev.chieppa.squash.document.MaterialShine
import dev.chieppa.squash.document.ShadowShift
import dev.chieppa.squash.document.ShineShape
import dev.chieppa.squash.document.SlotMaterial
import dev.chieppa.squash.document.SlotName
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive

/** A slot's toon material in a recipe: a preset by name, or a preset with any of its settings changed. */
@Serializable
@Doc("A palette slot's toon material: a preset, and any setting changed from it. Written as the preset's name alone where nothing changes.")
data class RecipeMaterial(
    @Doc("The finish it starts from: matte (two tones, the default), skin, cloth, plastic (a sharp round spot), metal (three hard tones, a band and a bright rim), glass (see-through with a streak) or glow (its colour whatever the light, with a halo).")
    val preset: MaterialPreset = MaterialPreset.MATTE,
    @Doc("How many flat tones it shades in, 1 to 3: 1 flat, 2 lit and shadow, 3 lit, a middle tone and shadow.")
    @Range(1.0, 3.0)
    val steps: Int? = null,
    @Doc("Its own shadow tone; when left out, the shadow slot's colour, else its colour shifted darker.")
    val shadow: RecipeColor? = null,
    @Doc("A palette slot whose colour is its shadow tone.")
    val shadowSlot: String? = null,
    @Doc("How much darker its shifted shadow is, 0 to 1; the character's own depth when left out.")
    @Range(0.0, 1.0)
    val shadowDepth: Float? = null,
    @Doc("Which way its shifted shadow turns: auto (warm for a saturated colour, cool for a pale one), cool or warm.")
    val shadowShift: ShadowShift? = null,
    @Doc("A light edge along the silhouette.")
    val rim: RecipeRim? = null,
    @Doc("A shine from the key light: a spot, a band or a streak.")
    val shine: RecipeShine? = null,
    @Doc("How much of what lies behind a part or cloth in it the part hides, 0 to 1, more toward the silhouette on cloth: a lens at 0.35 shows the eyes through it. Whole when left out. On a separate part it holds for its base colour, drawn after everything whole; on cloth it multiplies each area's own.")
    @Range(0.0, 1.0)
    val seeThrough: Float? = null,
    @Doc("A glow: its colour whatever the light, its middle lifted toward white, with a halo round it.")
    val glow: RecipeGlow? = null,
    @Doc("A second palette slot fading in along a separate part painted in this one, from its root toward its tip: a claw, horn, beak or tooth darkening or paling toward its tip.")
    val fade: RecipeFade? = null,
) {
    /** This material as the document holds it, its shadow slot's name as given. */
    val slotMaterial: SlotMaterial
        get() = SlotMaterial(
            preset, steps, shadow?.slotColor, shadowSlot?.let(::SlotName), shadowDepth, shadowShift,
            rim?.let { MaterialRim(it.width, it.color?.slotColor) },
            shine?.let { MaterialShine(it.shape, it.size, it.color?.slotColor) },
            seeThrough,
            glow?.let { MaterialGlow(it.strength, it.halo) },
            fade?.let { MaterialFade(SlotName(it.slot), it.from, it.to) },
        )

    companion object {
        const val SERIAL_NAME = "dev.chieppa.squash.recipe.RecipeMaterialOrPreset"

        /** [material] as a recipe writes it. */
        fun of(material: SlotMaterial) = RecipeMaterial(
            material.preset, material.steps, material.shadow?.let(RecipeColor::of), material.shadowSlot?.value, material.shadowDepth, material.shadowShift,
            material.rim?.let { RecipeRim(it.width, it.color?.let(RecipeColor::of)) },
            material.shine?.let { RecipeShine(it.shape, it.size, it.color?.let(RecipeColor::of)) },
            material.seeThrough,
            material.glow?.let { RecipeGlow(it.strength, it.halo) },
            material.fade?.let { RecipeFade(it.slot.value, it.from, it.to) },
        )
    }
}

/** A light edge along a silhouette. */
@Serializable
@Doc("A light edge along the silhouette.")
data class RecipeRim(
    @Doc("How far in from the silhouette it reaches on the side turned from the light, 0 to 1, as a share of the way to the middle of a round form.")
    @Range(0.0, 1.0)
    val width: Float,
    @Doc("Its colour; the slot's colour lifted toward white when left out.")
    val color: RecipeColor? = null,
)

/** A shine from the key light. */
@Serializable
@Doc("A shine from the key light, where the surface faces halfway between the light and the viewer.")
data class RecipeShine(
    @Doc("Its shape: none, spot (small, sharp and round), band (broad and hard, with a darker reflection beside it) or streak (thin, across the light's way).")
    val shape: ShineShape,
    @Doc("How large it is, 0 to 1.")
    @Range(0.0, 1.0)
    val size: Float,
    @Doc("Its colour; near white in the slot's hue when left out.")
    val color: RecipeColor? = null,
)

/** A glow. */
@Serializable
@Doc("A glow: the slot's colour whatever the light.")
data class RecipeGlow(
    @Doc("How far its middle lifts toward white, 0 to 1.")
    @Range(0.0, 1.0)
    val strength: Float,
    @Doc("How wide a soft ring it draws round itself, in pixels; none when 0.")
    @Range(0.0, MOST_HALO.toDouble())
    val halo: Float = 0f,
)

/** A second slot fading in along a separate part. */
@Serializable
@Doc("A second palette slot fading in along a separate part, from its root toward its tip.")
data class RecipeFade(
    @Doc("The palette slot that fades in.")
    val slot: String,
    @Doc("Where along the part, 0 at its root and 1 at its tip, the fade starts: none of the slot shows before it.")
    @Range(0.0, 1.0)
    val from: Float = 0f,
    @Doc("Where along the part the fade ends: all of the slot shows past it. After from.")
    @Range(0.0, 1.0)
    val to: Float = 1f,
)

/** Reads a material as a preset's name or as an object of settings; writes the name alone when it changes nothing. */
@OptIn(ExperimentalSerializationApi::class)
object RecipeMaterialSerializer : KSerializer<RecipeMaterial> {
    override val descriptor: SerialDescriptor = SerialDescriptor(RecipeMaterial.SERIAL_NAME, RecipeMaterial.serializer().descriptor)

    private val presets = MaterialPreset.entries.associateBy { MaterialPreset.serializer().descriptor.getElementName(it.ordinal) }

    override fun deserialize(decoder: Decoder): RecipeMaterial {
        val json = decoder as JsonDecoder
        val element = json.decodeJsonElement()
        if (element is JsonPrimitive && element.isString) {
            val preset = presets[element.content] ?: throw SerializationException("A material is one of ${presets.keys.joinToString()}, or an object of settings, not \"${element.content}\"")
            return RecipeMaterial(preset)
        }
        return json.json.decodeFromJsonElement(RecipeMaterial.serializer(), element)
    }

    override fun serialize(encoder: Encoder, value: RecipeMaterial) {
        val json = encoder as JsonEncoder
        if (value == RecipeMaterial(value.preset)) {
            json.encodeJsonElement(JsonPrimitive(MaterialPreset.serializer().descriptor.getElementName(value.preset.ordinal)))
        } else {
            json.encodeSerializableValue(RecipeMaterial.serializer(), value)
        }
    }
}
