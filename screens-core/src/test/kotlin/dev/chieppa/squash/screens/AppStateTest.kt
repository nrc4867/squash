package dev.chieppa.squash.screens

import dev.chieppa.squash.character.catalog.isLeader
import dev.chieppa.squash.character.catalog.openSockets
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.screens.shell.caninePlan
import dev.chieppa.squash.screens.shell.shipped
import dev.chieppa.stretch.document.RecentFiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppStateTest {
    private val work = TestWork()

    private fun created() = AppState(shipped, work).also { it.createCharacter(caninePlan, "Fennec") }

    @Test
    fun firstLaunchOpensOnNewCharacterAndLaterOnesOnHome() {
        assertEquals(Screen.NEW_CHARACTER, AppState(shipped, work).screen.value)
        assertEquals(Screen.HOME, AppState(shipped, work, recent = RecentFiles(listOf("a.json"))).screen.value)
    }

    @Test
    fun creatingPlacesTheRootThroughACommandAndSelectsIt() {
        val state = created()
        val session = assertNotNull(state.session.value)
        val root = session.document.value.character.parts.single()
        assertEquals(root.id, state.selectedPart.value)
        assertEquals(Screen.WORKSPACE, state.screen.value)
        assertTrue(session.history.canUndo)
    }

    @Test
    fun undoWaitsForAnOpenGesture() {
        val state = created()
        val history = assertNotNull(state.session.value).history
        val before = history.document.value
        val gesture = history.begin()
        state.undo()
        state.redo()
        gesture.commit()
        assertEquals(before, history.document.value)
    }

    @Test
    fun addingOnAPairedSocketPlacesBothSidesInOneStepAndSelectsTheLeft() {
        val state = created()
        val session = assertNotNull(state.session.value)
        val socket = openSockets(session.document.value, caninePlan, shipped).first { it.name == "front-leg" }
        state.offerSocket(socket)
        assertEquals(TemplateId("leg"), state.addingType.value, "the popover opens on the first type the socket takes")
        state.addPart()
        val parts = session.document.value.character.parts.filter { it.socket?.socket == "front-leg" }
        assertEquals(2, parts.size)
        assertEquals(parts.first { it.isLeader }.id, state.selectedPart.value)
        assertEquals(Screen.WORKSPACE, state.screen.value)
        assertNull(state.addingSocket.value, "and the popover closes")
        session.history.undo()
        assertTrue(session.document.value.character.parts.none { it.socket?.socket == "front-leg" }, "a pair is one undo step")
    }

    @Test
    fun aTypeTheSocketDoesNotTakeAddsNothing() {
        val state = created()
        val session = assertNotNull(state.session.value)
        val socket = openSockets(session.document.value, caninePlan, shipped).first { it.name == "tail" }
        state.offerSocket(socket)
        state.addingType.value = TemplateId("leg")
        val before = session.document.value
        state.addPart()
        assertEquals(before, session.document.value)
        assertEquals(socket, state.addingSocket.value)
    }

    @Test
    fun selectionFollowsTheDocumentThroughUndo() {
        val state = created()
        val session = assertNotNull(state.session.value)
        val socket = openSockets(session.document.value, caninePlan, shipped).first { it.name == "tail" }
        state.offerSocket(socket)
        state.addPart()
        val root = session.document.value.character.parts.first().id
        session.history.undo()
        assertEquals(root, state.selectedPart.value)
        session.history.undo()
        assertNull(state.selectedPart.value)
    }

    @Test
    fun aSaveNeedingAnUnknownTemplateIsNotOpened() {
        val state = created()
        val document = assertNotNull(state.session.value).document.value
        val foreign = document.copy(
            character = document.character.copy(
                parts = document.character.parts.map { it.copy(template = it.template.copy(version = 99)) },
            ),
        )
        val before = state.session.value
        assertFailsWith<IllegalArgumentException> { state.open(foreign) }
        assertEquals(before, state.session.value)
    }

    @Test
    fun editsMarkTheCharacterAsEditedUntilSaved() {
        val state = created()
        val session = assertNotNull(state.session.value)
        val file = assertNotNull(state.file.value)
        assertTrue(file.edited)
        session.send(SetParameter(session.document.value.character.parts.single().id, "size", 1.5f))
        assertTrue(file.edited)
    }
}
