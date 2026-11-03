package dev.chieppa.squash.character.load

import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.character.model.PartTemplate
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

private const val PART_GLOB = "*.part.json"
private const val PLAN_GLOB = "*.plan.json"
private const val PLANS_FOLDER = "plans"

/** Everything in a templates folder. */
data class TemplateFolder(val parts: List<PartTemplate>, val plans: List<BodyPlan>) {
    companion object {
        /** Loads every `*.part.json` in [folder] and every `*.plan.json` in its plans folder, in name order. */
        fun load(folder: Path): TemplateFolder {
            val parts = folder.listDirectoryEntries(PART_GLOB).sortedBy { it.name }
                .map { TemplateLoader.loadPart(it.name, it.readText()) }
            val ids = parts.map { it.id }.toSet()
            val plans = folder.resolve(PLANS_FOLDER).listDirectoryEntries(PLAN_GLOB).sortedBy { it.name }
                .map { TemplateLoader.loadPlan(it.name, it.readText(), ids) }
            return TemplateFolder(parts, plans)
        }
    }
}
