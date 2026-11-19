package dev.chieppa.squash.recipe

import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.dress.placeOnBody
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.recipe.build.RecipeBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import java.io.File
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** How far, in body units, a printed ink line's place may lie from the body place it is anchored to: the cloth's ease and a few cells. */
private const val NEAR = 0.05f

/** Ink lines printed on a long-sleeved top lie on the cloth over the body place they name, on the arms and on the torso beside their roots. */
@Tag("integration")
@OptIn(ExperimentalCoroutinesApi::class)
class RecipeGarmentMarksTest {
    private val folder = TemplateFolder.shipped()
    private val built = RecipeBuilder(folder).build(RecipeJson.read(File(System.getProperty("squash.recipes"), "sleeve-marks.recipe.json").readText()))

    @Test
    fun printedLinesStayOnTheClothOverTheirPlaces() = runTest {
        val session = CharacterSession(built.document, TemplateLibrary(folder.parts, folder.plans), backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent()
        val garment = assertNotNull(session.sessionSurface.dress.output.value?.dress).garments.single { it.cover.garment.id == GarmentId("tee") }
        val body = assertNotNull(session.assembly.output.value)
        val marks = assertNotNull(garment.paint).marks.values.flatten()
        assertTrue(marks.isNotEmpty())
        var worst = 0f
        for (mark in marks) for (point in mark.points) {
            val body = assertNotNull(placeOnBody(body, point.anchor.copy(offset = 0f)))
            val d = point.position - body
            val away = sqrt(d.x * d.x + d.y * d.y + d.z * d.z)
            if (away > worst) worst = away
        }
        assertTrue(worst <= NEAR, "a line place lies $worst from its body place")
    }
}
