package dev.chieppa.squash.screens.shell

import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.stretch.core.ActionId
import dev.chieppa.stretch.core.KeyChord
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.frame.ToastState
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class ShellKeysTest {
    private val work = TestWork()

    private object NoChooser : FileChooser {
        override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
        override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
    }

    private fun shell(keys: KeymapStore? = null): Shell {
        val state = AppState(shipped, work).also { it.createCharacter(caninePlan, "Fennec") }
        return Shell(state, NoChooser, ToastState(), CoroutineScope(Dispatchers.Unconfined), BuildContent({}), keys).apply { registerActions() }
    }

    private fun Shell.resolve(key: String, stage: Stage): String? = keymap.resolve(KeyChord.parse(key), stage.keyMode)?.id?.value

    @Test
    fun noTwoActionsShareAKeyInAnyStage() {
        assertEquals(emptyList(), shell().keymap.conflicts())
    }

    @Test
    fun eachStageGivesALetterItsOwnTool() {
        val shell = shell()
        assertEquals(BuildTool.FILL.actionId, shell.resolve("G", Stage.COLOR))
        assertEquals(BuildTool.BRUSH.actionId, shell.resolve("B", Stage.COLOR))
        assertEquals(BuildTool.GAZE_TARGET.actionId, shell.resolve("G", Stage.FACE))
        assertEquals(BuildTool.COMB.actionId, shell.resolve("C", Stage.LINES))
        assertEquals(BuildTool.PAINT_WEIGHT.actionId, shell.resolve("D", Stage.RIG))
        assertEquals(BuildTool.ERASE_WEIGHT.actionId, shell.resolve("X", Stage.RIG))
        assertEquals(BuildTool.SMOOTH_WEIGHT.actionId, shell.resolve("S", Stage.RIG))
        assertEquals(ShellActions.STAGE_RIG.value, shell.resolve("5", Stage.SHAPE))
        assertEquals(ShellActions.STAGE_DRESS.value, shell.resolve("6", Stage.RIG))
        assertEquals(ShellActions.STAGE_POSE.value, shell.resolve("7", Stage.DRESS))
        assertEquals(BuildTool.DRAW_GARMENT.actionId, shell.resolve("D", Stage.DRESS))
        assertEquals(BuildTool.EDIT_GARMENT.actionId, shell.resolve("E", Stage.DRESS))
        assertEquals(BuildTool.ERASE_GARMENT.actionId, shell.resolve("X", Stage.DRESS))
        assertEquals(BuildTool.SMOOTH_GARMENT.actionId, shell.resolve("S", Stage.DRESS))
        assertEquals(BuildTool.COVER.actionId, shell.resolve("C", Stage.DRESS))
        assertEquals(BuildTool.LOOSE_END.actionId, shell.resolve("L", Stage.DRESS))
        assertEquals(ShellActions.NEW_GARMENT.value, shell.resolve("N", Stage.DRESS))
        assertEquals(ShellActions.DRESS_HALF_OUTLINE.value, shell.resolve("H", Stage.DRESS))
        assertNull(shell.resolve("N", Stage.SHAPE), "N adds a garment only in Dress")
        assertEquals(BuildTool.DRAG.actionId, shell.resolve("D", Stage.POSE))
        assertEquals(BuildTool.TURN.actionId, shell.resolve("E", Stage.POSE))
        assertEquals(BuildTool.BODY_CURVE.actionId, shell.resolve("C", Stage.POSE))
        assertEquals(BuildTool.PIN.actionId, shell.resolve("P", Stage.POSE))
        assertEquals(BuildTool.LOOK.actionId, shell.resolve("L", Stage.POSE))
        assertNull(shell.resolve("P", Stage.SHAPE), "P pins only in Pose")
        assertNull(shell.resolve("B", Stage.SHAPE), "B opens no drawer")
        for (stage in Stage.entries) {
            assertEquals(ShellActions.FIT_PANE.value, shell.resolve("F", stage))
        }
    }

    @Test
    fun escPutsTheToolDownAndThenAnswersNoMore() {
        val shell = shell()
        val esc = shell.keymap.resolve(KeyChord("Esc"), Stage.SHAPE.keyMode)
        assertEquals(ShellActions.PUT_DOWN, esc?.id)
        esc?.run()
        assertEquals(BuildTool.NONE, shell.state.tool.value)
        assertNull(shell.keymap.resolve(KeyChord("Esc"), Stage.SHAPE.keyMode))
    }

    @Test
    fun mirrorsKeyAnswersExactlyWhenItsButtonIsEnabled() {
        val shell = shell()
        val state = shell.state
        fun answers() = shell.resolve("M", state.stage.value) == ShellActions.MIRROR.value
        assertEquals(false, state.mirrorApplies(), "the torso has no pair")
        assertEquals(false, answers())
        state.armTool(BuildTool.SURFACE_STROKE)
        assertEquals(true, state.mirrorApplies(), "a stroke mirrors across the middle")
        assertEquals(true, answers())
        state.armTool(BuildTool.DRAW_MARK)
        assertEquals(false, state.mirrorApplies(), "Face chooses the eyes another way")
        assertEquals(false, answers())
    }

    @Test
    fun keymapChangesComeBackOnTheNextRun() {
        val file = Files.createTempDirectory("keymap").resolve("keymap.txt")
        val first = shell(KeymapStore(file))
        first.keymap.rebind(ShellActions.UNDO, listOf(KeyChord("U", ctrl = true, alt = true)))
        first.keymap.rebind(ShellActions.MIRROR, emptyList())
        first.keymapChanged()
        val next = shell(KeymapStore(file))
        assertEquals(listOf(KeyChord("U", ctrl = true, alt = true)), next.keymap.keysFor(ShellActions.UNDO))
        assertTrue(next.keymap.keysFor(ShellActions.MIRROR).isEmpty())
        assertEquals(listOf(KeyChord.parse("D")), next.keymap.keysFor(ActionId(BuildTool.DRAW_OUTLINE.actionId)))
    }
}
