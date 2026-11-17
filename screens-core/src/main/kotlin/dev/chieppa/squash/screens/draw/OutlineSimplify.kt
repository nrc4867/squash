package dev.chieppa.squash.screens.draw

import dev.chieppa.squash.character.build.outlineEnds
import dev.chieppa.squash.character.catalog.VIEW_ORDER
import dev.chieppa.squash.character.build.simplifiedOutline
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.Together
import dev.chieppa.squash.document.View
import dev.chieppa.squash.document.anchors
import dev.chieppa.squash.character.build.toPath
import dev.chieppa.squash.geom.fitShareOfSpan
import dev.chieppa.squash.geom.longestSpan
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.edit.editedOutline
import dev.chieppa.squash.screens.edit.outlineWriter

/** The view Simplify outline acts on: the selected point's, or else the active one. */
fun AppState.simplifyView(): View = curves.selection?.takeIf { it.part == selectedPart.value }?.view ?: activeView.value

/** The fewest anchors an outline Simplify could still take one from has. */
private const val MIN_SIMPLIFIED_ANCHORS = 3

/** Whether this is an outline with points to spare for Simplify to work on. */
fun OutlineCurve?.simplifiable(): Boolean = (this?.anchors?.size ?: 0) >= MIN_SIMPLIFIED_ANCHORS

/** Whether Simplify has an outline in [view] to work on. */
fun AppState.canSimplify(view: View): Boolean = curves.drawing == null && editedOutline(view).simplifiable()

/** Whether Simplify part has any outline of the selected part to work on. */
fun AppState.canSimplifyPart(): Boolean = VIEW_ORDER.any(::canSimplify)

/** Refits the outline in [view] to as few points as the Fit setting allows, as one undo step. */
fun AppState.simplifyOutline(view: View = simplifyView()) {
    if (curves.drawing != null) return
    simplifiedCommand(listOf(view))?.let { send(it) }
}

/** Refits every outline of the part the tools draw on, as one undo step. */
fun AppState.simplifyPart() {
    if (curves.drawing != null) return
    simplifiedCommand(VIEW_ORDER)?.let { send(it) }
}

private fun AppState.send(command: DocumentCommand) {
    curves.selection = null
    session.value?.send(command)
}

/** The command that simplifies the outlines in [views] that lose points, or null if none does. */
private fun AppState.simplifiedCommand(views: List<View>): DocumentCommand? {
    val opened = session.value ?: return null
    val part = selectedPart.value?.let(opened.document.value.character::part) ?: return null
    val openEnd = opened.builtTemplate(part).openEnd
    val writer = outlineWriter() ?: return null
    val commands = views.mapNotNull { view ->
        val curve = editedOutline(view) ?: return@mapNotNull null
        val simpler = simplifiedOutline(curve, fitShareOfSpan(curves.fit) * longestSpan(curve.toPath()), outlineEnds(view, openEnd))
        if (simpler.anchors.size < curve.anchors.size) writer(view, simpler) else null
    }
    return when (commands.size) {
        0 -> null
        1 -> commands.single()
        else -> Together(commands)
    }
}
