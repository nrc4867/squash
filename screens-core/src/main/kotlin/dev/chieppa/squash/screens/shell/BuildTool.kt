package dev.chieppa.squash.screens.shell

/** The tool armed in the workspace's tool strip. */
enum class BuildTool {
    DRAW_OUTLINE, SPINE_WIDTH, EDIT_CURVES, ERASE, SMOOTH, MOVE, REGION_BORDER, FILL, BRUSH, PICK_COLOR, LINE_WIDTH, FUR_STROKE, COMB, SURFACE_STROKE, FLOATING_STROKE, SURFACE_ERASE, DRAW_MARK, FLOATING_MARK, EDIT_MARK, ERASE_MARK, GAZE_TARGET, PLACE_IMAGE, ROTATE_IMAGE, ADD_PART, NONE;

    /** Whether it works on the surface's color, in the Color stage. */
    val paints: Boolean get() = this == REGION_BORDER || this == FILL || this == BRUSH || this == PICK_COLOR

    /** Whether it works on the character's lines and fur, in the Lines stage. */
    val inks: Boolean get() = this == LINE_WIDTH || this == FUR_STROKE || this == COMB || this == SURFACE_STROKE || this == FLOATING_STROKE || this == SURFACE_ERASE

    /** Whether it works on a face's mark groups, in the Face stage. */
    val faces: Boolean get() = this == DRAW_MARK || this == FLOATING_MARK || this == EDIT_MARK || this == ERASE_MARK || this == GAZE_TARGET

    /** Whether it places a trace image, whose fields the References drawer shows. */
    val places: Boolean get() = this == PLACE_IMAGE || this == ROTATE_IMAGE

    /** Whether it draws a stroke that starts on the body and leaves it, such as a whisker. */
    val floats: Boolean get() = this == FLOATING_STROKE || this == FLOATING_MARK
}
