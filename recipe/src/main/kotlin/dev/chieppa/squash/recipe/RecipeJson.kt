package dev.chieppa.squash.recipe

import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.recipe.model.Recipe
import dev.chieppa.squash.recipe.model.RecipeColor
import dev.chieppa.squash.recipe.model.RecipeExpression
import dev.chieppa.squash.recipe.model.RecipeEye
import dev.chieppa.squash.recipe.model.RecipeGarment
import dev.chieppa.squash.recipe.model.RecipeMaterialSerializer
import dev.chieppa.squash.recipe.model.RecipePart
import dev.chieppa.squash.recipe.model.RecipeSlot
import dev.chieppa.squash.recipe.model.RecipeTarget
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Keys a recipe may carry for its author's own notes, which the build reads past. */
private const val NOTE_PREFIX = "x-"

/** The first `$`-rooted JSON path in a parser's message. */
private val PATH = Regex("""\$[\w.\[\]\-$]*""")

/** Where in the text a parser's message says it stopped, which a recipe error leaves out for its path. */
private val OFFSET = Regex("""Unexpected JSON token at offset \d+: """)

/** Reads and writes recipe files. */
object RecipeJson {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        allowComments = true
        allowTrailingComma = true
        explicitNulls = false
    }

    /** The recipe in [text]; a [RecipeException] naming the path of what is wrong if it is not one. */
    fun read(text: String): Recipe {
        val tree = try {
            json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw RecipeException("$", "not JSON: ${firstLine(e)}")
        }
        val kept = withoutNotes(tree)
        return try {
            json.decodeFromString(Recipe.serializer(), kept.toString())
        } catch (e: SerializationException) {
            val line = firstLine(e)
            val path = PATH.find(line)?.value ?: locate(kept)
            throw RecipeException(path, line.replace(Regex(""" at (path|element):? \$\S*"""), "").replace(OFFSET, "").trim())
        } catch (e: IllegalArgumentException) {
            throw RecipeException(locate(kept), e.message ?: e.toString())
        }
    }

    /** The targets in [text], a JSON list of them as `kit sheet measure` writes; a [RecipeException] when it is not one. */
    fun readTargets(text: String): List<RecipeTarget> = try {
        json.decodeFromString(ListSerializer(RecipeTarget.serializer()), text)
    } catch (e: SerializationException) {
        throw RecipeException("$", "not a list of targets: ${firstLine(e)}")
    } catch (e: IllegalArgumentException) {
        throw RecipeException("$", "not a list of targets: ${e.message}")
    }

    /**
     * Where in [recipe] the value the reader refused lies, for a refusal that does not say: the first list entry of the recipe that fails
     * alone, read down to a palette slot's colour, or `$` when none does.
     */
    private fun locate(recipe: JsonElement): String {
        val root = recipe as? JsonObject ?: return "$"
        val wardrobe = root["wardrobe"] as? JsonObject
        val lists: List<Triple<String, JsonElement?, KSerializer<*>>> = listOf(
            Triple("$.palette", root["palette"], RecipeSlot.serializer()),
            Triple("$.parts", root["parts"], RecipePart.serializer()),
            Triple("$.eyes", root["eyes"], RecipeEye.serializer()),
            Triple("$.expressions", root["expressions"], RecipeExpression.serializer()),
            Triple("$.displayPose", root["displayPose"], BonePose.serializer()),
            Triple("$.wardrobe.garments", wardrobe?.get("garments"), RecipeGarment.serializer()),
        )
        for ((path, list, serializer) in lists) {
            val items = list as? JsonArray ?: continue
            items.forEachIndexed { i, item ->
                if (!decodes(serializer, item)) {
                    val slot = (item as? JsonObject)?.takeIf { serializer == RecipeSlot.serializer() }
                    val colour = slot?.entries?.firstOrNull { (key, value) -> key == "color" && !decodes(RecipeColor.serializer(), value) }
                    val material = slot?.get("material")?.takeIf { !decodes(RecipeMaterialSerializer, it) }
                    return "$path[$i]" + (colour?.let { ".${it.key}" } ?: material?.let { ".material" } ?: "")
                }
            }
        }
        return "$"
    }

    private fun decodes(serializer: KSerializer<*>, element: JsonElement): Boolean =
        runCatching { json.decodeFromJsonElement(serializer, element) }.isSuccess

    /** [recipe] as a recipe file's text. */
    fun write(recipe: Recipe): String = CompactJson.print(json.encodeToJsonElement(Recipe.serializer(), recipe)) + "\n"

    private fun firstLine(e: Exception) = (e.message ?: e.toString()).lineSequence().first()

    private fun withoutNotes(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.filterKeys { !it.startsWith(NOTE_PREFIX) }.mapValues { (_, v) -> withoutNotes(v) })
        is JsonArray -> JsonArray(element.map(::withoutNotes))
        else -> element
    }
}
