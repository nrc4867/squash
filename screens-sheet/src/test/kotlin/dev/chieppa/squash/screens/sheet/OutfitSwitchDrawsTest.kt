package dev.chieppa.squash.screens.sheet

import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.BodyLines
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.render.ObjectShading
import dev.chieppa.squash.render.PartMaterial
import dev.chieppa.squash.render.Shading
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.woman.BEACH
import dev.chieppa.squash.screens.woman.OFFICE
import dev.chieppa.squash.screens.woman.TORSO
import dev.chieppa.squash.screens.woman.savedWomanDressed
import dev.chieppa.stretch.gpu.CameraView
import dev.chieppa.stretch.gpu.Vec3 as GpuVec3
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val WIDTH = 1000
private const val HEIGHT = 800

/**
 * Switching outfits on the dressed woman, shown in her display pose, swaps her feet for pumps and back: her own foot with its toes
 * and the pump's foot are meshes of different sizes on the same part. The assembly settles before the pose and its lines catch up
 * with it, and the panes may draw at any moment between: every part they draw, posed or at rest, takes its patches and its mesh from
 * one build.
 */
class OutfitSwitchDrawsTest {
    private val failures = mutableListOf<Throwable>()
    private val work = TestWork(CoroutineExceptionHandler { _, failure -> failures += failure })
    private val look = SheetLook(
        Color.White, Color.Black, Color.Gray, Color.DarkGray, Color.Yellow, Color.Red, Color.LightGray,
        PartMaterial(Color.Green, Color(0xFF008000), Color.Black), 1f, 2f, 1f, 2f, 2f, 2f, 1f, 2f, 2f, 3f, 3f,
    )
    private val rects = sheetPaneRects(WIDTH, HEIGHT, 2)

    @Test
    fun everyDrawBetweenOutfitSwitchesTakesPatchesAndMeshFromOneBuild() {
        val state = AppState(TemplateFolder.shipped(), work)
        val session = state.open(savedWomanDressed())
        work.settle(session)
        val apart = mutableListOf<String>()
        var drawn = 0
        work.scope.launch(Dispatchers.Unconfined) {
            session.posedBody.output.collect { body -> body?.let { apart += unmatched("posed body", it) } }
        }
        work.scope.launch(Dispatchers.Unconfined) {
            combine(session.assembly.output, session.posedLines.output) { assembled, lines -> assembled to lines }.collect { (assembled, lines) ->
                if (assembled != null && lines != null) {
                    apart += unmatched("posed lines", lines)
                    sheetFrame(input(state, assembled, lines))
                    drawn++
                }
            }
        }
        val switches = listOf(BEACH, OFFICE, BEACH)
        for (outfit in switches) {
            session.send(ShowOutfit(outfit))
            work.settle(session)
        }
        assertEquals(emptyList(), failures.map { it.toString() })
        assertTrue(drawn > switches.size, "the panes draw at each step of every switch")
        assertEquals(emptyList(), apart)
    }

    /** The panes as they draw [assembled] with each part as [lines] show it in the pose, colored as the session holds it now. */
    private fun input(state: AppState, assembled: AssembledCharacter, lines: BodyLines): SheetInput {
        val session = checkNotNull(state.session.value)
        val selection = checkNotNull(sheetSelection(assembled, TORSO, emptyMap(), posed = posedParts(lines)))
        val rest = checkNotNull(session.characterSurface.output.value?.surface)
        val surface = LastPaneSurface().next(rest, session.surfacePaint.output.value, session.sessionSurface.dress.output.value?.dress, null)
        val color = PaneColor(checkNotNull(session.bodyColor.output.value), session.document.value.character.palette, ObjectShading(Shading.TWO_TONE), surface)
        val full = CameraView(eye = selection.box.center.toGpu() + GpuVec3(3f, 2f, -4f), target = selection.box.center.toGpu())
        val cameras = camerasOn(selection.part, fitSheet(selection.part.box, selection.part.axes, rects), rects)
        return SheetInput(rects, WIDTH, HEIGHT, look, selection, cameras, full, color = color)
    }

    /** Each part of [lines] whose mesh holds other than its patches' vertices, named with [where]. */
    private fun unmatched(where: String, lines: BodyLines): List<String> =
        lines.parts.mapNotNull { (id, part) -> part.part.surface.unmatched()?.let { "$where ${id.value}: $it" } }

    private fun unmatched(where: String, body: AssembledCharacter): List<String> =
        body.parts.mapNotNull { part -> part.surface.unmatched()?.let { "$where ${part.id?.value}: $it" } }

    private fun PartSurface.unmatched(): String? {
        val vertices = patches.sumOf { it.nu * it.nv }
        return if (mesh.vertexCount == vertices) null else "mesh ${mesh.vertexCount}, patches $vertices"
    }
}
