package dev.chieppa.squash.geom

/**
 * How a locked pane looks at the body: from [toViewer], with [up] at the top of the screen and body forward to the left
 * wherever the pane shows it. Top above Side shares the column, world x; Side beside Front shares the row, world y.
 */
enum class BodyView(val toViewer: Vec3, val up: Vec3) {
    TOP(Vec3.Y, Vec3.Z),
    SIDE(Vec3.Z * -1f, Vec3.Y),
    FRONT(Vec3.X, Vec3.Y),
    ;

    /** Where the screen's right points. */
    val right: Vec3 get() = up cross toViewer
}
