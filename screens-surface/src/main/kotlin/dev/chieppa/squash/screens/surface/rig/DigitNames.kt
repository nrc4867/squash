package dev.chieppa.squash.screens.surface.rig

import androidx.compose.runtime.Composable
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.rig.BoneName
import dev.chieppa.squash.rig.DigitPlace
import dev.chieppa.squash.rig.Rig
import dev.chieppa.squash.rig.isUpright
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.rig_digit_joint
import dev.chieppa.squash.uidesign.generated.rig_finger_index
import dev.chieppa.squash.uidesign.generated.rig_finger_little
import dev.chieppa.squash.uidesign.generated.rig_finger_middle
import dev.chieppa.squash.uidesign.generated.rig_finger_n
import dev.chieppa.squash.uidesign.generated.rig_finger_ring
import dev.chieppa.squash.uidesign.generated.rig_finger_thumb
import dev.chieppa.squash.uidesign.generated.rig_toe_fifth
import dev.chieppa.squash.uidesign.generated.rig_toe_first
import dev.chieppa.squash.uidesign.generated.rig_toe_fourth
import dev.chieppa.squash.uidesign.generated.rig_toe_hind
import dev.chieppa.squash.uidesign.generated.rig_toe_n
import dev.chieppa.squash.uidesign.generated.rig_toe_second
import dev.chieppa.squash.uidesign.generated.rig_toe_third
import org.jetbrains.compose.resources.stringResource

private const val FIRST = "\u0001"
private const val SECOND = "\u0002"

/**
 * How the Rig stage and Pose name a digit and its bones: on an upright character's hand of five, Thumb, Index, Middle, Ring and
 * Little, on any other upright hand Finger 1 on; elsewhere, a four-legged character's front paws among them, First toe to Fifth toe, then Toe 6 on, and a digit turned back Hind toe; a bone is its digit and its
 * joint's number from the root, Index 2.
 */
class DigitNames(
    private val fingers: List<String>,
    private val finger: String,
    private val toes: List<String>,
    private val toe: String,
    private val hind: String,
    private val joint: String,
) {
    /** The name of the digit [place] is on, on a part whose digits are [fingered] or else toes, in a row of [rowSize] digits. */
    fun digit(place: DigitPlace, fingered: Boolean, rowSize: Int): String = when {
        place.back -> hind
        fingered && rowSize == fingers.size -> fingers[place.index]
        fingered -> finger.replace(FIRST, (place.index + 1).toString())
        place.index < toes.size -> toes[place.index]
        else -> toe.replace(FIRST, (place.index + 1).toString())
    }

    /** The name of the digit [patch] on [part] in [rig], or null when no bone of the rig lies on it. */
    fun digit(rig: Rig, part: PartId, patch: String): String? {
        val place = rig.bones.firstOrNull { it.name.part == part && it.digit?.patch == patch }?.digit ?: return null
        return digit(place, fingered(rig, part), rowSize(rig, part, patch))
    }

    /** The bone [name]'s name when it lies on a digit, Index 2, or null for a bone of its part's own chain. */
    fun of(rig: Rig, name: BoneName): String? {
        val place = rig.bone(name)?.digit ?: return null
        val digit = digit(place, fingered(rig, name.part), rowSize(rig, name.part, place.patch))
        return joint.replace(FIRST, digit).replace(SECOND, place.joint.toString())
    }

    /** Whether [part]'s digits are fingers: a hand on an upright character. */
    private fun fingered(rig: Rig, part: PartId): Boolean = rig.part(part)?.role == Role.HAND && rig.isUpright

    private fun rowSize(rig: Rig, part: PartId, patch: String): Int {
        val row = patch.substringBefore(ROW_SEPARATOR)
        return rig.bones.mapNotNull { bone -> bone.digit?.patch?.takeIf { bone.name.part == part && it.substringBefore(ROW_SEPARATOR) == row } }.distinct().size
    }

    private companion object {
        const val ROW_SEPARATOR = ":"
    }
}

/** The digit names in the user's language. */
@Composable
fun digitNames(): DigitNames = DigitNames(
    listOf(Res.string.rig_finger_thumb, Res.string.rig_finger_index, Res.string.rig_finger_middle, Res.string.rig_finger_ring, Res.string.rig_finger_little).map { stringResource(it) },
    stringResource(Res.string.rig_finger_n, FIRST),
    listOf(Res.string.rig_toe_first, Res.string.rig_toe_second, Res.string.rig_toe_third, Res.string.rig_toe_fourth, Res.string.rig_toe_fifth).map { stringResource(it) },
    stringResource(Res.string.rig_toe_n, FIRST),
    stringResource(Res.string.rig_toe_hind),
    stringResource(Res.string.rig_digit_joint, FIRST, SECOND),
)
