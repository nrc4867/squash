package dev.chieppa.squash.character

import dev.chieppa.squash.character.block.Area
import dev.chieppa.squash.character.build.PartColorInput
import dev.chieppa.squash.character.build.ResolvedBrushStroke
import dev.chieppa.squash.character.build.ResolvedPoint
import dev.chieppa.squash.character.build.ResolvedRegion
import dev.chieppa.squash.character.build.ResolvedSurfaceWork
import dev.chieppa.squash.character.build.ShapeSource
import dev.chieppa.squash.character.build.SocketEnd
import dev.chieppa.squash.character.build.colorShapes
import dev.chieppa.squash.character.catalog.newCharacter
import dev.chieppa.squash.character.catalog.startingData
import dev.chieppa.squash.character.lint.TemplateLint
import dev.chieppa.squash.character.model.AreaTag
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.Span
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.BrushStrokeId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.RegionId
import dev.chieppa.squash.document.SetAreaFill
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.geom.BorderClosing
import dev.chieppa.squash.geom.ColorShape
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.surfaceAt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The sections Fill colors, as the shipped templates and plan name them, and how shapes cross a socket. */
@OptIn(ExperimentalCoroutinesApi::class)
class FillSectionsTest {
    private fun template(id: String): PartTemplate = assertNotNull(shipped.parts.firstOrNull { it.id == TemplateId(id) })

    private fun areas(id: String) = template(id).blocks.flatMap { it.areas() }

    private fun point(part: PartId, u: Float, v: Float) = ResolvedPoint(Anchor(part, BODY_PATCH, u, v), Vec3.ZERO, Vec3.Y)

    private val parent = PartId("parent")
    private val child = PartId("child")
    private val slot = SlotName("cream")

    @Test
    fun everyShippedTemplateNamesSectionsThatLint() {
        val names = listOf("ear", "head", "leg", "paw", "tail", "torso").associateWith { id -> areas(id).map { it.name } }
        assertEquals(listOf("inner", "back", "tip"), names["ear"])
        assertEquals(listOf("muzzle", "chin", "cheeks", "crown"), names["head"])
        assertEquals(listOf("knee", "sock", "thigh"), names["leg"])
        assertEquals(listOf("sock", "top", "toes"), names["paw"])
        assertEquals(listOf("tip", "base", "underside"), names["tail"])
        assertEquals(listOf("chest", "belly", "saddle", "rump"), names["torso"])
        for (id in names.keys) assertEquals(emptyList(), TemplateLint.lint(template(id)), "$id lints clean")
    }

    /** The ear's lens turns its flat faces to the body's front at v = 0 and its back at v = 0.5, with its edges between. */
    @Test
    fun theEarsInnerAreaIsTheFaceTowardTheFront() {
        val ear = template("ear")
        val inner = areas("ear").first { it.name == "inner" }
        val mesh = earMesh(ear)
        val patch = mesh.first { it.name == BODY_PATCH }
        val middle = (inner.u.start + inner.u.end) / 2f
        val center = patch.surfaceAt(middle, ((inner.v.start + inner.vEnd) / 2f) % 1f).normal
        val across = patch.surfaceAt(middle, 0.5f).normal
        assertEquals(AreaTag.INNER, inner.tag)
        assertTrue((center dot Vec3.X) > 0.8f, "the area is centred on the face toward the body's front: $center")
        assertTrue((center dot across) < -0.8f, "the back's area faces the other way: $center, $across")
        assertEquals("inner-ear", inner.color)
    }

    private fun earMesh(ear: PartTemplate) = dev.chieppa.squash.character.build.PartBuilder.build(ear, dev.chieppa.squash.document.PartData())

    @Test
    fun aNewCharacterStartsWithThePlansPaletteAndBase() {
        val plan = assertNotNull(shipped.plans.firstOrNull())
        val made = newCharacter(plan, shipped, "Test")
        assertEquals(plan.palette.map { it.name }, made.document.character.palette.slots.map { it.name.value })
        assertEquals(listOf("fur", "cream", "pad", "nose", "inner-ear", "ink"), plan.palette.map { it.name })
        assertEquals(SlotName("fur"), plan.startingData().surface.baseSlot)
    }

    @Test
    fun aFilledAreaLandsOnlyOnItsPatchAndAFillRenamesAColoredBlocksSlot() = runTest {
        val session = surfaceSession(torsoPart)
        session.send(SetAreaFill(torsoPart.id, "chest", slot))
        session.settle()
        val cuts = assertNotNull(session.heldColor(torsoPart.id).output.value).cuts
        assertEquals(listOf<ShapeSource?>(ShapeSource.Area(torsoPart.id, "chest")), cuts.map { it.source })
    }

    @Test
    fun aBorderRunningOffAChildClosesPastItsSocketRingAndOnTheParentOnItself() {
        val around = listOf(point(child, 0.05f, 0.1f), point(child, 0.3f, 0.2f), point(child, 0.05f, 0.3f), point(parent, 0.8f, 0.4f), point(parent, 0.9f, 0.5f), point(parent, 0.8f, 0.6f))
        val work = ResolvedSurfaceWork(regions = listOf(ResolvedRegion(RegionId("sock"), around, slot, false)))
        val input = PartColorInput(socketEnds = mapOf(child to SocketEnd(parent, BorderClosing.PAST_FIRST_RING)))
        val shapes = colorShapes(child, work, input, emptyMap())
        val closing = shapes.associate { it.part to (it.shape as ColorShape.Border).closing }
        assertEquals(BorderClosing.PAST_FIRST_RING, closing[child])
        assertEquals(BorderClosing.ON_ITSELF, closing[parent], "ends on a parent near its last ring do not run off it")
    }

    @Test
    fun aStrokesRunsStayOpenWhereTheStrokeGoesOn() {
        val points = listOf(point(child, 0.3f, 0.2f), point(child, 0.1f, 0.2f), point(parent, 0.8f, 0.4f), point(parent, 0.7f, 0.4f))
        val work = ResolvedSurfaceWork(strokes = listOf(ResolvedBrushStroke(BrushStrokeId("across"), points, slot, 0.1f, 0.8f)))
        val shapes = colorShapes(child, work, PartColorInput(), emptyMap()).map { it.shape as ColorShape.Brush }
        assertEquals(listOf(false, true), shapes.map { it.openStart })
        assertEquals(listOf(true, false), shapes.map { it.openEnd })
        assertFalse(shapes.first().openStart)
    }

    @Test
    fun anAreaWhoseEndIsBelowItsStartCrossesTheSeam() {
        val saddle = areas("torso").first { it.name == "saddle" }
        assertEquals(1.15f, saddle.vEnd, 1e-5f)
        val empty = Area("none", u = saddle.u, v = Span(0.5f, 0.5f), tag = AreaTag.FILL)
        val shipping = template("torso")
        val findings = TemplateLint.lint(shipping.copy(blocks = shipping.blocks + empty))
        assertTrue(findings.any { it.message.contains("holds nothing") }, "$findings")
    }
}
