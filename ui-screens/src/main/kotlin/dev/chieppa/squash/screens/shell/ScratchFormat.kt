package dev.chieppa.squash.screens.shell

import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.stretch.document.DocumentFormat
import kotlinx.serialization.json.Json

private const val EXTENSION = "json"

private val json = Json { prettyPrint = true }

/** The scratch save: the serialized document as JSON, standing in until there is a project format. */
class ScratchFormat(override val description: String = "") : DocumentFormat<SquashDocument> {
    override val extension = EXTENSION

    override fun encode(document: SquashDocument): ByteArray =
        json.encodeToString(SquashDocument.serializer(), document).encodeToByteArray()

    override fun decode(bytes: ByteArray): SquashDocument =
        json.decodeFromString(SquashDocument.serializer(), bytes.decodeToString())
}
