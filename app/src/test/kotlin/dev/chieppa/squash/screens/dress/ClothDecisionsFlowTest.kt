package dev.chieppa.squash.screens.dress

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.character.surface.GarmentCover
import dev.chieppa.squash.character.surface.ShellCache
import dev.chieppa.squash.character.surface.WornCover
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.cat.CAPE
import dev.chieppa.squash.screens.cat.CAT_BACK_LEG_L
import dev.chieppa.squash.screens.cat.CAT_BACK_LEG_R
import dev.chieppa.squash.screens.cat.CAT_FRONT_LEG_L
import dev.chieppa.squash.screens.cat.CAT_FRONT_LEG_R
import dev.chieppa.squash.screens.cat.CAT_HEAD
import dev.chieppa.squash.screens.cat.CAT_TAIL
import dev.chieppa.squash.screens.cat.CAT_TORSO
import dev.chieppa.squash.screens.cat.savedCatDressed
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

/** Where the cat's leg sockets sit round the torso, in v, on the left; the right mirrors it. */
private const val LEG_SOCKET_V = 0.42f

/** Holes, cuffs and hanging are read from a garment's cells, so a garment painted in Dress decides as the same cells given as boxes do. */
@Tag("integration")
class ClothDecisionsFlowTest {
    private val work = TestWork()

    @AfterTest
    fun close() = work.close()

    private val AppState.open: CharacterSession get() = assertNotNull(session.value)
    private val AppState.character: Character get() = open.document.value.character
    private val AppState.body: AssembledCharacter get() = assertNotNull(open.assembly.output.value)
    private val AppState.cape: Garment get() = assertNotNull(character.wardrobe.garment(CAPE))

    private fun AppState.worn(): WornCover = WornCover.of(GarmentCover.of(cape, character), body, emptyList(), ShellCache())

    private fun assertLegsHangInside(worn: WornCover, label: String) {
        val legs = setOf(CAT_FRONT_LEG_L, CAT_FRONT_LEG_R, CAT_BACK_LEG_L, CAT_BACK_LEG_R)
        assertTrue(legs.none { it in worn.cuffs || it in worn.holes }, "$label: the legs come out under the cape, not through it: cuffs ${worn.cuffs}, holes ${worn.holes}")
        assertTrue(CAT_TORSO in worn.hangsOver, "$label: the cape hangs over what comes out of the torso")
        assertEquals(setOf(CAT_HEAD), worn.cuffs, "$label: the collar")
        assertEquals(setOf(CAT_TAIL), worn.holes, "$label: the tail's slit")
    }

    @Test
    fun theVampireCapePaintedInDressStillHangsOverTheCatsLegs() {
        val state = AppState(TemplateFolder.shipped(), work)
        state.open(savedCatDressed())
        work.settle(state.open)
        assertLegsHangInside(state.worn(), "as saved")
        val painted = paintedOf(state.character, state.body, state.cape)
        state.open.send(state.character.paintCommand(state.body, state.cape, null, painted.cloth, PaintStyle()))
        work.settle(state.open)
        val torso = assertNotNull(state.cape.branch(CAT_TORSO))
        assertTrue(LEG_SOCKET_V in torso.fromV..torso.toV && 1f - LEG_SOCKET_V in torso.fromV + 1f..torso.toV + 1f, "painting writes the cells' box, which reaches the leg sockets: $torso")
        assertEquals(painted.cloth.runs(), paintedOf(state.character, state.body, state.cape).cloth.runs(), "the same cells")
        assertTrue(listOf(CAT_FRONT_LEG_L, CAT_BACK_LEG_L).none { state.cape.changes[it] is BodyPartChange.Through }, "the legs sit outside the cells: ${state.cape.changes}")
        assertLegsHangInside(state.worn(), "painted")
    }
}
