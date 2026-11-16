package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.AssemblyPart
import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.Holed
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.branchOver
import dev.chieppa.squash.character.build.throughHoles
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.GarmentSlot
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.Outfit
import dev.chieppa.squash.document.OutfitId
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.Wardrobe
import dev.chieppa.squash.document.cuffPartId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertSame

/** Parts a garment owns: built only while it is worn, and standing outside every garment, never cut through or hidden by one. */
class OwnedPartsTest {
    private val plan = shipped.plans.single { it.id.value == "canine-quadruped" }
    private val library = TemplateLibrary(shipped.parts, shipped.plans)
    private val torso = PartId("torso")
    private val head = PartId("head")
    private val brim = PartId("brim")
    private val hat = GarmentId("hat")
    private val headSlot = GarmentSlotId("head")
    private val worn = OutfitId("worn")
    private val bare = OutfitId("bare")
    private fun ref(id: String) = library.latest(TemplateId(id))!!.ref

    /** A hand-made socket on the head, inside the region the hat's cap covers. */
    private val socket = HandSockets.add(head, 1, 0.3f, 0f, 0f, paired = false).values.mapNotNull { (k, v) -> v?.let { k to it } }.toMap()
    private val cap = Garment(hat, "Hat", headSlot, branches = listOf(GarmentBranch(head, 0f, 0.45f, fromV = -0.22f, toV = 0.22f)))

    private fun body(owner: GarmentId?): AssembledCharacter = assembleNow(
        listOf(
            AssemblyPart(torso, ref("torso"), null, null, null),
            AssemblyPart(head, ref("head"), SocketRef(torso, "neck"), null, null, placement = socket),
            AssemblyPart(brim, ref("blob"), SocketRef(head, HandSockets.name(1)), null, null, role = Role.OTHER, owner = owner),
        ),
        { PartData() }, plan, library,
    )

    @Test
    fun aGarmentCutsNoHoleForAPartItOwns() {
        fun holed(character: AssembledCharacter): Holed {
            val placed = assertNotNull(branchOver(cap, cap.branches.single(), assertNotNull(character.part(head))))
            return throughHoles(cap, placed, character, emptyMap()) {}
        }
        val owned = holed(body(hat))
        assertTrue(owned.cuffs.isEmpty() && brim !in owned.through, "no hole or cuff round an owned part")
        val plain = holed(body(null))
        assertTrue(brim in plain.through, "a body part there comes through")
        assertNotNull(plain.cuffs.singleOrNull { it.id == cuffPartId(hat, brim) }, "with a cuff")
    }

    @Test
    fun anOwnedPartIsAssembledOnlyWhileItsGarmentIsWorn() {
        val wardrobe = Wardrobe(
            garments = listOf(cap),
            slots = listOf(GarmentSlot(headSlot, "Head")),
            outfits = listOf(Outfit(worn, "Worn", mapOf(headSlot to hat)), Outfit(bare, "Bare")),
            shown = worn,
        )
        val character = Character(
            CharacterId("c"), "C", BodyPlanRef(BodyPlanId("canine-quadruped"), 1),
            parts = listOf(
                PartInstance(torso, ref("torso")),
                PartInstance(head, ref("head"), SocketRef(torso, "neck")),
                PartInstance(brim, ref("blob"), SocketRef(head, HandSockets.name(1)), role = Role.OTHER, garment = hat),
            ),
            wardrobe = wardrobe,
        )
        assertEquals(hat, assemblyParts(character).single { it.id == brim }.owner)
        assertEquals(listOf(torso, head), assemblyParts(character.copy(wardrobe = wardrobe.copy(shown = bare))).map { it.id })
        assertSame(null, assemblyParts(character).single { it.id == head }.owner)
    }
}
