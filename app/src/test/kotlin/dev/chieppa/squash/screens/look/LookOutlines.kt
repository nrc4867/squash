package dev.chieppa.squash.screens.look

import dev.chieppa.squash.character.build.templateOutline
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.TestWork

/**
 * A part's Side and then Top started from its template as Start from template starts them, then dragged: stretched [along] its axis from
 * the outline's own start and [across] it about the axis. 1 and 1 leave each outline as started.
 */
data class OutlineDrag(val part: PartId, val along: Float, val across: Float) {
    fun apply(session: CharacterSession, work: TestWork) {
        for (view in listOf(View.SIDE, View.TOP)) {
            val character = session.document.value.character
            val instance = requireNotNull(character.part(part)) { "No part ${part.value}" }
            val started = requireNotNull(templateOutline(session.builtTemplate(instance), character.resolved(instance), view)) { "${part.value} has no $view" }
            session.send(SetOutline(part, view, dragged(started, if (view == View.TOP) across else 1f)))
            work.settle(session)
        }
    }

    private fun dragged(outline: OutlineCurve, wide: Float): OutlineCurve {
        val start = outline.points.minOf { it.x }
        return OutlineCurve(outline.points.map { CurvePoint(start + (it.x - start) * along, it.y * wide) })
    }

    companion object {
        /** From `part:along:across`. */
        fun parse(entry: String): OutlineDrag {
            val (part, along, across) = entry.split(':').also { require(it.size == 3) { "outline takes part:along:across, not $entry" } }
            return OutlineDrag(PartId(part), along.toFloat(), across.toFloat())
        }
    }
}
