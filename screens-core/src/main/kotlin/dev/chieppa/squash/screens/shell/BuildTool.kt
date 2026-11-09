package dev.chieppa.squash.screens.shell

/** The tool armed in the workspace's tool strip. */
enum class BuildTool {
    DRAW_OUTLINE, SPINE_WIDTH, EDIT_CURVES, ERASE, SMOOTH, MOVE, REGION_BORDER, FILL, BRUSH, PICK_COLOR, PAINT_ERASE, LINE_WIDTH, FUR_STROKE, COMB, SURFACE_STROKE, LINE_EDIT, SURFACE_ERASE, DRAW_MARK, EDIT_MARK, ERASE_MARK, GAZE_TARGET, PAINT_WEIGHT, ERASE_WEIGHT, SMOOTH_WEIGHT, DRAW_GARMENT, EDIT_GARMENT, ERASE_GARMENT, SMOOTH_GARMENT, COVER, LOOSE_END, DRAG, TURN, BODY_CURVE, PIN, LOOK, ADD_PART, NONE;

    /** Whether it works on the surface's color, in the Color stage. */
    val paints: Boolean get() = this == REGION_BORDER || this == FILL || this == BRUSH || this == PICK_COLOR || this == PAINT_ERASE

    /** Whether it works on the character's lines and fur, in the Lines stage. */
    val inks: Boolean get() = this == LINE_WIDTH || this == FUR_STROKE || this == COMB || this == SURFACE_STROKE || this == LINE_EDIT || this == SURFACE_ERASE

    /** Whether it works on a face's mark groups, in the Face stage. */
    val faces: Boolean get() = this == DRAW_MARK || this == EDIT_MARK || this == ERASE_MARK || this == GAZE_TARGET

    /** Whether it lays strokes on the rig's seams, in the Rig stage. */
    val rigs: Boolean get() = this == PAINT_WEIGHT || this == ERASE_WEIGHT || this == SMOOTH_WEIGHT

    /** Whether it works on the character's garments, in the Dress stage. */
    val dresses: Boolean get() = this == DRAW_GARMENT || this == EDIT_GARMENT || this == ERASE_GARMENT || this == SMOOTH_GARMENT || this == COVER || this == LOOSE_END

    /** Whether it poses the character, in the Pose stage. */
    val poses: Boolean get() = this == DRAG || this == TURN || this == BODY_CURVE || this == PIN || this == LOOK
}
