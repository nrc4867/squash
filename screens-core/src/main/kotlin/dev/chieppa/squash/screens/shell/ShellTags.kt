package dev.chieppa.squash.screens.shell

/** Test tags for the shell's screens. */
object ShellTags {
    /** The Start from template button on [view]'s outline row. */
    fun startTemplate(view: dev.chieppa.squash.document.View) = "tools.startTemplate.${view.name}"

    /** Draw outline's row for [view]'s outline. */
    fun outlineRow(view: dev.chieppa.squash.document.View) = "outline.row.${view.name}"

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
    const val STAGES = "stages"
    const val LAYOUT = "layout"
    const val FACE_BAND = "face.band"
    const val ADD_PART = "addPart"
    const val ADD_PART_ADD = "addPart.add"
    const val STATUS_START = "status.start"
    const val STATUS_END = "status.end"
    const val STATUS_STAGE = "status.stage"
    const val PART_EMPTY = "part.empty"
    const val ROLE_CHIP = "part.role"
    const val MIRROR = "tools.mirror"
    const val HALF_OUTLINE = "tools.halfOutline"
    const val HOME_SEARCH = "home.search"
    const val HOME_CAST = "home.cast"

    fun newPlan(id: String) = "new.plan.$id"
    fun recent(name: String) = "home.recent.$name"
    fun partRow(name: String) = "parts.row.$name"
    fun partRowState(name: String) = "parts.state.$name"
    fun partRowRole(name: String) = "parts.role.$name"
    fun addTile(id: String) = "addPart.tile.$id"
    fun addSocket(name: String) = "addPart.socket.$name"
    fun tool(tool: BuildTool) = "tools.${tool.name}"
    fun section(name: String) = "tools.section.$name"
    fun sideRow(name: String) = "parts.side.$name"
    fun parameter(name: String) = "part.parameter.$name"
    fun viewChip(view: String) = "part.view.$view"
}
