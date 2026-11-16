package dev.chieppa.squash.screens.move

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.catalog.newCharacter
import dev.chieppa.squash.character.catalog.openSockets
import dev.chieppa.squash.character.catalog.placeOnSocket
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.settle
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.SheetFit
import dev.chieppa.squash.screens.sheet.sheetSelection
import kotlin.test.assertNotNull

/** A fennec with its front legs placed in the Workspace, its left leg selected, and a 600 by 400 pane on that leg. */
open class LegBench(folder: TemplateFolder, private val work: TestWork) {
    val state = AppState(folder, work)
    val leg: PartId
    val torso: PartId

    init {
        val plan = folder.plans.first()
        val new = newCharacter(plan, folder, "Fennec")
        state.open(new.document).also { open -> new.commands.forEach(open::send) }
        torso = new.root
        val session = assertNotNull(state.session.value)
        val socket = openSockets(session.document.value, plan, folder).first { it.name == "front-leg" }
        val parts = placeOnSocket(socket, assertNotNull(folder.template(socket.takes.first())), session.document.value)
        session.send(AddParts(parts))
        leg = parts.first { it.side == Side.LEFT }.id
        state.selectedPart.value = leg
        state.screen.value = Screen.WORKSPACE
    }

    val session get() = assertNotNull(state.session.value)
    val character: AssembledCharacter get() = assertNotNull(session.assembly.output.value)

    fun params() = session.document.value.character.resolved(torso) ?: PartData()

    fun camera(view: View = View.SIDE): LockedCamera {
        val part = assertNotNull(sheetSelection(character, leg, emptyMap())).part
        return LockedCamera(view, part.axes, SheetFit(part.origin, 200f, 4f), 600, 400, part.origin)
    }

    fun steps() = session.history.steps.value.done.size

    /** Runs the work queued in [test] until the graph is settled with the leg built. */
    @OptIn(ExperimentalTestApi::class)
    fun settleIn(test: ComposeUiTest) = test.settle(work) {
        session.graph.settled.value && session.assembly.output.value?.part(leg) != null
    }
}
