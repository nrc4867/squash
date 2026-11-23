package dev.chieppa.squash.character.keys

import dev.chieppa.squash.character.build.KeptSurfaces
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.AssemblyPart
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.buildSurfaces
import dev.chieppa.squash.character.surface.RunAll
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
 * the parts no key changes are built once and kept for every key's field. Given [surfaces] and [solids] that outlive it, a part another
 * character built the same way, as the one before an edit, is not built again, and an unchanged run keeps its solid, so its members read
 * as the same members.
 */
class KeyedFields(
    private val character: Character,
    private val templates: TemplateLibrary,
    private val surfaces: KeptSurfaces = KeptSurfaces(),
    private val solids: RunSolids = RunSolids(),
) {
    private val plan = templates.plan(character.bodyPlan)

    /** The character's field with no change. */
    val base: CharacterField? by lazy { of(emptyList()) }

    /**
     * The blend [id]'s join takes as the character rests, as the build gives a part with none of its own: what a key's blend scale
     * scales for such a part.
     */
    private fun builtBlend(id: PartId): Float? {
        val field = base ?: return null
        val run = field.runs.indexOfFirst { it.part.id == id && it.first }
        return field.field.members.getOrNull(run)?.blend?.takeIf { it > 0f }
    }

    /**
     * Builds, all at once by [runAll], every part surface the fields with no change and with each of [changes] read over the parts take
     * that are not kept yet, so [base] and [of] then build none.
     */
    suspend fun prepare(changes: List<List<PartChange>>, runAll: RunAll) {
        buildSurfaces((listOf(emptyList<PartChange>()) + changes).map { assembly(it, blends = false) }, plan, templates, surfaces, runAll)
    }

    /** The character's field with [changes] read over its parts' own settings; a part with no blend of its own scales its built one. */
    fun of(changes: List<PartChange>): CharacterField? {
        val (parts, data) = assembly(changes, blends = true)
        return fieldOf(assembleNow(parts, data, plan, templates, surfaces), solids)
    }

    /** The parts with [changes] read over them, their blends scaled only with [blends], and the data each part reads. */
    private fun assembly(changes: List<PartChange>, blends: Boolean): Pair<List<AssemblyPart>, (PartId) -> PartData> {
        val byPart = changes.associateBy { it.part }
        val parts = assemblyParts(character).map { part ->
            val change = byPart[part.id] ?: return@map part
            if (!blends) return@map part
            val own = part.blend ?: if (change.blend != 1f) builtBlend(part.id) else null
            part.copy(blend = own?.let { it * change.blend })
        }
        fun data(id: PartId): PartData {
            val own = character.resolved(id) ?: PartData()
            return byPart[id]?.let { keyed(own, it) } ?: own
        }
        return parts to ::data
    }
}
