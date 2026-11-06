package dev.chieppa.squash.screens.shell

import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.readDocument
import dev.chieppa.stretch.document.DocumentFormat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.encoding.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val EXTENSION = "squash"
private const val CHARACTER_ENTRY = "character.json"
private const val IMAGES_FOLDER = "images/"
private const val REFERENCES_KEY = "references"
private const val IMAGES_KEY = "images"
private const val ID_KEY = "id"
private const val DATA_KEY = "data"
private const val UNKNOWN_EXTENSION = "bin"

/** The first bytes of a zip file, which a package is. */
private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

/** Image files by the bytes they start with, and the extension each is kept under. */
private val IMAGE_MAGIC: List<Pair<ByteArray, String>> = listOf(
    byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47) to "png",
    byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) to "jpg",
    byteArrayOf(0x47, 0x49, 0x46) to "gif",
    byteArrayOf(0x42, 0x4D) to "bmp",
    byteArrayOf(0x52, 0x49, 0x46, 0x46) to "webp",
)

private val json = Json { prettyPrint = true }

/**
 * A Squash file: a zip holding the document as `character.json`, with each reference image beside it under `images/` in its own
 * encoding, so a file carries everything it shows. A plain JSON document, as older saves and the examples are, reads too.
 */
class SquashFormat(override val description: String = "") : DocumentFormat<SquashDocument> {
    override val extension = EXTENSION

    override fun encode(document: SquashDocument): ByteArray {
        val tree = json.encodeToJsonElement(SquashDocument.serializer(), document).jsonObject
        val files = LinkedHashMap<String, ByteArray>()
        val written = withImages(tree) { image ->
            val bytes = Base64.decode(image.getValue(DATA_KEY).jsonPrimitive.content)
            files[imageEntry(image.getValue(ID_KEY).jsonPrimitive.content, bytes)] = bytes
            JsonObject(image + (DATA_KEY to JsonPrimitive("")))
        }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(CHARACTER_ENTRY))
            zip.write(json.encodeToString(JsonElement.serializer(), written).encodeToByteArray())
            zip.closeEntry()
            for ((name, bytes) in files) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    override fun decode(bytes: ByteArray): SquashDocument {
        if (!bytes.startsWith(ZIP_MAGIC)) return readDocument(bytes.decodeToString())
        val entries = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry -> entries[entry.name] = zip.readBytes() }
        }
        val text = requireNotNull(entries[CHARACTER_ENTRY]) { "This file holds no $CHARACTER_ENTRY" }.decodeToString()
        val tree = Json.parseToJsonElement(text).jsonObject
        val filled = withImages(tree) { image ->
            val id = image.getValue(ID_KEY).jsonPrimitive.content
            val name = entries.keys.firstOrNull { it.startsWith("$IMAGES_FOLDER$id.") }
            val data = requireNotNull(name?.let(entries::get)) { "This file is missing the image $id" }
            JsonObject(image + (DATA_KEY to JsonPrimitive(Base64.encode(data))))
        }
        return readDocument(json.encodeToString(JsonElement.serializer(), filled))
    }

    /** [document] as plain JSON with its images inline, as the example characters are kept. */
    fun plainText(document: SquashDocument): String = json.encodeToString(SquashDocument.serializer(), document)
}

/** [document] with each reference image changed by [change]. */
private fun withImages(document: JsonObject, change: (JsonObject) -> JsonObject): JsonObject {
    val references = document[REFERENCES_KEY] as? JsonObject ?: return document
    val images = references[IMAGES_KEY] as? JsonArray ?: return document
    val changed = JsonArray(images.jsonArray.map { change(it.jsonObject) })
    return JsonObject(document + (REFERENCES_KEY to JsonObject(references + (IMAGES_KEY to changed))))
}

/** The entry an image's [bytes] are kept under, named for [id] and the kind of file they are. */
private fun imageEntry(id: String, bytes: ByteArray): String =
    "$IMAGES_FOLDER$id.${IMAGE_MAGIC.firstOrNull { (magic, _) -> bytes.startsWith(magic) }?.second ?: UNKNOWN_EXTENSION}"

private fun ByteArray.startsWith(prefix: ByteArray): Boolean = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
