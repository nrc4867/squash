package dev.chieppa.squash.screens.surface

import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.sheet.toGpu
import dev.chieppa.stretch.gpu.CameraView

internal const val VIEW_PIXELS = 900

/** A camera on [target], off to the fennec's side, far enough to hold all of it. */
internal fun sideCamera(target: Vec3, offset: Vec3 = Vec3(0f, 0f, -5f)) = CameraView(eye = (target + offset).toGpu(), target = target.toGpu())

/** An open [document] with its real graph on [work], settled. */
internal fun openSession(document: SquashDocument, work: TestWork): CharacterSession {
    val folder = TemplateFolder.shipped()
    val session = CharacterSession(document, TemplateLibrary(folder.parts, folder.plans), work.scope, work.dispatcher)
    work.settle(session)
    return session
}

/** The app's state on [work] with [document] open and its graph settled. */
internal fun openState(document: SquashDocument, work: TestWork): AppState {
    val state = AppState(TemplateFolder.shipped(), work)
    work.settle(state.open(document))
    return state
}
