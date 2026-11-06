package dev.chieppa.squash.screens.surface

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.screens.shell.ShellTags
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.shell_drawer_angle_versions
import dev.chieppa.squash.uidesign.generated.shell_drawer_expressions
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.ui.Divider
import dev.chieppa.stretch.ui.Label
import dev.chieppa.stretch.ui.VerticalDivider
import org.jetbrains.compose.resources.stringResource
import androidx.compose.runtime.getValue

private val BAND_HEIGHT = 152.dp
private val HEADER_HEIGHT = 32.dp
private val HEADER_INSET = 12.dp

/** The room the three angle versions' tiles take. */
private val VERSIONS_WIDTH = 300.dp

/** The Face inspector: Eyes or Mouth, and the picked one's controls, layers and gaze. */
@Composable
fun FaceInspector(state: AppState, session: CharacterSession) {
    FaceSection(state, session)
}

/**
 * The band under the view for as long as Face is on screen: the expressions, and the shown one's angle versions. While it is up the face tools
 * work on the head's eyes from the start; once it goes, the picked version, its lock and the test gaze go with it.
 */
@Composable
fun FaceBand(state: AppState, modifier: Modifier = Modifier) {
    val open by state.session.collectAsState()
    val document = open?.document?.collectAsState()?.value
    val head = document?.character?.faceHead()
    LaunchedEffect(head, document?.character?.let { character -> head?.let { groupsOn(character, it).size } }) {
        val character = document?.character ?: return@LaunchedEffect
        val at = head ?: return@LaunchedEffect
        if (state.face.group?.let(character::groupOf) == null) state.pickFeature(character, at, state.face.feature)
    }
    DisposableEffect(state) {
        onDispose {
            state.face.version = null
            state.face.locked = false
            state.face.gazeTarget = null
        }
    }
    Column(modifier.fillMaxWidth().background(Stretch.colors.surfacePanel).testTag(ShellTags.FACE_BAND)) {
        Divider()
        Row(Modifier.height(BAND_HEIGHT)) {
            Column(Modifier.weight(1f)) {
                BandHeader(stringResource(Res.string.shell_drawer_expressions)) { ExpressionActions(state) }
                ExpressionsDrawer(state)
            }
            VerticalDivider()
            Column(Modifier.width(VERSIONS_WIDTH)) {
                BandHeader(stringResource(Res.string.shell_drawer_angle_versions)) { AngleVersionActions(state) }
                AngleVersionsDrawer(state)
            }
        }
    }
}

@Composable
private fun BandHeader(title: String, actions: @Composable RowScope.() -> Unit) {
    Row(Modifier.height(HEADER_HEIGHT).padding(horizontal = HEADER_INSET), verticalAlignment = Alignment.CenterVertically) {
        Label(title, Stretch.type.bodySm, Stretch.colors.inkMuted)
        Spacer(Modifier.weight(1f))
        actions()
    }
}
