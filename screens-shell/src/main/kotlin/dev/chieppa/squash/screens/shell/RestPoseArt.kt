package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.AssemblyPart
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.render.ViewsScene
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.sheet.SheetLook
import dev.chieppa.squash.screens.sheet.rememberSheetLook
import dev.chieppa.squash.screens.sheet.restPoseFrame
import dev.chieppa.stretch.gpu.GpuOptions
import dev.chieppa.stretch.gpu.GpuSnapshot
import java.awt.image.BufferedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The size rest pose art renders at by default; an image scales it to fit, and it has the boards' proportions. */
const val ART_WIDTH = 1180
const val ART_HEIGHT = 840

/** Renders offscreen with the real scene, on a GPU device of its own for each image. */
object GpuRestPoseArtist : RestPoseArtist {
    override suspend fun render(frame: SceneFrame, width: Int, height: Int): ImageBitmap? = runCatching {
        val image = GpuSnapshot.render(frame, width, height, options = GpuOptions(onError = {})) { ViewsScene(it) }
        BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).apply { setRGB(0, 0, width, height, image.pixels, 0, width) }.toComposeImageBitmap()
    }.getOrNull()
}

/** [plan]'s whole character with only its root placed, or [document]'s when given, through the real build. */
fun restPoseOf(plan: BodyPlan, templates: TemplateFolder, document: SquashDocument? = null): AssembledCharacter? {
    val library = TemplateLibrary(templates.parts, templates.plans)
    if (document != null) {
        val character = document.character
        return assembleNow(assemblyParts(character), { character.resolved(it) ?: PartData() }, plan, library)
    }
    val root = templates.template(plan.root.template) ?: return null
    return assembleNow(listOf(AssemblyPart(PartId(ROOT_ID), root.ref, null, null, null)), { PartData() }, plan, library)
}

private const val ROOT_ID = "root"

/** The rest pose art for [plan] and [document], once rendered; every part dashed when [dashed]. Null while it renders, or where there is no artist. */
@Composable
fun rememberRestPoseArt(
    state: AppState,
    plan: BodyPlan?,
    document: SquashDocument? = null,
    dashed: Boolean = false,
    width: Int = ART_WIDTH,
    height: Int = ART_HEIGHT,
): ImageBitmap? {
    val look = rememberSheetLook()
    val art by produceState<ImageBitmap?>(null, plan, document, dashed, look, width, height) {
        value = if (plan == null) null else withContext(Dispatchers.Default) { renderArt(state, plan, document, dashed, look, width, height) }
    }
    return art
}

private suspend fun renderArt(state: AppState, plan: BodyPlan, document: SquashDocument?, dashed: Boolean, look: SheetLook, width: Int, height: Int): ImageBitmap? {
    val artist = state.artist ?: return null
    val character = restPoseOf(plan, state.templates, document) ?: return null
    return artist.render(restPoseFrame(character, look, width, height, dashed = dashed), width, height)
}

/** Rest pose art scaled to fit. */
@Composable
fun RestPoseImage(art: ImageBitmap, description: String, modifier: Modifier = Modifier) {
    Image(art, description, modifier, contentScale = ContentScale.Fit)
}
