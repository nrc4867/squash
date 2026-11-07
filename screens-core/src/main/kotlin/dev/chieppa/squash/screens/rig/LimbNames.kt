package dev.chieppa.squash.screens.rig

import androidx.compose.runtime.Composable
import dev.chieppa.squash.character.catalog.partNameOn
import dev.chieppa.squash.character.catalog.roles
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.rig_side_left
import dev.chieppa.squash.uidesign.generated.rig_side_right
import dev.chieppa.squash.uidesign.generated.role_left_arm
import dev.chieppa.squash.uidesign.generated.role_left_foot
import dev.chieppa.squash.uidesign.generated.role_left_hand
import dev.chieppa.squash.uidesign.generated.role_left_leg
import dev.chieppa.squash.uidesign.generated.role_right_arm
import dev.chieppa.squash.uidesign.generated.role_right_foot
import dev.chieppa.squash.uidesign.generated.role_right_hand
import dev.chieppa.squash.uidesign.generated.role_right_leg
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** How an upright character's limb ends and limbs read by role and side: Left foot, Right hand. */
private val SIDED: Map<Pair<Role, Side>, StringResource> = mapOf(
    (Role.ARM to Side.LEFT) to Res.string.role_left_arm, (Role.ARM to Side.RIGHT) to Res.string.role_right_arm,
    (Role.HAND to Side.LEFT) to Res.string.role_left_hand, (Role.HAND to Side.RIGHT) to Res.string.role_right_hand,
    (Role.LEG to Side.LEFT) to Res.string.role_left_leg, (Role.LEG to Side.RIGHT) to Res.string.role_right_leg,
    (Role.FOOT to Side.LEFT) to Res.string.role_left_foot, (Role.FOOT to Side.RIGHT) to Res.string.role_right_foot,
)

/**
 * How the Pose stage names a limb, by its paw where it has one. On an upright character it reads by its role and side, Left foot;
 * otherwise by its type in the place its leg's socket names, with its side after, Front paw L.
 */
@Composable
fun limbNames(character: Character, templates: TemplateFolder): (PartId) -> String {
    val left = stringResource(Res.string.rig_side_left, MARK)
    val right = stringResource(Res.string.rig_side_right, MARK)
    val roles = templates.roles(character)
    val sided = SIDED.mapValues { stringResource(it.value) }
    val upright = roles.upright
    return { id ->
        val part = character.part(id)
        val byRole = part?.side?.takeIf { upright }?.let { side -> sided[roles.resolved(part) to side] }
        val name = part?.let { partNameOn(it, it.socket?.parent?.let(character::part), templates) } ?: id.value
        byRole ?: when (part?.side) {
            Side.LEFT -> left.replace(MARK, name)
            Side.RIGHT -> right.replace(MARK, name)
            null -> name
        }
    }
}

private const val MARK = "\u0001"
