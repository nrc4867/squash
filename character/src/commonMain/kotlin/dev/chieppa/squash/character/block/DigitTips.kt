package dev.chieppa.squash.character.block

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.squash.character.lint.Finding
import dev.chieppa.squash.character.lint.LintContext
import dev.chieppa.squash.document.TemplateId
import kotlinx.serialization.Serializable

/** The suffix of a digit's tip socket, after the digit's patch name. */
private const val TIP_SUFFIX = ".tip"

/**
 * A socket at the tip of every digit a toe row builds, named `<row>:<i>.tip`, lying on the digit's own surface; a part placed there
 * sinks along its own axis and rides the digit's last joint. See HLD: data-model, Tip sockets.
 */
@Serializable
@Doc("A socket at the tip of every digit of the row, named for the digit with .tip after it; a part there sinks along its own axis and rides the digit's last joint.")
data class DigitTips(
    @Doc("The templates a tip takes; every loaded template when empty, and an empty tip shows no planned part.")
    val takes: List<TemplateId> = emptyList(),
    @Doc("Multiplies how deep a part on a tip sits inside the digit.") @Range(min = 0.0, max = 4.0)
    val sink: Float = 1f,
) {
    fun lint(ctx: LintContext, field: String): List<Finding> = ctx.sink("$field.sink", sink)

    companion object {
        /** The tip socket of the digit built as patch [digit]. */
        fun name(digit: String) = "$digit$TIP_SUFFIX"

        /** Whether [socket] is a digit's tip socket. */
        fun isTip(socket: String) = socket.endsWith(TIP_SUFFIX) && ':' in socket

        /** The toe row tip socket [socket] belongs to, or null for any other socket. */
        fun rowOf(socket: String): String? = if (isTip(socket)) socket.substringBefore(':') else null

        /** The place in its row of the digit whose tip socket is [socket], or null for any other socket. */
        fun indexOf(socket: String): Int? = if (isTip(socket)) socket.substringAfter(':').removeSuffix(TIP_SUFFIX).toIntOrNull() else null
    }
}
