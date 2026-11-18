package dev.chieppa.squash.screens.dress

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.AddGarment
import dev.chieppa.squash.document.AddGarmentSlot
import dev.chieppa.squash.document.AddOutfit
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.GarmentSlot
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.Outfit
import dev.chieppa.squash.document.OutfitId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.PickGarment
import dev.chieppa.squash.document.RenameGarment
import dev.chieppa.squash.document.RenameOutfit
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SetHemInk
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.Together
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.shell.caninePlan
import dev.chieppa.squash.screens.shell.shipped
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

private val HEAD = GarmentSlotId("head")
private val BODY = GarmentSlotId("body")
private val LOOSE = GarmentId("loose")
private val HAT = GarmentId("hat")
private val CAP = GarmentId("cap")
private val COAT = GarmentId("coat")
private val WINTER = OutfitId("winter")
private val BARE = OutfitId("bare")
private val OUTFIT_LOOK = WardrobeLook(40, 30, Color.White, Color.Gray)
private val GARMENT_LOOK = WardrobeLook(40, 20, Color.White, Color.Gray)
private val LOOKS = WardrobeLooks(OUTFIT_LOOK, GARMENT_LOOK)

/** Renders nothing but a pixel, keeping what it is asked for in order; with a [gate] each render waits for it. */
private class CountingArtist : WardrobeArtist {
    val scenes = mutableListOf<WardrobeScene>()
    val looks = mutableListOf<WardrobeLook>()
    var gate: CompletableDeferred<Unit>? = null
    var cancelled = 0

    override suspend fun render(scene: WardrobeScene, templates: TemplateLibrary, look: WardrobeLook): ImageBitmap {
        try {
            gate?.await()
        } catch (stop: CancellationException) {
            cancelled++
            throw stop
        }
        scenes += scene
        looks += look
        return ImageBitmap(1, 1)
    }
}

class WardrobeThumbnailsTest {
    private val work = TestWork()
    private val artist = CountingArtist()
    private val thumbnails = WardrobeThumbnails(artist, work.dispatcher)
    private var following: Job? = null

    /**
     * A bare new character with a head slot holding a hat and a cap, a body slot holding a coat, and a loose garment, in Winter (the hat
     * and the coat) and Bare, its thumbnails followed in [looks].
     */
    private fun dressed(looks: WardrobeLooks = LOOKS): CharacterSession {
        val state = AppState(shipped, work).also { it.createCharacter(caninePlan, "Fennec") }
        val session = assertNotNull(state.session.value)
        session.send(
            Together(
                listOf(
                    AddGarmentSlot(GarmentSlot(HEAD, "Head")),
                    AddGarmentSlot(GarmentSlot(BODY, "Body")),
                    AddGarment(Garment(HAT, "Hat", HEAD)),
                    AddGarment(Garment(CAP, "Cap", HEAD)),
                    AddGarment(Garment(COAT, "Coat", BODY)),
                    AddGarment(Garment(LOOSE, "Loose")),
                    AddOutfit(Outfit(WINTER, "Winter", mapOf(HEAD to HAT, BODY to COAT))),
                    AddOutfit(Outfit(BARE, "Bare")),
                    ShowOutfit(WINTER),
                ),
            ),
        )
        follow(session, looks)
        return session
    }

    private fun follow(session: CharacterSession, looks: WardrobeLooks) {
        following?.cancel()
        following = work.scope.launch { thumbnails.follow(session, looks) }
    }

    private val WardrobeScene.worn get() = document.character.wardrobe.worn.map { it.id }

    @Test
    fun everyOutfitAndSlottedGarmentRendersOnceAndKeepsItsThumbnailThroughRenamesAndOutfitSwitches() {
        val session = dressed()
        work.settle(session)
        val shots = listOf(WINTER, BARE).map { WardrobeShot.OfOutfit(it) } + listOf(HAT, CAP, COAT).map { WardrobeShot.OfGarment(it) }
        assertEquals(shots.toSet(), thumbnails.thumbs.value.keys, "a loose garment keeps its icon")
        assertEquals(5, artist.scenes.size)
        assertFalse(thumbnails.rendering.value)
        session.send(RenameOutfit(WINTER, "Snow"))
        session.send(RenameGarment(HAT, "Beanie"))
        session.send(ShowOutfit(BARE))
        work.settle(session)
        assertEquals(5, artist.scenes.size, "names and the outfit shown change no thumbnail")
    }

    @Test
    fun eachTileRendersInItsOwnKindsLook() {
        dressed().also(work::settle)
        val looks = artist.scenes.zip(artist.looks).associate { (scene, look) -> (scene.around == null) to look }
        assertEquals(mapOf(true to OUTFIT_LOOK, false to GARMENT_LOOK), looks)
    }

    @Test
    fun anEditToOneGarmentRendersOnlyItAndTheOutfitsWearingIt() {
        val session = dressed()
        work.settle(session)
        session.send(SetHemInk(HAT, false))
        work.settle(session)
        assertEquals(listOf(null, HAT), artist.scenes.drop(5).map { it.around }, "Winter, which wears the hat, and the hat; not the cap, the coat or Bare")
        session.send(SetHemInk(CAP, false))
        work.settle(session)
        assertEquals(listOf(CAP), artist.scenes.drop(7).map { it.around }, "no outfit wears the cap")
    }

