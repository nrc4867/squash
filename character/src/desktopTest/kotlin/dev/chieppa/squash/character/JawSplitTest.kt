package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.JAW_JOINT
import dev.chieppa.squash.character.build.JointTree
import dev.chieppa.squash.character.build.RigParams
import dev.chieppa.squash.character.catalog.newCharacter
import dev.chieppa.squash.character.face.newMouthCommand
import dev.chieppa.squash.character.mouth.JawSplit
import dev.chieppa.squash.character.mouth.jawLimitOf
import dev.chieppa.squash.character.mouth.jawSplitOf
import dev.chieppa.squash.character.mouth.openJawGroup
import dev.chieppa.squash.character.mouth.withOpenJaws
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SetBaseSlot
import dev.chieppa.squash.document.SetBoneAngle
import dev.chieppa.squash.document.SetPaletteSlot
import dev.chieppa.squash.document.SlotColor
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.blendedSlot
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.isMouth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private val HEAD = PartId("head")
private val FUR = SlotName("fur")
private val INK = SlotName("ink")

/** A head's jaw splits it along the mouth line its template and style give, or the one it draws, and opens no wider than they allow. */
class JawSplitTest {
    private val library = shipped.library()

    /** A character on [plan] with its head, the newest [head] template, on the root's neck. */
    private fun made(plan: String, head: String): SquashDocument {
        var next = 0
        val made = newCharacter(shipped.plans.single { it.id.value == plan }, shipped, plan) { "id-${next++}" }
        val template = shipped.parts.filter { it.id.value == head }.maxBy { it.version }
        val parts = AddParts(listOf(PartInstance(HEAD, template.ref, SocketRef(made.root, "neck"), role = Role.HEAD)))
        return (made.commands + parts).fold(made.document) { document, command -> command.apply(document).document }
    }

    private fun SquashDocument.head(): PartInstance = checkNotNull(character.part(HEAD))

    private fun SquashDocument.split(): JawSplit? = head().let { head -> jawSplitOf(character, head, checkNotNull(library.find(head.template)), library) }

    @Test
    fun aMuzzleWithNoLineDrawnSplitsAlongTheLineItsTemplateGives() {
        val document = made("canine-quadruped", "head")
        val split = assertIs<JawSplit.Inferred>(document.split())
        assertEquals(2, split.lines.size, "a muzzle's line runs back along both its sides")
        assertTrue(split.lines.flatten().all { it.patch == "muzzle" && it.part == document.head().id })
        assertTrue(split.lines.all { line -> line.first().u > line.last().u }, "each side's line runs from the tip back")
    }

    @Test
    fun aFaceWithNoLineDrawnSplitsAcrossItsMouthLandmark() {
        val document = made("biped", "head-round")
        val split = assertIs<JawSplit.Inferred>(document.split())
        val line = split.lines.single()
        assertTrue(line.all { it.patch == BODY_PATCH })
        assertEquals(0.25f, line[line.size / 2].u, 1e-4f, "the line's middle lies on the mouth landmark")
    }

    @Test
    fun aMouthGroupThatDrawsNoLineTakesTheTemplatesLine() {
        val document = made("canine-quadruped", "head").let { newMouthCommand(it.head().id).apply(it).document }
        val split = assertIs<JawSplit.Inferred>(document.split())
        assertNotNull(split.group, "the head's own group carries the line")
    }

    @Test
    fun aBeakOpensOnItsOwnPartAndNotByASplit() {
        val bird = made("bird", "head-bird")
        val head = bird.head()
        val withBeak = AddParts(listOf(PartInstance(PartId("beak"), template("beak").ref, SocketRef(head.id, "muzzle"), role = Role.JAW))).apply(bird).document
        assertNull(withBeak.split())
        assertEquals(0f, jawLimitOf(withBeak.character, withBeak.head(), library), "a head that cannot split holds its jaw shut")
    }

    @Test
    fun theJawOpensAsFarAsItsStyleOrItsOwnLimitSays() {
        val quadruped = made("canine-quadruped", "head")
        assertEquals(0f, jawLimitOf(quadruped.character, quadruped.head(), library), "a short muzzle's style holds its jaw shut")
        val biped = made("biped", "head-round")
        assertEquals(15f, jawLimitOf(biped.character, biped.head(), library), "lips open less far than the template's jaw joint")
        val held = SetParameter(biped.head().id, RigParams.JAW, 12f).apply(biped).document
        assertEquals(12f, jawLimitOf(held.character, held.head(), library), "the character's own limit comes first")
    }

    @Test
    fun nothingOnTheHeadHangsFromItsJaw() {
        val head = shipped.parts.filter { it.id.value == "head" }.maxBy { it.version }
        val joints = JointTree(head, emptyList())
        assertEquals(JAW_JOINT, joints.chain.last().name)
        assertEquals(0, joints.carrierAt(0.95f), "a socket past the jaw's u hangs from the neck")
    }

    @Test
    fun aJawAtRestAddsNothingAndAnOpenOneAddsTheLineAsAMouth() {
        val document = made("canine-quadruped", "head")
        assertSame(document, document.withOpenJaws(library))
        val head = document.head().id
        val colored = listOf(SetPaletteSlot(FUR, SlotColor(0.9f, 0.6f, 0.3f)), SetPaletteSlot(INK, SlotColor(0.1f, 0.1f, 0.1f)), SetBaseSlot(head, FUR))
            .fold(document) { held, command -> command.apply(held).document }
        val held = SetBoneAngle(head, JAW_JOINT, 10f).apply(colored).document
        assertSame(held, held.withOpenJaws(library), "a jaw its style holds shut adds nothing however it is posed")
        val posed = SetParameter(head, RigParams.JAW, 20f).apply(held).document
        val shown = posed.withOpenJaws(library)
        val group = shown.character.resolved(shown.head()).surface.groups.single { it.isMouth }
        assertEquals(2, group.roles.single { it.role == MarkRole.LIP_LINE }.marks.size)
        assertEquals(blendedSlot(FUR, INK, 0.72f), group.roles.single { it.role == MarkRole.INNER }.fill, "the inside is the head's color taken toward the ink")
        assertEquals(group.id, openJawGroup(shown.character, shown.head(), library))
        assertNull(openJawGroup(document.character, document.head(), library))
    }
}
