package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.pointOnView
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.MeshInvariants
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** A drawn outline through the real build. See HLD: data-model, Rules the code keeps. */
class DrawnPartTest {
    private val leg = template("leg")
    private val axes = PartBuilder.axes(leg)

    /** Bent at the hock: the midline moves from 0 to -0.2 and back toward -0.1 along the part's up. */
    private val bentSide = outline(
        0f to 0.2f, 0.4f to 0.15f, 0.6f to -0.05f, 1f to 0f,
        1f to -0.2f, 0.6f to -0.35f, 0.4f to -0.15f, 0f to -0.2f,
    )
    private val top = outline(0f to 0.3f, 0.5f to 0.3f, 1f to 0.1f, 1f to -0.1f, 0.5f to -0.3f, 0f to -0.3f)

    private fun body(vararg outlines: Pair<View, dev.chieppa.squash.document.OutlineCurve>, id: String = "leg"): Patch =
        PartBuilder.build(template(id), outlines = mapOf(*outlines)).first()

    private fun List<Vec3>.spanOn(direction: Vec3) = maxOf { it dot direction } - minOf { it dot direction }

    private fun assertHolds(id: String, patches: List<Patch>) {
        val template = template(id)
        val mesh = PartBuilder.mesh(id, template, patches)
        for (patch in mesh.patches) {
            assertEquals(emptyList(), MeshInvariants.check(patch, PartBuilder.openRings(template, patch.patch)), id)
            if (PartBuilder.openRings(template, patch.patch).isEmpty()) assertTrue(MeshInvariants.signedVolume(patch) > 0f, patch.patch.name)
        }
    }

    @Test
    fun aSideOutlineAloneBuildsTheFixedTopology() {
        val patches = PartBuilder.build(leg, outlines = mapOf(View.SIDE to bentSide))
        val undrawn = PartBuilder.build(leg).single()
        val patch = patches.single()
        assertEquals(undrawn.nu, patch.nu)
        assertEquals(undrawn.nv, patch.nv)
        assertEquals(undrawn.slices!!.map { it.u }, patch.slices!!.map { it.u })
        val mesh = PartBuilder.mesh("leg", leg, patches).patches.single()
        val empty = PartBuilder.mesh("leg", leg, listOf(undrawn)).patches.single()
        assertEquals(empty.indices.toList(), mesh.indices.toList())
        assertHolds("leg", patches)
    }

    @Test
    fun theAxisFollowsTheSideMidlineAndTheExtentSetsTheHeight() {
        val slices = body(View.SIDE to bentSide).slices!!
        val hock = slices.minBy { kotlin.math.abs(it.u - 0.4f) }
        assertEquals(0f, hock.frame.center dot axes.up, 2e-2f)
        val low = slices.minBy { kotlin.math.abs(it.u - 0.6f) }
        assertEquals(-0.2f, low.frame.center dot axes.up, 2e-2f)
        assertEquals(1f, slices.last().frame.center dot axes.along, 1e-4f)
        assertEquals(0.3f, low.height, 2e-2f)
    }

    @Test
    fun aMissingViewTakesTheOthersExtent() {
        val patch = body(View.SIDE to bentSide)
        val middle = patch.ring(6)
        assertEquals(middle.spanOn(axes.up), middle.spanOn(axes.side), 2e-2f)
    }

    @Test
    fun topAddsWidthAndLeavesTheSideAlone() {
        val sideOnly = body(View.SIDE to bentSide)
        val both = body(View.SIDE to bentSide, View.TOP to top)
        val ring = both.ring(6)
        assertEquals(0.6f, ring.spanOn(axes.side), 2e-2f)
        assertNotEquals(sideOnly.ring(6).spanOn(axes.side), ring.spanOn(axes.side))
        assertEquals(sideOnly.ring(6).spanOn(axes.up), ring.spanOn(axes.up), 1e-4f)
        assertHolds("leg", listOf(both))
    }

    @Test
    fun topAloneSetsWidthAndAStraightAxis() {
        val patch = body(View.TOP to top)
        assertEquals(1f, patch.slices!!.last().frame.center dot axes.along, 1e-3f)
        assertEquals(0f, patch.slices!![6].frame.center dot axes.up, 1e-3f)
        assertEquals(0.6f, patch.ring(6).spanOn(axes.side), 2e-2f)
    }

    @Test
    fun theOpenEndSitsOnTheSocketPlaneEvenWhenTheStrokeCrossesIt() {
        val crossing = outline(-0.3f to 0.4f, 1f to 0.2f, 1f to -0.2f, -0.3f to -0.6f)
        for (ring in listOf(body(View.SIDE to crossing).ring(0), body(View.SIDE to bentSide).ring(0))) {
            for (p in ring) assertEquals(0f, p dot axes.along, 1e-4f)
        }
        val patches = PartBuilder.build(leg, outlines = mapOf(View.SIDE to crossing))
        assertEquals(setOf(0), PartBuilder.openRings(leg, patches.single()))
        assertHolds("leg", patches)
    }

    @Test
    fun aStrokeBehindTheSocketPlaneSetsNothing() {
        val behind = outline(-1f to 0.2f, -0.2f to 0.2f, -0.2f to -0.2f, -1f to -0.2f)
        assertEquals(PartBuilder.build(leg), PartBuilder.build(leg, outlines = mapOf(View.SIDE to behind)))
    }

