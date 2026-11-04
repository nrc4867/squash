package dev.chieppa.squash.screens.shell

/** The tool armed in the workspace's tool strip. */
enum class BuildTool {
    DRAW_OUTLINE, SPINE_WIDTH, EDIT_CURVES, ERASE, SMOOTH, MOVE, REGION_BORDER, FILL, BRUSH, PICK_COLOR, LINE_WIDTH, FUR_STROKE, COMB, SURFACE_STROKE, SURFACE_ERASE, NONE;

    /** Whether it works on the surface's color, with the Color drawer. */
    val paints: Boolean get() = this == REGION_BORDER || this == FILL || this == BRUSH || this == PICK_COLOR

    /** Whether it works on the character's lines and fur, with the Lines drawer. */
    val inks: Boolean get() = this == LINE_WIDTH || this == FUR_STROKE || this == COMB || this == SURFACE_STROKE || this == SURFACE_ERASE
}
