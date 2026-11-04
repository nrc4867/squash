package dev.chieppa.squash.render

import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.stretch.gpu.GpuContext
import dev.chieppa.stretch.gpu.GpuFrame
import dev.chieppa.stretch.gpu.GpuScene
import java.util.IdentityHashMap

/**
 * Draws a [SceneFrame]: each view in its own part of the surface with its own camera, then the overlay across the whole
 * surface. Keeps the buffers of the meshes in the latest frame. Draws only when the frame changes.
 */
class ViewsScene(private val context: GpuContext) : GpuScene<SceneFrame> {
    private val meshes = MeshPass(context)
    private val uploaded = IdentityHashMap<RenderMesh, GpuMesh>()
    private var lastFrame: SceneFrame? = null

    override fun render(frame: GpuFrame, state: SceneFrame): Boolean {
        if (state !== lastFrame) keepOnly(state)
        lastFrame = state
        val pass = frame.beginPass(state.background)
        var slot = 0
        for (view in state.views) {
            val area = view.viewport.clampedTo(frame.width, frame.height)
            if (area.isEmpty) continue
            pass.setViewport(area.x.toFloat(), area.y.toFloat(), area.width.toFloat(), area.height.toFloat())
            pass.setScissorRect(area.x, area.y, area.width, area.height)
            for (draw in view.draws) meshes.draw(pass, slot++, uploadedFor(draw.mesh), draw, view)
        }
        pass.setViewport(0f, 0f, frame.width.toFloat(), frame.height.toFloat())
        pass.setScissorRect(0, 0, frame.width, frame.height)
        frame.drawOverlay(pass, state.overlay)
        pass.end()
        return false
    }

    private fun uploadedFor(mesh: RenderMesh): GpuMesh = uploaded.getOrPut(mesh) { GpuMesh(context, mesh) }

    private fun keepOnly(frame: SceneFrame) {
        val wanted = frame.views.flatMap { view -> view.draws.map { it.mesh } }
        val gone = uploaded.keys.filter { kept -> wanted.none { it === kept } }
        gone.forEach { uploaded.remove(it)?.close() }
    }

    override fun close() {
        uploaded.values.forEach { it.close() }
        uploaded.clear()
        meshes.close()
    }
}
