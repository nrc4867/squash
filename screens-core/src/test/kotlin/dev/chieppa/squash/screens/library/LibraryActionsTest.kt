package dev.chieppa.squash.screens.library

import dev.chieppa.squash.character.catalog.newest
import dev.chieppa.squash.character.catalog.openSockets
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.addPart
import dev.chieppa.squash.screens.shell.caninePlan
import dev.chieppa.squash.screens.shell.shipped
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LibraryActionsTest {
    private val work = TestWork()

    private fun created() = AppState(shipped, work).also { it.createCharacter(caninePlan, "Fennec") }

    private fun AppState.add(socket: String, type: String) {
        val document = assertNotNull(session.value).document.value
        offerSocket(openSockets(document, caninePlan, shipped).first { it.name == socket })
        addPart(TemplateId(type))
    }

    @Test
    fun groupsComeInBodyOrderWithTheTargetsGroupFirst() {
        val shelves = libraryShelves(shipped.newest, "")
        assertEquals(LibraryGroup.entries.filter { group -> shelves.any { it.group == group } }, shelves.map { it.group })
        assertEquals(listOf("Paw", "Foot", "Bird foot", "Hand").toSet(), shelves.first { it.group == LibraryGroup.HANDS_FEET }.templates.map { it.name }.toSet())
        val tail = libraryShelves(shipped.newest, "", first = { it.id == TemplateId("tail") })
        assertEquals(LibraryGroup.TAIL, tail.first().group)
    }

    @Test
    fun aSearchShowsOnlyGroupsWithMatchesAndFindsTheBestOne() {
        val shelves = libraryShelves(shipped.newest, "claw")
        assertEquals(listOf(LibraryGroup.HORNS_CLAWS), shelves.map { it.group })
        assertEquals("Claw", bestMatch(shipped.newest, "claw")?.name)
        assertEquals(emptyList(), libraryShelves(shipped.newest, "zzqx"))
    }

    @Test
    fun aTargetLightsWhatItsSocketTakesAndAPartLightsWhatItCanBecome() {
        val state = created()
        val document = assertNotNull(state.session.value).document.value
        state.offerSocket(openSockets(document, caninePlan, shipped).first { it.name == "tail" })
        val add = assertIs<LibraryMode.Add>(state.libraryMode())
        assertTrue(add.lit(TemplateId("tail")))
        assertFalse(add.lit(TemplateId("leg")))
        state.closeAddPart()
        state.add("front-leg", "leg")
        state.add("paw", "paw")
        val replace = assertIs<LibraryMode.Replace>(state.libraryMode())
        assertEquals(TemplateId("paw"), replace.own)
        assertTrue(replace.lit(TemplateId("foot")))
        assertEquals(TemplateId("paw"), state.library.recent.value.first(), "the added template leads Recent")
    }

    @Test
    fun replacingAPartIsOneStepAndKeepsItsPair() {
        val state = created()
        state.add("front-leg", "leg")
        state.add("paw", "paw")
        val session = assertNotNull(state.session.value)
        val paw = assertNotNull(state.selectedPart.value)
        state.pickFromLibrary(TemplateId("foot"))
        val paws = session.document.value.character.parts.filter { it.socket?.socket == "paw" }
        assertEquals(listOf("foot", "foot"), paws.map { it.template.id.value })
        session.history.undo()
        assertEquals("paw", session.document.value.character.part(paw)?.template?.id?.value)
    }

    @Test
    fun removingALegTakesItsOtherSideAndThePawsOnThemInOneStep() {
        val state = created()
        state.add("front-leg", "leg")
        state.add("paw", "paw")
        val session = assertNotNull(state.session.value)
        val before = session.document.value
        val leg = before.character.parts.first { it.socket?.socket == "front-leg" }
        assertEquals(4, before.character.removalOf(leg.id).size)
        state.removePart(leg.id)
        assertEquals(1, session.document.value.character.parts.size)
        assertTrue(openSockets(session.document.value, caninePlan, shipped).any { it.name == "front-leg" }, "the socket comes back")
        session.history.undo()
        assertEquals(before, session.document.value)
    }

    @Test
    fun theRootTakesNoRemove() {
        val state = created()
        val session = assertNotNull(state.session.value)
        val root = session.document.value.character.parts.single()
        assertEquals(null, session.document.value.character.removePartsCommand(root.id))
    }
}
