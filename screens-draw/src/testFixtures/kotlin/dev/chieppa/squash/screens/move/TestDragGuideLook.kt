package dev.chieppa.squash.screens.move

import androidx.compose.ui.graphics.Color

/** A handle drag's guides in fixed colours and widths, for tests and smoke renders. */
val TEST_DRAG_GUIDE_LOOK = DragGuideLook(
    held = Color(0xFF66574E),
    heldWidth = 1f,
    heldDash = 2f,
    heldGap = 6f,
    caught = Color(0xFFE4572E),
    caughtWidth = 2f,
    markRadius = 11f,
)
