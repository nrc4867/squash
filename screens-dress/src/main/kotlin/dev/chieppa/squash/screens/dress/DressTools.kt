package dev.chieppa.squash.screens.dress

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import dev.chieppa.squash.character.build.Covering
import dev.chieppa.squash.document.CoverPart
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.UncoverPart
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.draw.isToolPress
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.PaneTool
import dev.chieppa.squash.screens.sheet.SurfaceView
import dev.chieppa.squash.screens.sheet.bodyThrough
import dev.chieppa.squash.screens.surface.surfaceHit

/** What a press in Dress lands on: a garment's branch or cuff, by what it [covers], or a body [part]. */
sealed interface DressHit {
    data class OnGarment(val covers: Covering) : DressHit
    data class OnBody(val part: PartId) : DressHit
}

/** What a press at [pixel] through [sight] lands on in the Dress stage, garments in front of the body, or null on nothing. */
fun AppState.dressHit(pixel: Offset, sight: SurfaceView, garments: Boolean = true): DressHit? {
    val body = bodyThrough(sight) ?: return null
    val hit = surfaceHit(body, pixel, sight, garments) ?: return null
    val covering = body.part(hit.part)?.covering
    return if (covering != null) DressHit.OnGarment(covering) else DressHit.OnBody(hit.part)
}

/** A Dress tool answering a single press, in a locked pane or a free one. A press on nothing is left to the pane. */
abstract class DressPressTool(protected val state: AppState) : PaneTool {
    override val takesFreeCamera: Boolean get() = true
    override val showsWhole: Boolean get() = true

    /** Whether a press finds a garment in front of the body, or passes through it to the body. */
    protected open val findsGarments: Boolean get() = true

    /** Takes a press on [hit]. */
    abstract fun pressed(hit: DressHit)

    override fun pointerInput(view: View, camera: LockedCamera): Modifier = gestures(camera, camera)

    override fun fullInput(view: FullView): Modifier = gestures(view, view)

    private fun gestures(key: Any, sight: SurfaceView): Modifier = Modifier.pointerInput(this, key) {
        awaitEachGesture {
            val down = awaitFirstDown()
            if (!isToolPress(down)) return@awaitEachGesture
            val hit = state.dressHit(down.position, sight, findsGarments) ?: return@awaitEachGesture
            down.consume()
            pressed(hit)
        }
    }
}

/** What a press does in Dress with no garment tool working: on a garment it picks the garment and selects the part its branch covers, on the body the part. */
@Stable
class GarmentSelectTool(state: AppState) : DressPressTool(state) {
    override val showsWhole: Boolean get() = false

    override fun pressed(hit: DressHit) {
        when (hit) {
            is DressHit.OnGarment -> {
                state.dress.garment.value = hit.covers.garment
                state.selectedPart.value = hit.covers.part
            }
            is DressHit.OnBody -> state.selectedPart.value = hit.part
        }
    }
}

/** Cover: a press on a body part gives the picked garment a branch over it, or takes the branch it has there off; on a garment it picks it. */
@Stable
class CoverTool(state: AppState) : DressPressTool(state) {
    override val findsGarments: Boolean get() = false

    override fun pressed(hit: DressHit) {
        val part = (hit as? DressHit.OnBody)?.part ?: return
        state.selectedPart.value = part
        val session = state.session.value ?: return
        val garment = state.dress.garment.value?.let(session.document.value.character.wardrobe::garment) ?: return
        coverToggle(session.document.value.character, garment, part)?.let(session::send)
    }
}

/** The command a Cover press on [part] sends for [garment]: its branch there taken off, or a new branch over it, keyed by the pair's leader; null for no part. */
fun coverToggle(character: Character, garment: Garment, part: PartId): DocumentCommand? {
    val instance = character.part(part) ?: return null
    val leader = character.leaderOf(instance)
    val held = character.covers(garment, instance)
    return if (held != null) UncoverPart(garment.id, held.part) else CoverPart(garment.id, GarmentBranch(leader.id))
}

@Composable
fun rememberGarmentSelectTool(state: AppState): GarmentSelectTool = remember(state) { GarmentSelectTool(state) }

@Composable
fun rememberCoverTool(state: AppState): CoverTool = remember(state) { CoverTool(state) }
