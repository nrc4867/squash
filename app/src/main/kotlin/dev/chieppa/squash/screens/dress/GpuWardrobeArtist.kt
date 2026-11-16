package dev.chieppa.squash.screens.dress

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import dev.chieppa.squash.character.build.FurView
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.geom.field.CpuFieldReads
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.render.ViewsScene
import dev.chieppa.squash.rig.SessionPose
import dev.chieppa.squash.screens.surface.CharacterState
import dev.chieppa.stretch.core.Applied
import dev.chieppa.stretch.core.Command
import dev.chieppa.stretch.gpu.GpuOptions
import dev.chieppa.stretch.gpu.GpuSnapshots
import java.awt.image.BufferedImage
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * Renders wardrobe thumbnails from the side through the real graph, rig and scene: one character session of its own, run on the
 * caller's dispatcher and kept between renders, its document set to each scene's, so scenes that share a body share its build; then
 * each image through one GPU device and scene kept between renders. A render cancelled while its session is still building drops that
 * session, so no stale build keeps running; a render that fails drops the device, and the next opens another.
 */
class GpuWardrobeArtist : WardrobeArtist {
    private class Kept(val session: CharacterSession, val scope: CoroutineScope, val templates: TemplateLibrary)

    private var kept: Kept? = null
    private var next: SquashDocument? = null
    private var snapshots: GpuSnapshots<SceneFrame>? = null

    /** The one command the session's history is given: it sets the document to [next], holding no document of its own. */
    private val mirror: Command<SquashDocument> = Command { Applied(checkNotNull(next), keep) }
    private val keep: Command<SquashDocument> = Command { Applied(it, keep) }

    override suspend fun render(scene: WardrobeScene, templates: TemplateLibrary, look: WardrobeLook): ImageBitmap? {
        val session = sessionFor(scene.document, templates, currentCoroutineContext()[ContinuationInterceptor] as CoroutineDispatcher)
        try {
            combine(session.graph.settled, session.viewGraph.settled) { a, b -> a && b }.first { it }
        } catch (cancelled: CancellationException) {
            drop()
            throw cancelled
        }
        val state = shownState(session) ?: return null
        val frame = wardrobeFrame(state, session.document.value, scene, look) ?: return null
        return try {
            val gpu = snapshots ?: GpuSnapshots.open(GpuOptions(onError = {})) { ViewsScene(it) }.also { snapshots = it }
            val image = gpu.render(frame, look.width, look.height)
            BufferedImage(look.width, look.height, BufferedImage.TYPE_INT_ARGB).apply { setRGB(0, 0, look.width, look.height, image.pixels, 0, look.width) }.toComposeImageBitmap()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: Exception) {
            snapshots?.close()
            snapshots = null
            null
        }
    }

    private fun sessionFor(document: SquashDocument, templates: TemplateLibrary, dispatcher: CoroutineDispatcher): CharacterSession {
        val held = kept?.takeIf { it.templates === templates }
        if (held != null) {
            if (held.session.document.value != document) {
                next = document
                held.session.history.execute(mirror)
                next = null
            }
            return held.session
        }
        drop()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val session = CharacterSession(document, templates, scope, dispatcher, CpuFieldReads)
        SessionPose(session)
        kept = Kept(session, scope, templates)
        return session
    }

    private fun drop() {
        kept?.scope?.cancel()
        kept = null
    }
}

/** What [session] shows, settled: its posed body, color, surface work and lines, without fur, and its one surface posed with its paint and dress. */
private fun shownState(session: CharacterSession): CharacterState? {
    val posed = session.sessionSurface.posed.value?.output?.value?.posed
    return CharacterState(
        session.posedBody.output.value ?: return null,
        session.bodyColor.output.value ?: return null,
        session.posedSurfaceWork.output.value ?: return null,
        session.document.value.character.palette,
        session.posedLines.output.value ?: return null,
        fur = FurView.EMPTY,
        surface = posed?.surface ?: session.characterSurface.output.value?.surface ?: return null,
        paint = posed?.paint ?: session.surfacePaint.output.value?.paint,
        dress = posed?.dress ?: session.sessionSurface.dress.output.value?.dress,
    )
}
