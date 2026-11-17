package dev.chieppa.squash.screens.library

import dev.chieppa.squash.character.catalog.newest
import dev.chieppa.squash.character.catalog.openSockets
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.screens.fennec.BACK_LEG_L
import dev.chieppa.squash.screens.fennec.BACK_LEG_R
import dev.chieppa.squash.screens.fennec.BACK_PAW_L
import dev.chieppa.squash.screens.fennec.BACK_PAW_R
import dev.chieppa.squash.screens.fennec.FRONT_LEG_L
import dev.chieppa.squash.screens.fennec.FRONT_LEG_R
import dev.chieppa.squash.screens.fennec.FRONT_PAW_R
import dev.chieppa.squash.screens.fennec.HEAD
import dev.chieppa.squash.screens.fennec.TORSO
import dev.chieppa.squash.screens.fennec.savedFennec
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

    /** The fennec open with [part] selected, as a press on its row in Parts leaves it. */
    private fun fennecWith(part: PartId): AppState = AppState(shipped, work).also {
        it.open(savedFennec())
        it.selectPart(part)
    }

    private fun AppState.templatesOn(socket: String): List<String> =
        assertNotNull(session.value).document.value.character.parts.filter { it.socket?.socket == socket }.map { it.template.id.value }

    @Test
    fun anEmptyLegSocketLightsEveryLimbAndNothingElse() {
        val state = created()
        val document = assertNotNull(state.session.value).document.value
        state.offerSocket(openSockets(document, caninePlan, shipped).first { it.name == "front-leg" })
        val add = assertIs<LibraryMode.Add>(state.libraryMode())
        val lit = shipped.newest.filter { add.lit(it.id) }.map { it.id.value }.toSet()
        assertEquals(setOf("leg", "leg-bird", "arm"), lit, "a limb socket takes any limb; a body, paw or tail stays dim")
    }

    @Test
    fun aSelectedLegLightsTheLimbsItsPawCanMoveOnto() {
        val state = fennecWith(BACK_LEG_L)
        val replace = assertIs<LibraryMode.Replace>(state.libraryMode())
        assertEquals(setOf("leg-bird", "arm"), replace.choices.map { it.value }.toSet())
        assertTrue(replace.lit(TemplateId("leg")), "its own template stays lit")
        assertFalse(replace.lit(TemplateId("tail")), "a tail has no socket for the paw")
        assertFalse(replace.lit(TemplateId("torso")), "nor a template of another family whose sockets have other names")
        assertFalse(replace.lit(TemplateId("wing-feathered")), "nor one that reads its views another way")
    }

    @Test
    fun aSelectedHeadKeepsItsEarsOrStaysDim() {
        val state = fennecWith(HEAD)
        val replace = assertIs<LibraryMode.Replace>(state.libraryMode())
        assertTrue(replace.lit(TemplateId("head-round")), "a round head has ear sockets by the same name")
        assertFalse(replace.lit(TemplateId("head-bird")), "a bird head has only its muzzle, nowhere near the ears")
    }

    @Test
    fun birdLegsBehindAndArmsInFrontEachSwapInOneStepKeepingPairsAndPaws() {
        val state = fennecWith(BACK_LEG_L)
        val session = assertNotNull(state.session.value)
        val before = session.document.value
        val steps = session.history.steps.value.done.size
        state.pickFromLibrary(TemplateId("leg-bird"))
        assertEquals(steps + 1, session.history.steps.value.done.size, "one undo step")
        val character = session.document.value.character
        assertEquals(listOf("leg-bird", "leg-bird"), state.templatesOn("back-leg"), "both sides of the pair")
        assertEquals(character.part(BACK_LEG_L)?.pair, character.part(BACK_LEG_R)?.pair)
        assertEquals(listOf(Role.LEG, Role.LEG), listOf(BACK_LEG_L, BACK_LEG_R).map { character.part(it)?.role })
        assertEquals(SocketRef(BACK_LEG_L, "foot"), character.part(BACK_PAW_L)?.socket, "each paw on its bird leg's end")
        assertEquals(SocketRef(BACK_LEG_R, "foot"), character.part(BACK_PAW_R)?.socket)
        assertEquals(BACK_LEG_L, state.selectedPart.value)

        state.selectPart(FRONT_LEG_R)
        state.pickFromLibrary(TemplateId("arm"))
        val armed = session.document.value.character
        assertEquals(listOf("arm", "arm"), state.templatesOn("front-leg"), "a right side swaps its pair too")
        assertEquals(listOf(Role.ARM, Role.ARM), listOf(FRONT_LEG_L, FRONT_LEG_R).map { armed.part(it)?.role })
        assertEquals(SocketRef(FRONT_LEG_R, "hand"), armed.part(FRONT_PAW_R)?.socket)

        session.history.undo()
        session.history.undo()
        assertEquals(before, session.document.value, "undo takes both swaps back whole")
    }

    @Test
    fun aDropOnAFilledSocketReplacesThePartThereAndSelectsIt() {
        val state = fennecWith(HEAD)
        val session = assertNotNull(state.session.value)
        assertTrue(state.replaceWith(BACK_LEG_R, TemplateId("leg-bird")))
        assertEquals(listOf("leg-bird", "leg-bird"), state.templatesOn("back-leg"))
        assertEquals(BACK_LEG_R, state.selectedPart.value)
        val before = session.document.value
        assertFalse(state.replaceWith(BACK_LEG_R, TemplateId("tail")), "a tail has nowhere for the paw")
        assertFalse(state.replaceWith(TORSO, TemplateId("torso-upright")), "the root keeps its plan's template")
        assertEquals(before, session.document.value)
    }
}
