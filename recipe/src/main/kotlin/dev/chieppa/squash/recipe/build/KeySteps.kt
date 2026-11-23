package dev.chieppa.squash.recipe.build

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.PoseKey
import dev.chieppa.squash.document.SetPoseKeys
import dev.chieppa.squash.recipe.model.Recipe

/** [recipe] with each key's outline fitted ([fittedKey], posed by [bodies] when given) and given as the changes it asks for, beside how each was fitted. */
internal fun fittedKeys(recipe: Recipe, templates: TemplateFolder, bodies: KeyBodies?): Pair<Recipe, List<KeyReport>> {
    if (recipe.keys.none { it.outline != null }) return recipe to emptyList()
    val library = templates.library()
    val reports = mutableListOf<KeyReport>()
    val keys = recipe.keys.mapIndexed { k, key ->
        val outline = key.outline ?: return@mapIndexed key
        val report = fittedKey(recipe, key, outline, "$.keys[$k].outline", templates, library, bodies)
        reports += report
        val given = key.changes.map { it.part }.toSet()
        key.copy(outline = null, changes = report.changes.filter { it.part !in given } + key.changes)
    }
    return recipe.copy(keys = keys) to reports
}

/** Sends the recipe's keys as the character's pose keys, each change read as the document's at its path. */
internal fun BuildContext.poseKeys() {
    val keys = recipe.keys.mapIndexed { k, key ->
        val path = "$.keys[$k]"
        if (key.outline != null) fail("$path.outline", "a key's outline is fitted before the build sends it")
        val pose = pose("$path.pose", key.pose)
        val changes = key.changes.flatMapIndexed { c, change ->
            val at = "$path.changes[$c]"
            partChanges(recipe, change, idsOf("$at.part", change.part).all, at)
        }
        guard(path) { PoseKey(key.name, pose.bones, changes) }
    }
    send("$.keys", guard("$.keys") { SetPoseKeys(keys) })
}
