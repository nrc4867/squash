package dev.chieppa.squash.screens.surface.rig

import androidx.compose.runtime.Composable
import dev.chieppa.squash.character.catalog.partName
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.rig.BoneName
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.rig_bone_name
import dev.chieppa.squash.uidesign.generated.rig_seam_name
import dev.chieppa.squash.uidesign.generated.rig_side_left
import dev.chieppa.squash.uidesign.generated.rig_side_right
import org.jetbrains.compose.resources.stringResource

/** How the Rig stage names parts, bones and seams: Front leg L, Front leg L · knee, Torso ↔ Front leg L. */
class RigNames(private val part: (PartId) -> String, private val bone: String, private val seam: String) {
    fun of(id: PartId): String = part(id)

    fun of(name: BoneName): String = bone.replace(FIRST, part(name.part)).replace(SECOND, name.joint)

    fun seam(parent: PartId, child: PartId): String = seam.replace(FIRST, part(parent)).replace(SECOND, part(child))

    private companion object {
        const val FIRST = "\u0001"
        const val SECOND = "\u0002"
    }
}

/** The Rig stage's names for [character]'s parts, by type, with L or R on a part that sits on one side. */
@Composable
fun rigNames(character: Character, templates: TemplateFolder): RigNames {
    val left = stringResource(Res.string.rig_side_left, "\u0001")
    val right = stringResource(Res.string.rig_side_right, "\u0001")
    val bone = stringResource(Res.string.rig_bone_name, "\u0001", "\u0002")
    val seam = stringResource(Res.string.rig_seam_name, "\u0001", "\u0002")
    return RigNames({ id ->
        val part = character.part(id)
        val name = part?.let { partName(it, templates) } ?: id.value
        when (part?.side) {
            Side.LEFT -> left.replace("\u0001", name)
            Side.RIGHT -> right.replace("\u0001", name)
            null -> name
        }
    }, bone, seam)
}
