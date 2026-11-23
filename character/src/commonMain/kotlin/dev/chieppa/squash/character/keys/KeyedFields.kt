package dev.chieppa.squash.character.keys

import dev.chieppa.squash.character.build.KeptSurfaces
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.character.surface.CharacterField
import dev.chieppa.squash.character.surface.RunSolids
import dev.chieppa.squash.character.surface.fieldOf
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartChange
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId

/**
 * The fields of one [character] as it rests and as pose keys change it, built through the real slice build and assembly with no session;
 * the parts no key changes are built once and kept for every key's field.
 */
class KeyedFields(private val character: Character, private val templates: TemplateLibrary) {
    private val surfaces = KeptSurfaces()
    private val solids = RunSolids()
    private val plan = templates.plan(character.bodyPlan)

    /** The character's field with no change. */
    val base: CharacterField? by lazy { of(emptyList()) }

    /** The character's field with [changes] read over its parts' own settings. */
    fun of(changes: List<PartChange>): CharacterField? {
        val byPart = changes.associateBy { it.part }
        val parts = assemblyParts(character).map { part ->
            val change = byPart[part.id] ?: return@map part
            part.copy(blend = part.blend?.let { it * change.blend })
        }
        fun data(id: PartId): PartData {
            val own = character.resolved(id) ?: PartData()
            return byPart[id]?.let { keyed(own, it) } ?: own
        }
        return fieldOf(assembleNow(parts, ::data, plan, templates, surfaces), solids)
    }
}
