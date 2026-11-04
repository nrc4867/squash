package dev.chieppa.squash.screens.shell

/** Test tags for the shell's screens. */
object ShellTags {
    const val HOME = "home"
    const val HOME_CONTINUE = "home.continue"
    const val HOME_NEW = "home.new"
    const val HOME_ART = "home.art"
    const val NEW_ART = "new.art"
    const val NEW_DIALOG = "new.dialog"
    const val NEW_NAME = "new.name"
    const val NEW_CREATE = "new.create"
    const val NEW_OPEN = "new.open"
    const val WORKSPACE = "workspace"
    const val PICK = "pick"
    const val PICK_DRAW = "pick.draw"
    const val PICK_SOCKET = "pick.socket"
    const val STATUS_START = "status.start"
    const val STATUS_END = "status.end"
    const val PART_EMPTY = "part.empty"

    fun newPlan(id: String) = "new.plan.$id"
    fun recent(name: String) = "home.recent.$name"
    fun partRow(name: String) = "parts.row.$name"
    fun partRowState(name: String) = "parts.state.$name"
    fun pickTile(id: String) = "pick.tile.$id"
    fun socketDot(name: String) = "pick.dot.$name"
    fun tool(tool: BuildTool) = "tools.${tool.name}"
    fun parameter(name: String) = "part.parameter.$name"
    fun viewChip(view: String) = "part.view.$view"
}
