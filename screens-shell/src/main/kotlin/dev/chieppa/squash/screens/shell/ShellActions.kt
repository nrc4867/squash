package dev.chieppa.squash.screens.shell

import dev.chieppa.squash.screens.sheet.SheetLayout
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.draw.startFromTemplate
import dev.chieppa.squash.screens.draw.templateStartView
import dev.chieppa.stretch.core.Action
import dev.chieppa.stretch.core.ActionId
import dev.chieppa.stretch.core.KeyChord

/** The shell's actions. An action's text key is its id. */
object ShellActions {
    val NEW = ActionId("new")
    val OPEN = ActionId("open")
    val SAVE = ActionId("save")
    val SAVE_AS = ActionId("save-as")
    val CLOSE = ActionId("close")
    val UNDO = ActionId("undo")
    val REDO = ActionId("redo")
    val COMMANDS = ActionId("commands")
    val PARTS = ActionId("drawer-parts")
    val HISTORY = ActionId("drawer-history")
    val INSPECTOR = ActionId("drawer-inspector")
    val STAGE_SHAPE = ActionId("stage-shape")
    val STAGE_COLOR = ActionId("stage-color")
    val STAGE_LINES = ActionId("stage-lines")
    val STAGE_FACE = ActionId("stage-face")
    val STAGE_RIG = ActionId("stage-rig")
    val STAGE_POSE = ActionId("stage-pose")
    val DRAG = ActionId(BuildTool.DRAG.actionId)
    val TURN = ActionId(BuildTool.TURN.actionId)
    val BODY_CURVE = ActionId(BuildTool.BODY_CURVE.actionId)
    val PIN = ActionId(BuildTool.PIN.actionId)
    val LOOK = ActionId(BuildTool.LOOK.actionId)
    val PAINT_WEIGHT = ActionId(BuildTool.PAINT_WEIGHT.actionId)
    val ERASE_WEIGHT = ActionId(BuildTool.ERASE_WEIGHT.actionId)
    val SMOOTH_WEIGHT = ActionId(BuildTool.SMOOTH_WEIGHT.actionId)
    val HELP = ActionId("drawer-help")
    val RESET_LAYOUT = ActionId("reset-layout")
    val REGION_BORDER = ActionId(BuildTool.REGION_BORDER.actionId)
    val FILL = ActionId(BuildTool.FILL.actionId)
    val BRUSH = ActionId(BuildTool.BRUSH.actionId)
    val PICK_COLOR = ActionId(BuildTool.PICK_COLOR.actionId)
    val PAINT_ERASE = ActionId(BuildTool.PAINT_ERASE.actionId)
    val DRAW = ActionId(BuildTool.DRAW_OUTLINE.actionId)
    val SPINE_WIDTH = ActionId(BuildTool.SPINE_WIDTH.actionId)
    val EDIT_CURVES = ActionId(BuildTool.EDIT_CURVES.actionId)
    val ERASE = ActionId(BuildTool.ERASE.actionId)
    val SMOOTH = ActionId(BuildTool.SMOOTH.actionId)
    val MOVE = ActionId(BuildTool.MOVE.actionId)
    val LINE_WIDTH = ActionId(BuildTool.LINE_WIDTH.actionId)
    val SURFACE_STROKE = ActionId(BuildTool.SURFACE_STROKE.actionId)
    val LINE_EDIT = ActionId(BuildTool.LINE_EDIT.actionId)
    val SURFACE_ERASE = ActionId(BuildTool.SURFACE_ERASE.actionId)
    val FUR_STROKE = ActionId(BuildTool.FUR_STROKE.actionId)
    val COMB = ActionId(BuildTool.COMB.actionId)
    val DRAW_MARK = ActionId(BuildTool.DRAW_MARK.actionId)
    val EDIT_MARK = ActionId(BuildTool.EDIT_MARK.actionId)
    val ERASE_MARK = ActionId(BuildTool.ERASE_MARK.actionId)
    val GAZE_TARGET = ActionId(BuildTool.GAZE_TARGET.actionId)
    val FIT_PANE = ActionId("fit-pane")
    val PUT_DOWN = ActionId("put-down")
    val SWAP_PANE = ActionId("swap-pane")
    val MIRROR = ActionId("mirror")
    val START_TEMPLATE = ActionId("start-template")
    val HALF_OUTLINE = ActionId("half-outline")
    val ADD_PART = ActionId(BuildTool.ADD_PART.actionId)
}

private fun chord(keys: String) = listOf(KeyChord.parse(keys))

/** The key mode of a stage, which scopes its tools' keys so a key means one thing in each stage. */
val Stage.keyMode: String get() = name.lowercase()

/**
 * Registers everything a key, a menu or the palette can do. A stage's tools are scoped to it, and where a stage's tool and an action of every
 * screen share a key the stage's tool answers; everything else answers on every screen it is available on.
 */
