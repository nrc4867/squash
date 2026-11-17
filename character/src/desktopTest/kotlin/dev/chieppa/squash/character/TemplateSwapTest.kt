package dev.chieppa.squash.character

import dev.chieppa.squash.character.catalog.newCharacter
import dev.chieppa.squash.character.catalog.openSockets
import dev.chieppa.squash.character.catalog.placeOnSocket
import dev.chieppa.squash.character.catalog.swapSeats
import dev.chieppa.squash.character.catalog.swapTemplateCommand
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.SwapFamily
import dev.chieppa.squash.character.model.swapsWith
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.SetRole
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A part changing to another template of its family: which sockets take what, where the parts on it go, and its role. */
class TemplateSwapTest {
    private val plan = shipped.plans.single { it.id == BodyPlanId("canine-quadruped") }

    private fun template(id: String): PartTemplate = assertNotNull(shipped.template(TemplateId(id)))

    private var made = 0

    private fun SquashDocument.place(socket: String, type: String, role: Role? = null): Pair<SquashDocument, List<PartInstance>> {
        val open = openSockets(this, plan, shipped).first { it.name == socket }
        val parts = placeOnSocket(open, template(type), this) { "$type-${made++}" }.map { if (role != null) it.copy(role = role) else it }
        return AddParts(parts).apply(this).document to parts
    }

    /** A torso with a pair of front legs, which a four-legged body has as arms, and a paw on each. */
    private fun legged(): Pair<SquashDocument, PartInstance> {
        val new = newCharacter(plan, shipped, "Fennec")
        val start = new.commands.fold(new.document) { document, command -> command.apply(document).document }
        val (withLegs, legs) = start.place("front-leg", "leg", Role.ARM)
        val (withPaws, _) = withLegs.place("paw", "paw")
        return withPaws to legs.first()
    }

    private fun SquashDocument.socketsOf(parent: PartInstance) =
        character.parts.filter { it.socket?.parent == parent.id || it.socket?.parent == character.twinOf(parent)?.id }.map { it.socket?.socket }

    @Test
    fun limbsSwapWithLimbsAndHandsWithFeetAndAGenericPartWithNothing() {
        assertTrue(template("leg").swapsWith(template("leg-bird")))
        assertTrue(template("leg").swapsWith(template("arm")))
        assertTrue(template("paw").swapsWith(template("hand")))
        assertFalse(template("leg").swapsWith(template("tail")))
        assertFalse(template("tube").swapsWith(template("tube")), "a generic template is in no family")
        assertEquals(SwapFamily.LIMB, SwapFamily.of(Role.ARM))
    }

    @Test
    fun anEmptyPlanSocketTakesEveryLimbWithItsOwnFirst() {
        val new = newCharacter(plan, shipped, "Fennec")
        val document = new.commands.fold(new.document) { document, command -> command.apply(document).document }
        val sockets = openSockets(document, plan, shipped)
        val leg = sockets.first { it.name == "front-leg" }
        assertEquals(TemplateId("leg"), leg.takes.first(), "the plan's own leg comes first")
        assertEquals(setOf("leg", "leg-bird", "arm"), leg.takes.map { it.value }.toSet())
        assertEquals(listOf("tail", "tail-feathers").toSet(), sockets.first { it.name == "tail" }.takes.map { it.value }.toSet())
    }

    @Test
    fun aLegsPawsMoveToTheSocketAtTheEndOfABirdLegOrAnArm() {
        val (document, leg) = legged()
        assertEquals(mapOf("paw" to "foot"), document.character.swapSeats(leg, template("leg"), template("leg-bird"), plan))
        assertEquals(mapOf("paw" to "hand"), document.character.swapSeats(leg, template("leg"), template("arm"), plan))
        assertNull(document.character.swapSeats(leg, template("leg"), template("tail"), plan), "a tail has nowhere for the paws")
        assertNull(document.character.swapSeats(leg, template("leg"), template("torso"), plan), "nor does a template of another family")
    }

    @Test
    fun aSwapIsOneStepThatKeepsThePairTheRoleAndThePawsAndUndoesWhole() {
        val (document, leg) = legged()
        val command = assertNotNull(document.character.swapTemplateCommand(leg, template("leg"), template("leg-bird"), plan))
        val applied = command.apply(document)
        val after = applied.document
        val legs = after.character.parts.filter { it.socket?.socket == "front-leg" }
        assertEquals(listOf("leg-bird", "leg-bird"), legs.map { it.template.id.value }, "both sides of the pair change")
        assertEquals(1, legs.mapNotNull { it.pair }.distinct().size, "and stay one pair")
        assertEquals(listOf(Role.ARM, Role.ARM), legs.map { it.role }, "a front leg stays an arm")
        assertEquals(listOf("foot", "foot"), after.socketsOf(leg), "each paw sits on its bird leg's foot socket")
        assertEquals(legs.map { it.id }.toSet(), after.character.parts.filter { it.socket?.socket == "foot" }.map { it.socket?.parent }.toSet())
        assertEquals(document, applied.undo.apply(after).document)
    }

    @Test
    fun aRoleOfAnotherFamilyTakesTheTemplatesOwn() {
        val (document, leg) = legged()
        val tagged = SetRole(leg.id, Role.TAIL).apply(document).document
        val command = assertNotNull(tagged.character.swapTemplateCommand(assertNotNull(tagged.character.part(leg.id)), template("leg"), template("arm"), plan))
        assertEquals(Role.ARM, command.apply(tagged).document.character.part(leg.id)?.role)
    }

    @Test
    fun aMovedSocketKeepsItsPlaceUnderItsNewName() {
        val (document, leg) = legged()
        val moved = SetParameter(leg.id, PlacementParams.socketU("paw"), 0.9f).apply(document).document
        val after = assertNotNull(moved.character.swapTemplateCommand(assertNotNull(moved.character.part(leg.id)), template("leg"), template("arm"), plan)).apply(moved).document
        val params = after.character.resolvedShape(assertNotNull(after.character.part(leg.id))).params
        assertEquals(0.9f, params[PlacementParams.socketU("hand")])
        assertNull(params[PlacementParams.socketU("paw")])
    }
}
