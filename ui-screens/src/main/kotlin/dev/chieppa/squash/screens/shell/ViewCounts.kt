package dev.chieppa.squash.screens.shell

import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.Sets
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.View

/** The Side, Top and Front order every view list uses. */
val VIEW_ORDER: List<View> = listOf(View.SIDE, View.TOP, View.FRONT)

/** Of a template's views: how many have an outline, and how many still come from the template. */
data class ViewCounts(val drawn: Int, val fromTemplate: Int)

fun viewCounts(template: PartTemplate, outlines: Map<View, OutlineCurve>): ViewCounts {
    val drawn = template.views.keys.count { it in outlines }
    return ViewCounts(drawn, template.views.size - drawn)
}

/** Whether a view's outline is what a person draws; caps and sections default from the template. */
fun Sets.isDrawnByHand(): Boolean = this == Sets.HEIGHT || this == Sets.WIDTH