fun Shell.registerActions() {
    val inFrame = { state.screen.value != Screen.HOME }
    val hasCharacter = { state.session.value != null }
    val inWorkspace = { state.screen.value == Screen.WORKSPACE }
    fun action(id: ActionId, icon: String?, keys: List<KeyChord> = emptyList(), available: () -> Boolean = { true }, mode: String? = null, run: () -> Unit) =
        registry.register(Action(id, id.value, icon, keys, mode, available, run))
    fun tool(tool: BuildTool, icon: String, key: String, available: () -> Boolean = inWorkspace) =
        action(ActionId(tool.actionId), icon, chord(key), available, tool.stage?.keyMode) { state.armTool(tool) }
    fun stage(id: ActionId, key: String, stage: Stage) = action(id, null, chord(key), inWorkspace) { state.showStage(stage) }
    fun toggle(drawer: String) {
        layout = layout.toggle(drawer)
    }

    action(ShellActions.NEW, "file", chord("Ctrl N")) { newCharacter() }
    action(ShellActions.OPEN, "folder", chord("Ctrl O")) { chooseAndOpen() }
    action(ShellActions.SAVE, "save", chord("Ctrl S"), hasCharacter) { save() }
    action(ShellActions.SAVE_AS, null, chord("Ctrl Shift S"), hasCharacter) { save(asNew = true) }
    action(ShellActions.CLOSE, null, available = hasCharacter) { close() }
    action(ShellActions.UNDO, "undo", chord("Ctrl Z"), { state.session.value?.history?.canUndo == true }) { state.undo() }
    action(ShellActions.REDO, "redo", chord("Ctrl Shift Z"), { state.session.value?.history?.canRedo == true }) { state.redo() }
    action(ShellActions.COMMANDS, "command", chord("Ctrl K")) { palette = true }
    action(ShellActions.PARTS, "parts", available = inFrame) { toggle(DrawerIds.PARTS) }
    action(ShellActions.ADD_PART, "add-part", chord("T"), inWorkspace) { state.armTool(BuildTool.ADD_PART) }
    action(ShellActions.HISTORY, "history", available = hasCharacter) { toggle(DrawerIds.HISTORY) }
    action(ShellActions.INSPECTOR, "part", chord("Tab"), inFrame) { toggle(DrawerIds.INSPECTOR) }
    action(ShellActions.HELP, "help", available = inFrame) { toggle(DrawerIds.HELP) }
    action(ShellActions.RESET_LAYOUT, null, available = inFrame) { layout = DEFAULT_LAYOUT }
    stage(ShellActions.STAGE_SHAPE, "1", Stage.SHAPE)
    stage(ShellActions.STAGE_COLOR, "2", Stage.COLOR)
    stage(ShellActions.STAGE_LINES, "3", Stage.LINES)
    stage(ShellActions.STAGE_FACE, "4", Stage.FACE)
    stage(ShellActions.STAGE_RIG, "5", Stage.RIG)
    stage(ShellActions.STAGE_POSE, "6", Stage.POSE)
    action(ShellActions.SWAP_PANE, null, chord("Space"), { inWorkspace() && state.layout.value == SheetLayout.FOCUS }) { state.swapPane() }
    action(ShellActions.FIT_PANE, null, chord("F"), inWorkspace) { state.fitUnderPointer() }
    action(ShellActions.PUT_DOWN, null, chord("Esc"), { inWorkspace() && state.tool.value != BuildTool.NONE }) { state.putToolDown() }
    action(ShellActions.MIRROR, "mirror", chord("M"), { inWorkspace() && state.mirrorApplies() }) { state.mirror.value = !state.mirror.value }

    tool(BuildTool.DRAW_OUTLINE, "pen", "D")
    tool(BuildTool.SPINE_WIDTH, "spine-and-width", "W")
    tool(BuildTool.EDIT_CURVES, "edit-curves", "E")
    tool(BuildTool.ERASE, "erase", "X")
    tool(BuildTool.SMOOTH, "smooth", "S")
    tool(BuildTool.MOVE, "move", "V")
    action(ShellActions.START_TEMPLATE, "start-template", available = { inWorkspace() && state.templateStartView() != null }, mode = Stage.SHAPE.keyMode) { state.startFromTemplate() }
    action(ShellActions.HALF_OUTLINE, "half-outline", chord("H"), inWorkspace, Stage.SHAPE.keyMode) { state.curves.halfOutline = !state.curves.halfOutline }

    tool(BuildTool.FILL, "fill", "G")
    tool(BuildTool.BRUSH, "brush", "B")
    tool(BuildTool.REGION_BORDER, "region-border", "R")
    tool(BuildTool.PICK_COLOR, "pick-color", "I")
    tool(BuildTool.PAINT_ERASE, "erase", "X")

    tool(BuildTool.LINE_WIDTH, "line-width", "W")
    tool(BuildTool.SURFACE_STROKE, "surface-stroke", "D")
    tool(BuildTool.LINE_EDIT, "edit-curves", "E")
    tool(BuildTool.FUR_STROKE, "fur-stroke", "U")
    tool(BuildTool.COMB, "comb", "C")
    tool(BuildTool.SURFACE_ERASE, "erase", "X")

    tool(BuildTool.DRAW_MARK, "draw-mark", "D")
    tool(BuildTool.EDIT_MARK, "edit-curves", "E")
    tool(BuildTool.ERASE_MARK, "erase", "X")
    tool(BuildTool.GAZE_TARGET, "gaze-target", "G")

    tool(BuildTool.PAINT_WEIGHT, "paint-weight", "D")
    tool(BuildTool.ERASE_WEIGHT, "erase-weight", "X")
    tool(BuildTool.SMOOTH_WEIGHT, "smooth-weight", "S")

    tool(BuildTool.DRAG, "drag", "D")
    tool(BuildTool.TURN, "turn", "E")
    tool(BuildTool.BODY_CURVE, "body-curve", "C")
    tool(BuildTool.PIN, "pin", "P")
    tool(BuildTool.LOOK, "look", "L")

}
