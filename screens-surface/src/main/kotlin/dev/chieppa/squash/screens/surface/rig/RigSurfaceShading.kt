package dev.chieppa.squash.screens.surface.rig

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.rig.Rig
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.sheet.RigSurface
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The open character's one surface as the Rig stage colors it, by [rig]'s weights on it: each vertex in the mean of its parts' [colors]
 * weighed by its bones' weights, over its root part's color, or with [heat] the heat map of that bone; null until its skin is built.
 */
@Composable
fun rememberRigSurface(state: AppState, rig: Rig, colors: Map<PartId, Color>, heat: Int?): RigSurface? {
    val posing by state.posing.collectAsState()
    val node = posing?.surface?.skin ?: return null
    val held by remember(node) { node.output }.collectAsState()
    val posedNode by remember(posing) { posing?.surface?.posed?.output ?: MutableStateFlow(null) }.collectAsState()
    val skin = held?.skin ?: return null
    val surface = held?.surface ?: return null
    val root = surface.field.runs.firstOrNull { it.part.socket == null }?.partId ?: return null
    val base = colors[root] ?: return null
    val layers = remember(skin, rig, colors, heat) { surfaceSeamLayers(rig, skin, root, colors) + heat?.let { surfaceHeatLayers(skin, it) }.orEmpty() }
    val posed = posedNode?.posed?.surface?.render?.takeIf { it !== surface.render && it.vertexCount == surface.render.vertexCount }
    return remember(layers, surface, posed) { RigSurface(surface.parts, base, layers, surface.render, posed) }
}
