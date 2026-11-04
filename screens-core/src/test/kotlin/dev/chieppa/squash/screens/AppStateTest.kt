package dev.chieppa.squash.screens

import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.screens.shell.caninePlan
import dev.chieppa.squash.character.catalog.isLeader
import dev.chieppa.squash.character.catalog.openSockets
import dev.chieppa.squash.screens.shell.shipped
import dev.chieppa.stretch.document.RecentFiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppStateTest {
    private fun created() = AppState(shipped).also { it.createCharacter(caninePlan, "Fennec") }

    @Test
    fun firstLaunchOpensOnNewCharacterAndLaterOnesOnHome() {
        assertEquals(Screen.NEW_CHARACTER, AppState(shipped).screen.value)
        assertEquals(Screen.HOME, AppState(shipped, recent = RecentFiles(listOf("a.json"))).screen.value)
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
    fun placingAPickedSocketSelectsTheLeftSide() {
        val state = created()
        val session = assertNotNull(state.session.value)
        val socket = openSockets(session.document.value, caninePlan, shipped).first { it.name == "front-leg" }
        state.chooseSocket(socket)
        assertEquals(Screen.PICK_PART, state.screen.value)
        assertEquals(TemplateId("leg"), state.pickedType.value)
        state.placePicked()
        val parts = session.document.value.character.parts.filter { it.socket?.socket == "front-leg" }
        assertEquals(2, parts.size)
        assertEquals(parts.first { it.isLeader }.id, state.selectedPart.value)
        assertEquals(Screen.WORKSPACE, state.screen.value)
        assertNull(state.pickedSocket.value)
    }

    @Test
    fun choosingATypeMovesToASocketThatTakesIt() {
        val state = created()
        state.openPartTypes()
        assertEquals("neck", state.pickedSocket.value?.name)
        state.chooseType(TemplateId("tail"))
        assertEquals("tail", state.pickedSocket.value?.name)
        state.chooseType(TemplateId("torso"))
        assertNull(state.pickedSocket.value)
    }

    @Test
    fun selectionFollowsTheDocumentThroughUndo() {
        val state = created()
        val session = assertNotNull(state.session.value)
        val socket = openSockets(session.document.value, caninePlan, shipped).first { it.name == "tail" }
        state.chooseSocket(socket)
        state.placePicked()
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
