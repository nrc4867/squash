package dev.chieppa.squash.screens.surface.paint

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.character.build.ShapeSource
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetAreaFill
import dev.chieppa.squash.document.SetPaletteSlot
import dev.chieppa.squash.document.SlotColor
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.BodyBox
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.edit.FillReach
import dev.chieppa.squash.screens.fennec.CREAM
import dev.chieppa.squash.screens.fennec.EAR_L
import dev.chieppa.squash.screens.fennec.EAR_R
import dev.chieppa.squash.screens.fennec.FRONT_LEG_L
import dev.chieppa.squash.screens.fennec.FRONT_LEG_R
import dev.chieppa.squash.screens.fennec.FUR
import dev.chieppa.squash.screens.fennec.INNER_EAR
import dev.chieppa.squash.screens.fennec.TORSO
import dev.chieppa.squash.screens.fennec.savedFennec
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.PartAxes
import dev.chieppa.squash.screens.sheet.SheetFit
import dev.chieppa.squash.screens.sheet.SurfaceView
import dev.chieppa.squash.screens.surface.SurfaceHit
import dev.chieppa.squash.screens.surface.surfaceHit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PANE = 800

/** What a pick and Fill find on the fennec, through its real graph. */
class PaintSectionsTest {
    private val work = TestWork()
    private val folder = TemplateFolder.shipped()
    private val session = CharacterSession(savedFennec(), TemplateLibrary(folder.parts, folder.plans), work.scope, work.dispatcher)

    @AfterTest
    fun finish() = work.close()

    private fun settled() = work.settle(session)

    private fun assembled() = run {
        settled()
        assertNotNull(session.assembly.output.value)
    }

    /** A locked view along the body's side with the whole character in it. */
    private fun sideView(box: BodyBox): SurfaceView {
        val axes = PartAxes.of(Vec3.X)
        val fit = SheetFit(box.center, PANE / (box.radius * 2f), box.radius * 2f)
        return LockedCamera(View.SIDE, axes, fit, PANE, PANE)
    }

    private fun hitOn(id: PartId, sight: SurfaceView): SurfaceHit {
        val character = assembled()
        val center = assertNotNull(character.part(id)?.box).center
        return assertNotNull(surfaceHit(character, assertNotNull(sight.toPixel(center)), sight), "something shows at the middle of ${id.value}")
    }

    @Test
    fun aPickFindsTheNearestPartAndLeavesNoHitOffTheCharacter() {
        val character = assembled()
        val box = assertNotNull(character.box)
        val sight = sideView(box)
        assertEquals(TORSO, hitOn(TORSO, sight).part)
        assertNull(surfaceHit(character, Offset(1f, 1f), sight))
    }

    @Test
    fun aHitLandsBackAtItsPixel() {
        val character = assembled()
        val sight = sideView(assertNotNull(character.box))
        val hit = hitOn(TORSO, sight)
        val pixel = assertNotNull(sight.toPixel(hit.position))
        val again = assertNotNull(surfaceHit(character, pixel, sight))
        assertEquals(hit.u, again.u, 0.02f)
        assertEquals(hit.v, again.v, 0.02f)
    }

    @Test
    fun underThePointerFillsWhatShowsThereAndWholePartTheBase() {
        val character = assembled()
        val sight = sideView(assertNotNull(character.box))
        val hit = hitOn(TORSO, sight)
        val chest = hit.copy(u = 0.75f, v = 0.5f)
        val belly = hit.copy(u = 0.5f, v = 0.5f)
        assertEquals(Section.Named(TORSO, "belly"), session.sectionAt(belly, FillReach.UNDER_POINTER), "an area the template names counts though nothing has filled it")
        assertEquals(Section.Base(TORSO), session.sectionAt(belly, FillReach.WHOLE_PART))
        assertIs<Section.Border>(session.sectionAt(chest.copy(u = 0.85f), FillReach.UNDER_POINTER), "the fennec's chest is a region")
        assertEquals(Section.Base(TORSO), session.sectionAt(chest.copy(u = 0.85f), FillReach.WHOLE_PART))
    }

