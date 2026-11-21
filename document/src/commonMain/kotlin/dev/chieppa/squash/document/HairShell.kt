package dev.chieppa.squash.document

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How high up the sides and back a short-hair shell's tone fades into the skin. */
@Serializable
enum class ShellFade {
    @SerialName("none") NONE,
    @SerialName("low") LOW,
    @SerialName("mid") MID,
    @SerialName("high") HIGH,
}

/** The lowest and highest a shell's hairline runs, in degrees up the head from level. */
const val LOWEST_HAIRLINE = -60f
const val HIGHEST_HAIRLINE = 80f

/**
 * Where a short-hair shell's hairline runs round the head, each in degrees up the head from level as the head's scalp reads it: [front]
 * across the middle of the forehead, [temples] at the corners above the outer brows, [sideburns] at the foot of the sideburns in front of
 * the ears, [ears] over the ears and [nape] at the middle of the back; it runs smoothly between them, the same on both sides.
 */
@Serializable
@Doc("Where a short-hair shell's hairline runs round the head, in degrees up the head from level, the same on both sides.")
data class Hairline(
    @Doc("Degrees up the head where it crosses the middle of the forehead.") @Range(min = -60.0, max = 80.0)
    val front: Float = 32f,
    @Doc("Degrees up the head at its corners above the outer brows: above front recedes them, below rounds them.") @Range(min = -60.0, max = 80.0)
    val temples: Float = 36f,
    @Doc("Degrees up the head at the foot of the sideburns, in front of the ears.") @Range(min = -60.0, max = 80.0)
    val sideburns: Float = -6f,
    @Doc("Degrees up the head over the ears.") @Range(min = -60.0, max = 80.0)
    val ears: Float = 8f,
    @Doc("Degrees up the head at the middle of the nape.") @Range(min = -60.0, max = 80.0)
    val nape: Float = -24f,
) {
    init {
        require(listOf(front, temples, sideburns, ears, nape).all { it in LOWEST_HAIRLINE..HIGHEST_HAIRLINE }) { "A hairline runs $LOWEST_HAIRLINE to $HIGHEST_HAIRLINE degrees up the head" }
    }
}

/**
 * Hair cut to the skin over a head ([on]): a thin shell lying [thickness] off the scalp, a share of the head's reach, inside a [hairline],
 * in a stubble [tone] (when left out, the head's colour carried [shade] of the way toward the hair's), its edge softening into the skin
 * over [soft] degrees. With a [fade] it fades into the skin up the sides and back from the hairline, low, mid or high, down to [fadeTo] of
 * its tone at the hairline. Locks may lie over it.
 */
@Serializable
@Doc("Hair cut to the skin over a head: a thin shell inside a hairline, in a stubble tone, fading into the skin up the sides and back.")
data class HairShell(
    @Doc("The head it covers, by part id.")
    val on: PartId,
    val hairline: Hairline = Hairline(),
    @Doc("How far it stands off the scalp, a share of the head's reach from its middle: about 0.01 a buzz cut.") @Range(min = 0.0, max = 0.1)
    val thickness: Float = 0.012f,
    @Doc("Its palette slot; when left out, a stubble tone: the head's colour carried shade of the way toward the hair's.")
    val tone: SlotName? = null,
    @Doc("How far the stubble tone carries the head's colour toward the hair's, 0 bare skin to 1 the hair's own.") @Range(min = 0.0, max = 1.0)
    val shade: Float = 0.6f,
    @Doc("Degrees over which its edge softens into the skin.") @Range(min = 0.0, max = 20.0)
    val soft: Float = 3f,
    @Doc("How high up the sides and back it fades into the skin: none, low (just above the ears and nape), mid (to the temples) or high (to the corners of the top).")
    val fade: ShellFade = ShellFade.NONE,
    @Doc("How much of its tone is left at the hairline under a fade, 0 bare skin to 1 none faded.") @Range(min = 0.0, max = 1.0)
    val fadeTo: Float = 0f,
) {
    init {
        require(thickness in 0f..MOST_SHELL_THICKNESS) { "A shell stands 0 to $MOST_SHELL_THICKNESS of the head's reach off it, not $thickness" }
        require(shade in 0f..1f && fadeTo in 0f..1f) { "A shell's shade and fadeTo are 0 to 1" }
        require(soft in 0f..MOST_SHELL_SOFT) { "A shell's edge softens over 0 to $MOST_SHELL_SOFT degrees, not $soft" }
    }
}

/** The most a shell stands off the scalp, a share of the head's reach. */
const val MOST_SHELL_THICKNESS = 0.1f

/** The most degrees a shell's edge softens over. */
const val MOST_SHELL_SOFT = 20f

/** The short-hair shell over [head], or null. */
fun Character.hairShellOn(head: PartId): HairShell? = hairShells.firstOrNull { it.on == head }