    @Test
    fun nothingRendersWhileAGestureIsOpenAndOnlyWhatChangedRendersAfter() {
        val session = dressed()
        work.settle(session)
        val gesture = session.history.begin()
        gesture.update(PickGarment(WINTER, HEAD, CAP))
        work.settle(session)
        assertEquals(5, artist.scenes.size, "a drag renders nothing")
        gesture.commit()
        work.settle(session)
        assertEquals(6, artist.scenes.size, "Winter alone wears something else")
        assertEquals(listOf(CAP, COAT), artist.scenes.last().worn)
    }

    @Tag("integration")
    @Test
    fun anEditDropsTheRenderUnderWay() {
        val gate = CompletableDeferred<Unit>()
        artist.gate = gate
        val session = dressed()
        work.drain()
        assertTrue(thumbnails.thumbs.value.isEmpty())
        assertTrue(thumbnails.rendering.value)
        session.send(PickGarment(WINTER, HEAD, CAP))
        work.drain()
        assertEquals(1, artist.cancelled, "the edit cancels the render it finds under way")
        gate.complete(Unit)
        work.settle(session)
        assertEquals(5, thumbnails.thumbs.value.size)
        assertEquals(listOf(CAP, COAT), assertNotNull(thumbnails.thumbs.value[WardrobeShot.OfOutfit(WINTER)]).scene.worn)
    }

    @Test
    fun tilesOnScreenRenderFirst() {
        thumbnails.showing(WardrobeShot.OfGarment(COAT), true)
        thumbnails.showing(WardrobeShot.OfOutfit(BARE), true)
        thumbnails.showing(WardrobeShot.OfOutfit(BARE), false)
        work.settle(dressed())
        assertEquals(COAT, artist.scenes.first().around)
        assertEquals(listOf(null, null, HAT, CAP), artist.scenes.drop(1).map { it.around }, "then the rest in the drawer's order")
    }

    @Test
    fun aTileWaitsForItsSizeAndRendersAgainOnlyWhenItsSizeChangesMuch() {
        val session = dressed(WardrobeLooks(OUTFIT_LOOK, null))
        work.settle(session)
        assertEquals(listOf(null, null), artist.scenes.map { it.around }, "garment tiles wait to be laid out")
        follow(session, WardrobeLooks(OUTFIT_LOOK.copy(width = 44), GARMENT_LOOK))
        work.settle(session)
        assertEquals(5, artist.scenes.size, "a slightly wider outfit tile keeps its thumbnail")
        follow(session, WardrobeLooks(OUTFIT_LOOK.copy(width = 80), GARMENT_LOOK))
        work.settle(session)
        assertEquals(7, artist.scenes.size, "a much wider one renders again")
    }

    @Test
    fun aGarmentIsSeenAloneOnTheBareBodyAndALooseOneNotAtAll() {
        val session = dressed()
        val document = session.document.value
        val cap = assertNotNull(wardrobeScene(document, WardrobeShot.OfGarment(CAP)))
        assertEquals(CAP, cap.around)
        assertEquals(listOf(CAP), cap.worn, "nothing of the shown outfit is worn with it")
        assertEquals(listOf(CAP), cap.document.character.wardrobe.garments.map { it.id }, "and no other garment is held")
        assertNull(wardrobeScene(document, WardrobeShot.OfGarment(LOOSE)))
        val winter = assertNotNull(wardrobeScene(document, WardrobeShot.OfOutfit(WINTER)))
        assertEquals(listOf(HAT, COAT), winter.worn)
        assertEquals(cap.document, assertNotNull(wardrobeScene(RenameGarment(CAP, "Beret").apply(document).document, WardrobeShot.OfGarment(CAP))).document)
        val hatEdited = SetHemInk(HAT, false).apply(document).document
        assertEquals(cap, wardrobeScene(hatEdited, WardrobeShot.OfGarment(CAP)), "another garment's edit leaves it")
    }

    @Test
    fun aGarmentsSceneHoldsOnlyTheOwnedPartsOfWhatItWears() {
        val document = dressed().document.value
        val root = document.character.parts.first { it.socket == null }
        fun owned(id: String, garment: GarmentId) = PartInstance(PartId(id), root.template, SocketRef(root.id, "socket.1"), role = Role.OTHER, garment = garment)
        val withParts = AddParts(listOf(owned("brim", HAT), owned("collar", COAT))).apply(document).document
        val hat = assertNotNull(wardrobeScene(withParts, WardrobeShot.OfGarment(HAT)))
        assertEquals(listOf(PartId("brim")), hat.document.character.parts.filter { it.garment != null }.map { it.id })
        val winter = assertNotNull(wardrobeScene(withParts, WardrobeShot.OfOutfit(WINTER)))
        assertEquals(setOf(PartId("brim"), PartId("collar")), winter.document.character.parts.filter { it.garment != null }.map { it.id }.toSet())
    }
}
