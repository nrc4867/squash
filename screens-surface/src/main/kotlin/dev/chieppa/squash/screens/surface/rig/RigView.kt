package dev.chieppa.squash.screens.surface.rig

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.rig.rememberRig
import dev.chieppa.squash.screens.sheet.RigShading
import dev.chieppa.stretch.theme.Stretch
import kotlin.math.PI
import kotlin.math.sin

/** How long a wiggle plays, how far it bends at its start, and how many times it swings. */
private const val WIGGLE_SECONDS = 0.9f
private const val WIGGLE_DEGREES = 22f
private const val WIGGLE_SWINGS = 2f
private const val NANOS_PER_SECOND = 1e9f

private val JOINT_RADIUS = 4.2.dp
private val JOINT_EDGE = 1.5.dp
private val LIT_RADIUS = 7.dp

/**
 * How the Rig stage shows the open character: each part in its Rig color with its seams' bands mixing the colors by weight, the
 * bone under the pointer or picked as a heat map over it, and a dot at every joint, all in the pose the panes show. Null before the
 * rig has run.
 */
@Composable
fun rememberRigShading(state: AppState): RigShading? {
    val rig = rememberRig(state) ?: return null
    val session by state.session.collectAsState()
    val document = session?.document?.collectAsState()?.value ?: return null
    val held by state.rig.held.collectAsState()
    val pose = held ?: document.character.pose
    val colors = remember(document.character.parts) { rigPartColors(document.character) }
    val seams = remember(rig, colors) { seamLayers(rig, colors) }
    val heat = state.rig.heat?.let(rig::indexOf)
    val heated = remember(rig, heat) { heat?.let { heatLayers(rig, it) }.orEmpty() }
    val points = remember(rig, pose) { jointPoints(rig, pose) }
    val c = Stretch.colors
    val look = with(LocalDensity.current) { JointLook(c.surfaceFloat, c.inkStrong, c.accent, JOINT_RADIUS.toPx(), JOINT_EDGE.toPx(), LIT_RADIUS.toPx()) }
    val lit = state.rig.heat
    val layers = remember(seams, heated) { rig.parts.associate { it.id to seams[it.id].orEmpty() + heated[it.id].orEmpty() } }
    return RigShading(colors, layers) { sight -> jointDots(rig, points, sight, look, lit) }
}

/**
 * Plays the wiggle asked of [AppState.rig]: the bone swings about its hinge and comes back to rest, frame by frame, held as a pose
 * in place of the character's own and never written to it. Leaving the stage ends it.
 */
@Composable
fun RigWiggle(state: AppState) {
    val wiggle = state.rig.wiggle
    LaunchedEffect(wiggle) {
        val asked = wiggle ?: return@LaunchedEffect
        val rest = state.session.value?.document?.value?.character?.pose ?: return@LaunchedEffect
        val bone = asked.bone
        val from = rest.of(bone.part, bone.joint) ?: BonePose(bone.part, bone.joint)
        try {
            val start = withFrameNanos { it }
            while (true) {
                val t = (withFrameNanos { it } - start) / NANOS_PER_SECOND
                if (t >= WIGGLE_SECONDS) break
                val swing = WIGGLE_DEGREES * (1f - t / WIGGLE_SECONDS) * sin(2f * PI.toFloat() * WIGGLE_SWINGS * t / WIGGLE_SECONDS)
                state.rig.held.value = rest.with(from.copy(degrees = from.degrees + swing))
            }
        } finally {
            state.rig.held.value = null
        }
    }
    DisposableEffect(state) { onDispose { state.rig.stopWiggle() } }
}
