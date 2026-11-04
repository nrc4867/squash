package dev.chieppa.squash.screens.shell

import androidx.compose.runtime.Composable
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.BodyView
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.shell_view_front
import dev.chieppa.squash.uidesign.generated.shell_view_side
import dev.chieppa.squash.uidesign.generated.shell_view_top
import org.jetbrains.compose.resources.stringResource

/** A view's name. */
@Composable
fun viewLabel(view: View): String = stringResource(
    when (view) {
        View.SIDE -> Res.string.shell_view_side
        View.TOP -> Res.string.shell_view_top
        View.FRONT -> Res.string.shell_view_front
    },
)

/** The name of the direction a camera looks at the body from. */
@Composable
fun directionLabel(body: BodyView): String = stringResource(
    when (body) {
        BodyView.SIDE -> Res.string.shell_view_side
        BodyView.TOP -> Res.string.shell_view_top
        BodyView.FRONT -> Res.string.shell_view_front
    },
)
