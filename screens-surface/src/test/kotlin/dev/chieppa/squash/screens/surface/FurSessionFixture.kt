package dev.chieppa.squash.screens.surface

import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.sheet.toGpu
import dev.chieppa.stretch.gpu.CameraView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

internal const val VIEW_PIXELS = 900
private const val SETTLE_MILLIS = 30_000L

/** A camera on [target], off to the fennec's side, far enough to hold all of it. */
internal fun sideCamera(target: Vec3, offset: Vec3 = Vec3(0f, 0f, -5f)) = CameraView(eye = (target + offset).toGpu(), target = target.toGpu())

internal fun CharacterSession.settle() = runBlocking {
    withTimeout(SETTLE_MILLIS) {
        do {
            graph.settled.first { it }
            viewGraph.settled.first { it }
        } while (!graph.settled.value)
    }
}

/** An open [document] with its real graph, settled. */
internal fun openSession(document: SquashDocument): CharacterSession {
    val folder = TemplateFolder.shipped()
    val session = CharacterSession(document, TemplateLibrary(folder.parts, folder.plans), CoroutineScope(SupervisorJob() + Dispatchers.Default))
    session.settle()
    return session
}

/** The app's state with [document] open and its graph settled. */
internal fun openState(document: SquashDocument): AppState {
    val state = AppState(TemplateFolder.shipped(), CoroutineScope(SupervisorJob() + Dispatchers.Default))
    state.open(document).settle()
    return state
}
