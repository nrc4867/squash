package dev.chieppa.squash.screens.surface.rig

import androidx.compose.runtime.Composable
import dev.chieppa.squash.character.catalog.partName
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.rig.BoneName
import dev.chieppa.squash.rig.Rig
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.rig_bone_name
import dev.chieppa.squash.uidesign.generated.rig_seam_name
import dev.chieppa.squash.uidesign.generated.rig_side_left
import dev.chieppa.squash.uidesign.generated.rig_side_right
import org.jetbrains.compose.resources.stringResource

/**
 * How the Rig stage names parts, bones and seams: Front leg L, Front leg L · knee, Hand L · Index 2, Torso ↔ Front leg L. A joint
 * reads as its template names it, a digit's as [joint] names it.
 */
class RigNames(
    private val part: (PartId) -> String,
    private val bone: String,
    private val seam: String,
    private val joint: (BoneName) -> String? = { null },
    private val digit: (PartId, String) -> String? = { _, _ -> null },
) {
    fun of(id: PartId): String = part(id)

    fun of(name: BoneName): String = bone.replace(FIRST, part(name.part)).replace(SECOND, joint(name))

    /** The bone [name] by its joint alone: knee, Index 2. */
    fun joint(name: BoneName): String = joint.invoke(name) ?: name.joint

    /** The digit [patch] of [part] by its name, Index, or the patch's own name when the rig has no bone on it. */
    fun digit(part: PartId, patch: String): String = digit.invoke(part, patch) ?: patch

    fun seam(parent: PartId, child: PartId): String = seam.replace(FIRST, part(parent)).replace(SECOND, part(child))

    private companion object {
        const val FIRST = "\u0001"
        const val SECOND = "\u0002"
    }
}

/** The Rig stage's names for [character]'s parts, by type, with L or R on a part that sits on one side, and for [rig]'s digits. */
@Composable
fun rigNames(character: Character, templates: TemplateFolder, rig: Rig? = null): RigNames {
    val digits = digitNames()
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
    }, bone, seam, { name -> rig?.let { digits.of(it, name) } }, { part, patch -> rig?.let { digits.digit(it, part, patch) } })
}
