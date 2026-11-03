package dev.chieppa.squash.document

import kotlinx.serialization.Serializable

const val DOCUMENT_FORMAT = 1

@Serializable
data class Character(
    val id: CharacterId,
    val name: String,
    val bodyPlan: BodyPlanRef,
    val parts: List<PartInstance> = emptyList(),
) {
    fun part(id: PartId): PartInstance? = parts.firstOrNull { it.id == id }
}

/** The whole document; [format] numbers its grammar for the migration chain. */
@Serializable
data class SquashDocument(val character: Character, val format: Int = DOCUMENT_FORMAT)
