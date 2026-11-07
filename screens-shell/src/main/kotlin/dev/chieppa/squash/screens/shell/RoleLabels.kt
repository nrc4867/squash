package dev.chieppa.squash.screens.shell

import androidx.compose.runtime.Composable
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.role_arm
import dev.chieppa.squash.uidesign.generated.role_body
import dev.chieppa.squash.uidesign.generated.role_ear
import dev.chieppa.squash.uidesign.generated.role_fin
import dev.chieppa.squash.uidesign.generated.role_foot
import dev.chieppa.squash.uidesign.generated.role_front_leg
import dev.chieppa.squash.uidesign.generated.role_front_paw
import dev.chieppa.squash.uidesign.generated.role_hand
import dev.chieppa.squash.uidesign.generated.role_head
import dev.chieppa.squash.uidesign.generated.role_horn
import dev.chieppa.squash.uidesign.generated.role_jaw
import dev.chieppa.squash.uidesign.generated.role_leg
import dev.chieppa.squash.uidesign.generated.role_neck
import dev.chieppa.squash.uidesign.generated.role_other
import dev.chieppa.squash.uidesign.generated.role_spine
import dev.chieppa.squash.uidesign.generated.role_tail
import dev.chieppa.squash.uidesign.generated.role_wing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val NAMES: Map<Role, StringResource> = mapOf(
    Role.BODY to Res.string.role_body,
    Role.SPINE to Res.string.role_spine,
    Role.NECK to Res.string.role_neck,
    Role.HEAD to Res.string.role_head,
    Role.JAW to Res.string.role_jaw,
    Role.EAR to Res.string.role_ear,
    Role.ARM to Res.string.role_arm,
    Role.HAND to Res.string.role_hand,
    Role.LEG to Res.string.role_leg,
    Role.FOOT to Res.string.role_foot,
    Role.TAIL to Res.string.role_tail,
    Role.WING to Res.string.role_wing,
    Role.FIN to Res.string.role_fin,
    Role.HORN to Res.string.role_horn,
    Role.OTHER to Res.string.role_other,
)

/** On a four-legged character the arms are its front legs and the hands its front paws: the same roles, named as the body stands. */
private val FOUR_LEGGED: Map<Role, StringResource> = mapOf(Role.ARM to Res.string.role_front_leg, Role.HAND to Res.string.role_front_paw)

/** How each role reads, in the app's language, on a character that is [fourLegged] or not. */
@Composable
fun roleLabels(fourLegged: Boolean): (Role) -> String {
    val names = Role.entries.associateWith { role -> stringResource((if (fourLegged) FOUR_LEGGED[role] else null) ?: NAMES.getValue(role)) }
    return names::getValue
}
