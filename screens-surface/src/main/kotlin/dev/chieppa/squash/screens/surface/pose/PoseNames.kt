package dev.chieppa.squash.screens.surface.pose

import androidx.compose.runtime.Composable
import dev.chieppa.squash.character.catalog.partNameOn
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.rig_side_left
import dev.chieppa.squash.uidesign.generated.rig_side_right
import org.jetbrains.compose.resources.stringResource

/** How the Pose stage names a limb, by its paw where it has one: Front paw L, with the place its leg's socket names. */
@Composable
fun limbNames(character: Character, templates: TemplateFolder): (PartId) -> String {
    val left = stringResource(Res.string.rig_side_left, MARK)
    val right = stringResource(Res.string.rig_side_right, MARK)
    return { id ->
        val part = character.part(id)
        val name = part?.let { partNameOn(it, it.socket?.parent?.let(character::part), templates) } ?: id.value
        when (part?.side) {
            Side.LEFT -> left.replace(MARK, name)
            Side.RIGHT -> right.replace(MARK, name)
            null -> name
        }
    }
}

private const val MARK = "\u0001"
