package dev.chieppa.squash.screens.shell

import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.Screen
import dev.chieppa.stretch.core.ActionId
import dev.chieppa.stretch.core.KeyChord
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.frame.ToastState
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import dev.chieppa.squash.screens.move.BodyAxis
import dev.chieppa.squash.screens.move.HANDLE_DRAG_KEY_MODE

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
        assertEquals(BuildTool.DRAW_MARK.actionId, shell.resolve("D", Stage.FACE))
        assertEquals(BuildTool.COMB.actionId, shell.resolve("C", Stage.LINES))
        assertEquals(BuildTool.PAINT_WEIGHT.actionId, shell.resolve("D", Stage.RIG))
        assertEquals(BuildTool.ERASE_WEIGHT.actionId, shell.resolve("X", Stage.RIG))
        assertEquals(BuildTool.SMOOTH_WEIGHT.actionId, shell.resolve("S", Stage.RIG))
        assertEquals(ShellActions.STAGE_RIG.value, shell.resolve("5", Stage.SHAPE))
        assertEquals(ShellActions.STAGE_DRESS.value, shell.resolve("6", Stage.RIG))
        assertEquals(ShellActions.STAGE_POSE.value, shell.resolve("7", Stage.DRESS))
        assertEquals(BuildTool.EDGE.actionId, shell.resolve("D", Stage.DRESS))
        assertEquals(BuildTool.HOLE.actionId, shell.resolve("O", Stage.DRESS))
        assertEquals(BuildTool.EDIT_GARMENT.actionId, shell.resolve("E", Stage.DRESS))
        assertEquals(BuildTool.ERASE_GARMENT.actionId, shell.resolve("X", Stage.DRESS))
        assertEquals(BuildTool.SMOOTH_GARMENT.actionId, shell.resolve("S", Stage.DRESS))
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
    fun vArmsSelectWhereTheStripHoldsItAndMoveInShape() {
        val shell = shell()
        assertEquals(BuildTool.MOVE.actionId, shell.resolve("V", Stage.SHAPE))
        assertNull(shell.resolve("V", Stage.FACE))
        for (stage in Stage.entries.filter { it.holdsSelect }) {
            assertEquals(selectActionId(stage), shell.resolve("V", stage))
        }
        shell.state.armTool(BuildTool.BRUSH)
        shell.state.selectPart(assertNotNull(shell.state.selectedPart.value))
        assertNotNull(shell.keymap.resolve(KeyChord.parse("V"), Stage.COLOR.keyMode)).run.invoke()
        assertEquals(BuildTool.NONE, shell.state.tool.value, "Select puts the brush down")
        assertEquals(Stage.COLOR, shell.state.stage.value, "and stays in Color")
        assertTrue(shell.state.partChosen.value, "keeping the choice")
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

    @Test
    fun whileAHandleDragsXYAndZHoldItToAnAxisAndArrowsNudgeWithMoveArmed() {
        val shell = shell()
        val state = shell.state
        state.screen.value = Screen.WORKSPACE
        fun dragKey(key: String) = shell.keymap.resolve(KeyChord.parse(key), HANDLE_DRAG_KEY_MODE)?.id?.value
        assertEquals(BuildTool.ERASE.actionId, shell.resolve("X", Stage.SHAPE), "X erases while nothing drags")
        assertNull(dragKey("X"))
        state.move.dragging.value = true
        assertEquals(ShellActions.dragAxis(BodyAxis.X).value, dragKey("X"))
        assertEquals(ShellActions.dragAxis(BodyAxis.Y).value, dragKey("Y"))
        assertEquals(ShellActions.dragAxis(BodyAxis.Z).value, dragKey("Z"))
        assertNotNull(shell.keymap.resolve(KeyChord.parse("Y"), HANDLE_DRAG_KEY_MODE)).run.invoke()
        assertEquals(BodyAxis.Y, state.move.axis.value)
        assertNotNull(shell.keymap.resolve(KeyChord.parse("Y"), HANDLE_DRAG_KEY_MODE)).run.invoke()
        assertNull(state.move.axis.value, "pressed again, it frees the drag")
        state.move.dragging.value = false

        state.armTool(BuildTool.MOVE)
        assertEquals(ShellActions.NUDGE_LEFT.value, shell.resolve("Left", Stage.SHAPE))
        assertEquals(ShellActions.NUDGE_UP_FAR.value, shell.resolve("Shift Up", Stage.SHAPE))
        state.armTool(BuildTool.DRAW_OUTLINE)
        assertNull(shell.resolve("Left", Stage.SHAPE), "arrows nudge only with Move armed")
    }
}