    @Test
    fun eraseFindsTheRegionOnTopAndTakesItAwayAsOneCommand() {
        val character = assembled()
        val sight = sideView(assertNotNull(character.box))
        val chest = hitOn(TORSO, sight).copy(u = 0.85f, v = 0.5f)
        val found = assertIs<ShapeSource.Border>(session.erasableAt(chest))
        assertNull(session.erasableAt(hitOn(TORSO, sight).copy(u = 0.5f, v = 0.25f)), "the base is no stroke or region")
        session.send(assertNotNull(session.eraseCommand(found, thisSide = false)))
        assertTrue(assertNotNull(session.document.value.character.resolved(TORSO)).surface.regions.none { it.id == found.id })
    }

    @Test
    fun aFilledAreaShowsUnderThePointerAndAcrossTheSeam() {
        session.send(SetAreaFill(TORSO, "saddle", CREAM))
        settled()
        val hit = hitOn(TORSO, sideView(assertNotNull(assembled().box)))
        assertEquals(Section.Named(TORSO, "saddle"), session.sectionAt(hit.copy(u = 0.5f, v = 0.95f), FillReach.UNDER_POINTER))
        assertEquals(Section.Named(TORSO, "saddle"), session.sectionAt(hit.copy(u = 0.5f, v = 0.05f), FillReach.UNDER_POINTER))
        assertEquals(CREAM, session.slotAt(hit.copy(u = 0.5f, v = 0.05f)))
        assertEquals(FUR, session.slotAt(hit.copy(u = 0.5f, v = 0.25f)))
    }

    @Test
    fun theEarsInnerAreaShowsItsSlotOnTheFaceTowardTheFront() {
        val hit = hitOn(EAR_L, sideView(assertNotNull(assembled().box)))
        assertEquals(INNER_EAR, session.slotAt(hit.copy(u = 0.5f, v = 0.02f)))
        assertEquals(FUR, session.slotAt(hit.copy(u = 0.5f, v = 0.5f)))
    }

    @Test
    fun aFillOnOneSideOfAPairShowsOnBothAndOnThisSideOnlyWithItsOverride() {
        val hit = hitOn(FRONT_LEG_L, sideView(assertNotNull(assembled().box)))
        session.send(SetPaletteSlot(SlotName("sock"), SlotColor(0.2f, 0.3f, 0.7f)))
        val sock = SlotName("sock")
        session.send(assertNotNull(session.fillCommand(Section.Named(FRONT_LEG_R, "sock"), sock, thisSide = false)))
        settled()
        val character = session.document.value.character
        assertEquals(sock, character.resolved(FRONT_LEG_L)?.surface?.areaFills?.get("sock"), "the left side reads the pair's fill")
        assertEquals(sock, character.resolved(FRONT_LEG_R)?.surface?.areaFills?.get("sock"))
        session.send(assertNotNull(session.fillCommand(Section.Base(FRONT_LEG_L), CREAM, thisSide = true)))
        settled()
        val after = session.document.value.character
        assertEquals(CREAM, after.resolved(FRONT_LEG_L)?.surface?.baseSlot)
        assertEquals(FUR, after.resolved(FRONT_LEG_R)?.surface?.baseSlot, "the other side keeps the pair's")
        assertEquals(CREAM, session.slotAt(hit.copy(u = 0.3f, v = 0.3f)))
    }

    @Test
    fun aFillOfARegionKeepsItsBorderAsThePairHoldsIt() {
        val before = assertNotNull(session.document.value.character.resolved(EAR_R)).surface.regions.single()
        val section = Section.Border(ShapeSource.Border(EAR_R, before.id))
        session.send(assertNotNull(session.fillCommand(section, CREAM, thisSide = false)))
        val character = session.document.value.character
        assertEquals(CREAM, character.resolved(EAR_L)?.surface?.regions?.single()?.slot)
        assertEquals(before.border, character.resolved(EAR_R)?.surface?.regions?.single()?.border)
    }

    @Test
    fun aDrawnLineKeepsOnlyThePointsItsShapeNeeds() {
        val straight = List(20) { Offset(it.toFloat() * 3f, 0f) }
        assertEquals(listOf(0, 19), keptAlong(straight, 1f))
        val corner = straight + List(20) { Offset(57f, it * 3f) }
        assertEquals(listOf(0, 19, 39), keptAlong(corner, 1f))
    }
}
