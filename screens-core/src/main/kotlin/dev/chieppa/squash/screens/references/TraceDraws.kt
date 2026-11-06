package dev.chieppa.squash.screens.references

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TraceOrder
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.ImageDraw
import dev.chieppa.squash.screens.AppState

/** A view's trace images: those drawn [behind] the character and those drawn [over] it. */
data class TraceDraws(val behind: List<ImageDraw> = emptyList(), val over: List<ImageDraw> = emptyList()) {
    companion object {
        val None = TraceDraws()
    }
}

/** The shown trace of the character's view that a camera looking from [toViewer] sees most squarely, drawn where it lies. */
fun traceDraws(document: SquashDocument, images: ReferenceImageCache, toViewer: Vec3): TraceDraws {
    val view = viewFacing(toViewer)
    return traceDrawsIn(document, images, view)
}

/** The shown trace of [view], drawn where it lies. */
fun traceDrawsIn(document: SquashDocument, images: ReferenceImageCache, view: View): TraceDraws {
    val trace = document.character.traces[view]?.takeIf { it.shown } ?: return TraceDraws.None
    val image = document.references.image(trace.image) ?: return TraceDraws.None
    val decoded = images.of(image) ?: return TraceDraws.None
    val draw = ImageDraw(decoded.trace, traceCorners(trace, view, image.width, image.height), trace.opacity)
    return if (trace.order == TraceOrder.BEHIND) TraceDraws(behind = listOf(draw)) else TraceDraws(over = listOf(draw))
}

/** What a pane looking from a direction shows of the open character's traces, kept while the document stays the same. */
@Composable
fun rememberTraces(state: AppState): (toViewer: Vec3) -> TraceDraws {
    val session by state.session.collectAsState()
    val document = session?.document?.collectAsState()?.value
    return remember(document) {
        if (document == null || document.character.traces.isEmpty()) { _ -> TraceDraws.None } else { toViewer -> traceDraws(document, state.images, toViewer) }
    }
}
