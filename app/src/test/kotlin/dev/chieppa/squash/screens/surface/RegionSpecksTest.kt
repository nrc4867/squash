package dev.chieppa.squash.screens.surface

import dev.chieppa.squash.character.build.ShapeSource
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.cat.catDocument
import dev.chieppa.squash.screens.smoke.CloseUps
import dev.chieppa.squash.screens.woman.HAIR
import dev.chieppa.squash.screens.woman.womanDocument
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The most vertices a run of a region's values may hold and still be a speck: an island of its color or a pinhole in it. */
private const val SPECK_VERTICES = 12

/** How far, in cells along the surface, a part's paint may reach past the curve where a neighbour crosses it and takes the surface over. */
private const val OVERREACH_CELLS = 0.05f

/**
 * Regions and parts' paint end cleanly where parts share the surface: the cat's regions hold no scrap of color cut off from their region
 * and no pinhole of the base showing through, and the woman's hair ends on the curve where it crosses her head, where her ink is drawn.
 */
@Tag("integration")
class RegionSpecksTest {
    @Test
    fun theCatsRegionsHaveNoSpecksOrPinholes() {
        TestWork().use { work ->
            val session = CharacterSession(catDocument(), CloseUps.shippedLibrary(), work.scope, work.dispatcher)
            work.settle(session)
            val paint = assertNotNull(session.surfacePaint.output.value?.paint)
            assertTrue(paint.layers.any { it.source is ShapeSource.Border }, "the cat has painted regions")
            val render = assertNotNull(session.characterSurface.output.value?.surface).render
            val found = paint.layers.filter { it.source is ShapeSource.Border }.flatMap { layer ->
                val region = (layer.source as ShapeSource.Border).id.value
                val specks = LayerSpecks(layer, render)
                specks.islands.filter { it.vertices <= SPECK_VERTICES }.map { "region $region: an island of ${it.vertices} vertices at ${it.at}" } +
                    specks.pinholes.filter { it.vertices <= SPECK_VERTICES }.map { "region $region: a pinhole of ${it.vertices} vertices at ${it.at}" }
            }
            assertTrue(found.isEmpty(), "the cat's regions hold specks: $found")
        }
    }

    @Test
    fun theWomansHairEndsWhereItCrossesHerHead() {
        TestWork().use { work ->
            val session = CharacterSession(womanDocument(), CloseUps.shippedLibrary(), work.scope, work.dispatcher)
            work.settle(session)
            val paint = assertNotNull(session.surfacePaint.output.value?.paint)
            val surface = assertNotNull(session.characterSurface.output.value?.surface)
            val chart = surface.chart
            val members = chart.members(HAIR, surface.field.runs.first { it.partId == HAIR }.patch.name)
            val triangles = chart.trianglesOf(members)
            val hair = paint.layers.single { it.candidates.contentEquals(triangles) && it.source == null }
            val section = chart.layOn(members, triangles = triangles) { v, _, member -> chart.section(v, member) }
            val overreach = SectionOverreach(hair, section, surface)
            assertTrue(overreach.most <= OVERREACH_CELLS, "the hair reaches ${overreach.most} of a cell past her head's crossing, at ${overreach.at}")
            val specks = LayerSpecks(hair, surface.render)
            val found = specks.islands.filter { it.vertices <= SPECK_VERTICES } + specks.pinholes.filter { it.vertices <= SPECK_VERTICES }
            assertTrue(found.isEmpty(), "the hair holds specks at ${found.map { it.at }}")
        }
    }
}
