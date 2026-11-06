package dev.chieppa.squash.screens.shell

/** A stage of Build: what the tool strip, the keys and the inspector are for. Each opens on its [first] tool. */
enum class Stage(val first: BuildTool) {
    SHAPE(BuildTool.DRAW_OUTLINE),
    COLOR(BuildTool.FILL),
    LINES(BuildTool.LINE_WIDTH),
    FACE(BuildTool.DRAW_MARK),
}

/** The stage a tool belongs to; putting the tool down belongs to none. */
val BuildTool.stage: Stage?
    get() = when {
        this == BuildTool.NONE -> null
        paints -> Stage.COLOR
        inks -> Stage.LINES
        faces -> Stage.FACE
        else -> Stage.SHAPE
    }
