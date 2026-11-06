package dev.chieppa.squash.render

import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.stretch.gpu.GpuContext
import dev.chieppa.stretch.gpu.GpuFrame
import dev.chieppa.stretch.gpu.GpuScene
import dev.chieppa.stretch.gpu.webgpu.GpuBuffer
import java.util.IdentityHashMap

/**
 * Draws a [SceneFrame]: each view in its own part of the surface with its own camera, then the overlay across the whole
 * surface. Keeps the buffers of the meshes in the latest frame. Draws only when the frame changes.
 */
class ViewsScene(private val context: GpuContext) : GpuScene<SceneFrame> {
    private val meshes = MeshPass(context)
    private val colorLayers: ColorLayers = ColorLayerPass(context)
    private val ribbons: StrokeRibbons = StrokeRibbonPass(context)
    private val images = ImagePass(context)
    private val uploaded = IdentityHashMap<RenderMesh, GpuMesh>()
    private val uploadedAlphas = IdentityHashMap<FloatArray, GpuBuffer>()
    private var lastFrame: SceneFrame? = null

    override fun render(frame: GpuFrame, state: SceneFrame): Boolean {
        if (state !== lastFrame) keepOnly(state)
        lastFrame = state
        val pass = frame.beginPass(state.background)
        var slot = 0
        var layerSlot = 0
        var ribbonSlot = 0
        var imageSlot = 0
        for (view in state.views) {
            val area = view.viewport.clampedTo(frame.width, frame.height)
            if (area.isEmpty) continue
            pass.setViewport(area.x.toFloat(), area.y.toFloat(), area.width.toFloat(), area.height.toFloat())
            pass.setScissorRect(area.x, area.y, area.width, area.height)
            for (image in view.behind) images.draw(pass, imageSlot++, image, view)
            for (draw in view.draws) {
                meshes.draw(pass, slot++, uploadedFor(draw.mesh), draw, view)
                if (draw.opacity < 1f) continue
                for (layer in draw.layers) colorLayers.draw(pass, layerSlot++, uploadedFor(layer.mesh), layer.alphas?.let(::uploadedAlphasFor), layer, draw, view)
            }
            for (layer in view.fur) {
                layer.ink?.let { ribbons.draw(pass, ribbonSlot++, it, view) }
                for (fill in layer.fills) meshes.draw(pass, slot++, uploadedFor(fill.mesh), fill, view)
            }
            for (ribbon in view.ribbons) ribbons.draw(pass, ribbonSlot++, ribbon, view)
            for (image in view.over) images.draw(pass, imageSlot++, image, view)
        }
        pass.setViewport(0f, 0f, frame.width.toFloat(), frame.height.toFloat())
        pass.setScissorRect(0, 0, frame.width, frame.height)
        frame.drawOverlay(pass, state.overlay)
        pass.end()
        return false
    }

    private fun uploadedFor(mesh: RenderMesh): GpuMesh = uploaded.getOrPut(mesh) { GpuMesh(context, mesh) }

    private fun uploadedAlphasFor(alphas: FloatArray): GpuBuffer = uploadedAlphas.getOrPut(alphas) { alphaBuffer(context, alphas) }

    private fun keepOnly(frame: SceneFrame) {
        val wanted = IdentityHashMap<RenderMesh, Unit>()
        val wantedAlphas = IdentityHashMap<FloatArray, Unit>()
        for (view in frame.views) {
            for (draw in view.draws) {
                wanted[draw.mesh] = Unit
                for (layer in draw.layers) {
                    wanted[layer.mesh] = Unit
                    layer.alphas?.let { wantedAlphas[it] = Unit }
                }
            }
        }
        for (view in frame.views) for (layer in view.fur) for (fill in layer.fills) wanted[fill.mesh] = Unit
        uploaded.keys.filter { it !in wanted }.forEach { uploaded.remove(it)?.close() }
        uploadedAlphas.keys.filter { it !in wantedAlphas }.forEach { uploadedAlphas.remove(it)?.close() }
        images.keepOnly(frame.views.flatMap { view -> (view.behind + view.over).map { it.pixels } })
        ribbons.keepOnly(frame.views.flatMap { view -> view.ribbons.map { it.ribbon } + view.fur.mapNotNull { it.ink?.ribbon } })
    }

    override fun close() {
        uploaded.values.forEach { it.close() }
        uploaded.clear()
        uploadedAlphas.values.forEach { it.close() }
        uploadedAlphas.clear()
        meshes.close()
        colorLayers.close()
        ribbons.close()
        images.close()
    }
}