    @Test
    fun theSizeParameterMultipliesTheDrawing() {
        val slices = PartBuilder.build(leg, mapOf("size" to 2f), mapOf(View.SIDE to bentSide)).single().slices!!
        assertEquals(2f, slices.last().frame.center dot axes.along, 1e-3f)
    }

    @Test
    fun aFrontOutlineShapesOnlyTheCapWhereTheTemplateSaysCap() {
        val side = body(View.SIDE to bentSide)
        val tall = outline(0f to 1f, 0.3f to 0f, 0f to -1f, -0.3f to 0f, 0f to 1f)
        val capped = body(View.SIDE to bentSide, View.FRONT to tall)
        val rings = side.slices!!.size
        assertEquals(side.slices!!.take(rings * 2 / 3), capped.slices!!.take(rings * 2 / 3))
        assertNotEquals(side.slices!![rings * 7 / 8], capped.slices!![rings * 7 / 8])
        assertHolds("leg", listOf(capped))
    }

    @Test
    fun aFrontOutlineSetsTheWholeSectionWhereTheTemplateSaysSection() {
        val flat = outline(0f to 0.5f, 1f to 0f, 0f to -0.5f, -1f to 0f, 0f to 0.5f)
        val patch = body(View.SIDE to outline(0f to 0.2f, 1f to 0.2f, 1f to -0.2f, 0f to -0.2f), View.FRONT to flat, id = "torso")
        val middle = patch.ring(16)
        val torsoAxes = PartBuilder.axes(template("torso"))
        assertEquals(0.4f, middle.spanOn(torsoAxes.up), 2e-2f)
        assertEquals(0.8f, middle.spanOn(torsoAxes.side), 8e-2f)
    }

    @Test
    fun aPartBuildsFromWhatItResolvesTo() {
        val data = PartData(mapOf(View.SIDE to bentSide, View.TOP to top), mapOf("size" to 1.5f))
        val expected = PartBuilder.build(leg, mapOf("size" to 1.5f), mapOf(View.SIDE to bentSide, View.TOP to top))
        assertEquals(expected, PartBuilder.build(leg, data))
    }

    @Test
    fun changingASpanOfTheOutlineLeavesTheOtherSlicesExactlyAsTheyWere() {
        val before = outline(0f to 0.2f, 0.3f to 0.2f, 0.45f to 0.2f, 0.55f to 0.2f, 0.7f to 0.2f, 1f to 0f, 1f to -0.2f, 0f to -0.2f)
        val after = outline(0f to 0.2f, 0.3f to 0.2f, 0.45f to 0.2f, 0.5f to 0.35f, 0.55f to 0.2f, 0.7f to 0.2f, 1f to 0f, 1f to -0.2f, 0f to -0.2f)
        val old = body(View.SIDE to before).slices!!
        val new = body(View.SIDE to after).slices!!
        val reach = 2f / 64f + 1f / 128f
        val outsidePoints = old.indices.filter { old[it].u < 0.45f - 1f / 128f || old[it].u > 0.55f + 1f / 128f }
        for (i in outsidePoints) assertEquals(old[i].frame to old[i].height, new[i].frame to new[i].height, "ring $i at u = ${old[i].u}")
        val outside = old.indices.filter { old[it].u < 0.45f - reach || old[it].u > 0.55f + reach }
        assertTrue(outside.size > 16)
        for (i in outside) assertEquals(old[i], new[i], "ring $i at u = ${old[i].u}")
        assertTrue(old.indices.any { old[it] != new[it] })
    }

    @Test
    fun everyTemplateBuildsWatertightFromDrawnViews() {
        val side = outline(0f to 0.25f, 0.5f to 0.3f, 0.9f to 0.2f, 1f to 0f, 0.9f to -0.2f, 0.5f to -0.3f, 0f to -0.25f)
        val width = outline(0f to 0.2f, 0.5f to 0.25f, 0.9f to 0.15f, 1f to 0f, 0.9f to -0.15f, 0.5f to -0.25f, 0f to -0.2f)
        val front = outline(0f to 1f, 0.8f to 0f, 0f to -1f, -0.8f to 0f, 0f to 1f)
        for (template in shipped.parts) {
            val id = template.id.value
            val views = listOf(
                mapOf(View.SIDE to side),
                mapOf(View.TOP to width),
                mapOf(View.SIDE to side, View.TOP to width),
                mapOf(View.SIDE to side, View.TOP to width, View.FRONT to front),
                mapOf(View.FRONT to front),
            )
            for (drawn in views) assertHolds(id, PartBuilder.build(template, outlines = drawn))
        }
    }

    @Test
    fun aViewPointLandsInThePartsAxes() {
        assertEquals(axes.point(0.5f, 0.2f, 0f), axes.pointOnView(View.SIDE, 0.5f, 0.2f))
        assertEquals(axes.point(0.5f, 0f, -0.2f), axes.pointOnView(View.TOP, 0.5f, 0.2f))
        assertEquals(axes.point(0f, 0.2f, 0.5f), axes.pointOnView(View.FRONT, 0.5f, 0.2f))
    }
}
