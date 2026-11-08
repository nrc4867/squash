package dev.chieppa.squash.screens.draw

import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.AddPart
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.PartMesh
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.shell.shipped
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first

private const val PIXEL = 0.005f
private const val SAMPLES = 300
private const val STROKE_LENGTH = 3f
private const val STROKE_HEIGHT = 0.8f

/** A stroke through the real cleanup, the real command and the real build, with nothing in between stubbed. */
class DrawnPartPipelineTest {
    private fun stroke(): List<CurvePoint> = List(SAMPLES) { i ->
        val angle = 2.0 * PI * i / SAMPLES
        CurvePoint(STROKE_LENGTH / 2f * (1f + cos(angle).toFloat()), STROKE_HEIGHT * sin(angle).toFloat())
    }

    @Test
    fun aDrawnSideOutlineBuildsTheLegWithItsFixedTopologyAndItsSize() {
        val leg = shipped.parts.first { it.id.value == "leg" }
        val plan = shipped.plans.first()
        val part = PartId("leg")
        val document = SquashDocument(Character(CharacterId("c"), "Fennec", BodyPlanRef(plan.id, plan.version)))
        val work = TestWork()
        try {
            val session = CharacterSession(document, TemplateLibrary(shipped.parts), work.scope, work.dispatcher)
            session.send(AddPart(PartInstance(part, leg.ref)))
            val mesh = session.mesh(part)
            fun built(): PartMesh {
                work.settle(session)
                return assertNotNull(mesh.output.value)
            }
            val before = built()

            val curve = assertNotNull(fittedOutline(stroke(), strokeEnds(View.SIDE, leg.openEnd), PIXEL)).curve
            session.send(SetOutline(part, View.SIDE, curve))
            val after = built()

            assertEquals(before.patches.map { it.patch.nu to it.patch.nv }, after.patches.map { it.patch.nu to it.patch.nv })
            assertFalse(before.patches.first().patch.points.contentEquals(after.patches.first().patch.points))
            val axes = PartBuilder.axes(leg)
            val body = after.patches.first().patch
            val along = (0 until body.nu * body.nv).map { body.point(it) dot axes.along }
            val up = (0 until body.nu * body.nv).map { body.point(it) dot axes.up }
            assertTrue(along.max() in STROKE_LENGTH * 0.9f..STROKE_LENGTH * 1.1f, "reaches ${along.max()}")
            assertTrue((up.max() - up.min()) in STROKE_HEIGHT * 1.6f..STROKE_HEIGHT * 2.4f, "height ${up.max() - up.min()}")
        } finally {
            work.close()
        }
    }
}
