package dev.chieppa.squash.screens.shell

/** The tool armed in the workspace's tool strip. */
enum class BuildTool {
    DRAW_OUTLINE, SPINE_WIDTH, EDIT_CURVES, ERASE, SMOOTH, MOVE, REGION_BORDER, FILL, BRUSH, PICK_COLOR, NONE;

    /** Whether it works on the surface's color, with the Color drawer. */
    val paints: Boolean get() = this == REGION_BORDER || this == FILL || this == BRUSH || this == PICK_COLOR
}
