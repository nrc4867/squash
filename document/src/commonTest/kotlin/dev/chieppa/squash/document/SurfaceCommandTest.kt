package dev.chieppa.squash.document

import dev.chieppa.stretch.test.scenario
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SurfaceCommandTest {
    private val head = PartInstance(PartId("head-1"), TemplateRef(TemplateId("head"), 1))
    private val empty = SquashDocument(Character(CharacterId("fennec"), "Fennec", BodyPlanRef(BodyPlanId("canine-quadruped"), 1)))
    private val placed = AddPart(head).apply(empty).document
    private val fur = SlotName("fur")
    private val cream = SlotName("cream")

    private fun anchors(vararg vs: Float) = vs.map { Anchor(head.id, u = 0.5f, v = it) }
    private fun mark(id: String, vararg vs: Float) = Mark(MarkId(id), anchors(*vs))
    private fun region(id: String) = Region(RegionId(id), anchors(0.1f, 0.2f, 0.3f), fur)
    private fun stroke(id: String) = BrushStroke(BrushStrokeId(id), anchors(0.4f), cream, radius = 0.05f)
    private fun SquashDocument.surface() = checkNotNull(character.resolved(head.id)).surface
    private fun Mark.id() = id.value

    /** Runs [commands] one at a time, undoes them all back to [start], and redoes them to where they ended. */
    private fun assertUndoesAndRedoes(start: SquashDocument, vararg commands: DocumentCommand) {
        val end = commands.fold(start) { d, c -> c.apply(d).document }
        scenario(start) {
            run(*commands)
            expect(end)
            repeat(commands.size) { undo() }
            expect(start)
            repeat(commands.size) { redo() }
            expect(end)
        }
    }

    @Test
    fun aMarkUndoesToWhereItWas() {
        val a = mark("a", 0.1f, 0.2f)
        val b = mark("b", 0.3f, 0.4f)
        val c = mark("c", 0.5f, 0.6f)
        val all = listOf(SetMark(head.id, a.id, a), SetMark(head.id, b.id, b), SetMark(head.id, c.id, c))
            .fold(placed) { d, command -> command.apply(d).document }
        scenario(all) {
            run(SetMark(head.id, b.id, null))
            expect { assertEquals(listOf("a", "c"), it.surface().marks.map { m -> m.id() }) }
            undo()
            expect { assertEquals(listOf("a", "b", "c"), it.surface().marks.map { m -> m.id() }) }
            redo()
            expect { assertEquals(listOf("a", "c"), it.surface().marks.map { m -> m.id() }) }
        }
    }

    @Test
    fun aMarkKeepsItsPlaceWhenReplaced() {
        val a = mark("a", 0.1f, 0.2f)
        val b = mark("b", 0.3f, 0.4f)
        val moved = a.copy(points = anchors(0.7f, 0.8f), ink = false, cameraLocked = true)
        scenario(placed) {
            run(SetMark(head.id, a.id, a), SetMark(head.id, b.id, b), SetMark(head.id, a.id, moved))
            expect { assertEquals(listOf(moved, b), it.surface().marks) }
            undo()
            expect { assertEquals(listOf(a, b), it.surface().marks) }
        }
    }

    @Test
    fun aFurStrokeIsARootAndALineNeedsTwoPlaces() {
        assertEquals(1, Mark(MarkId("fur"), anchors(0.1f), kind = MarkKind.FUR).points.size)
        assertFailsWith<IllegalArgumentException> { Mark(MarkId("line"), anchors(0.1f)) }
        assertFailsWith<IllegalArgumentException> { Mark(MarkId("none"), emptyList(), kind = MarkKind.FUR) }
    }

    @Test
    fun aDragsStrokesAreOneStep() {
        val strokes = listOf(0.1f, 0.2f, 0.3f).map { Mark(MarkId("fur-$it"), anchors(it), kind = MarkKind.FUR) }
        assertUndoesAndRedoes(placed, AddMarks(head.id, strokes))
        assertEquals(strokes, AddMarks(head.id, strokes).apply(placed).document.surface().marks)
        val held = AddMarks(head.id, strokes).apply(placed).document
        assertUndoesAndRedoes(held, RemoveMarks(head.id, listOf(strokes[1].id)))
    }

    @Test
    fun aFurRuleSetsAndClears() {
        val rule = FurRule(showNear = 0.5f, spacingPixels = 20f, lengthPixels = 30f)
        assertUndoesAndRedoes(placed, SetFurRule(head.id, rule), SetFurRule(head.id, null))
        assertEquals(rule, SetFurRule(head.id, rule).apply(placed).document.surface().fur)
        assertFailsWith<IllegalArgumentException> { FurRule(showNear = 2f) }
        assertFailsWith<IllegalArgumentException> { FurRule(spacingPixels = 0f) }
    }

    @Test
    fun aMarkHoldsItsAngleVersionsThroughUndo() {
        val versions = mapOf(CameraBin("front") to MarkVersion(anchors(0.1f, 0.2f), MarkWidth(listOf(WidthStop(0f, 0f), WidthStop(1f, 1f)))))
        val versioned = mark("lid", 0.3f, 0.4f).copy(versions = versions)
        assertUndoesAndRedoes(placed, SetMark(head.id, versioned.id, versioned))
    }

    @Test
    fun eachKindOfChangeUndoesAndRedoes() {
        val group = eyeGroup(MarkGroupId("eye"), "Eye")
        assertUndoesAndRedoes(
            placed,
            SetBaseSlot(head.id, fur),
            SetAreaFill(head.id, "inner", cream),
            SetBaseSlot(head.id, cream),
            SetAreaFill(head.id, "inner", fur),
            SetAreaFill(head.id, "inner", cream),
            SetAreaFill(head.id, "pads", fur),
            SetAreaFill(head.id, "inner", null),
            SetGrain(head.id, Grain(listOf(GrainComb(u = 0.2f, v = 0.3f, du = 0f, dv = 1f)))),
            SetGrain(head.id, null),
            SetMarkGroup(head.id, group.id, group),
            SetMarkControls(head.id, group.id, MarkControls(open = 0.25f)),
            SetRegion(head.id, RegionId("r"), region("r")),
            SetRegion(head.id, RegionId("r"), region("r").copy(ink = true, slot = cream)),
            SetBrushStroke(head.id, BrushStrokeId("s"), stroke("s")),
            SetBrushStroke(head.id, BrushStrokeId("s"), null),
            SetPaletteSlot(fur, SlotColor(0.8f, 0.5f, 0.2f)),
            SetPaletteSlot(cream, SlotColor(1f, 0.9f, 0.8f)),
            SetPaletteSlot(fur, SlotColor(0.7f, 0.4f, 0.1f)),
            SetExpression(ExpressionId("glad"), ExpressionSet(ExpressionId("glad"), "Glad")),
            SetExpressionMarks(ExpressionId("glad"), head.id, group.id, MarkRole.LID, listOf(mark("lid", 0.1f, 0.2f))),
            SetFacePose(ExpressionId("glad"), FacePose(listOf(JointAngle(head.id, "jaw", 10f)))),
        )
    }

    @Test
    fun aBaseSlotLandsOnThePart() {
        scenario(placed) {
            run(SetBaseSlot(head.id, fur))
            expect { assertEquals(fur, it.surface().baseSlot) }
            undo()
            expect { assertNull(it.surface().baseSlot) }
        }
    }

    @Test
    fun anAreaFillLandsOnItsAreaAndUndoesToNoneOrThePreviousSlot() {
        scenario(placed) {
            run(SetAreaFill(head.id, "inner", fur), SetAreaFill(head.id, "inner", cream))
            expect { assertEquals(mapOf("inner" to cream), it.surface().areaFills) }
            undo()
            expect { assertEquals(mapOf("inner" to fur), it.surface().areaFills) }
            undo()
            expect(placed)
        }
    }

    @Test
    fun brushStrokesStayInTheOrderDrawnThroughARemovalAndItsUndo() {
        val strokes = listOf(stroke("first"), stroke("second"), stroke("third"))
        val painted = strokes.fold(placed) { d, s -> SetBrushStroke(head.id, s.id, s).apply(d).document }
        assertEquals(strokes, painted.surface().paint.strokes)
        scenario(painted) {
            run(SetBrushStroke(head.id, strokes[1].id, null))
            expect { assertEquals(listOf(strokes[0], strokes[2]), it.surface().paint.strokes) }
            undo()
            expect { assertEquals(strokes, it.surface().paint.strokes) }
        }
    }

    @Test
    fun removingAPaletteSlotRestoresItInPlace() {
        val tan = SlotColor(0.8f, 0.5f, 0.2f)
        val slots = listOf(fur, cream, SlotName("ink"))
        val filled = slots.fold(placed) { d, slot -> SetPaletteSlot(slot, tan).apply(d).document }
        scenario(filled) {
            run(SetPaletteSlot(cream, null))
            expect { assertEquals(listOf(fur, SlotName("ink")), it.character.palette.slots.map { s -> s.name }) }
            undo()
            expect { assertEquals(slots, it.character.palette.slots.map { s -> s.name }) }
            expect { assertEquals(tan, it.character.palette.color(cream)) }
        }
    }

    @Test
    fun removingAnExpressionRestoresItInPlace() {
        val ids = listOf("glad", "cross", "tired").map { ExpressionId(it) }
        val filled = ids.fold(placed) { d, id -> SetExpression(id, ExpressionSet(id, id.value)).apply(d).document }
        scenario(filled) {
            run(SetExpression(ids[1], null))
            undo()
            expect { assertEquals(ids, it.character.expressions.map { e -> e.id }) }
        }
    }

    @Test
    fun controlsOnAMissingGroupAndMarksOnAMissingExpressionAreRefused() {
        assertFailsWith<IllegalArgumentException> { SetMarkControls(head.id, MarkGroupId("none"), MarkControls()).apply(placed) }
        assertFailsWith<IllegalArgumentException> { SetFacePose(ExpressionId("none"), FacePose()).apply(placed) }
        assertFailsWith<IllegalArgumentException> { SetBaseSlot(PartId("none"), fur).apply(placed) }
    }

    @Test
    fun theTypesRefuseWhatTheyCannotHold() {
        assertFailsWith<IllegalArgumentException> { Mark(MarkId("m"), anchors(0.1f)) }
        assertFailsWith<IllegalArgumentException> { MarkVersion(anchors(0.1f)) }
        assertFailsWith<IllegalArgumentException> { Region(RegionId("r"), anchors(0.1f, 0.2f), fur) }
        assertFailsWith<IllegalArgumentException> { BrushStroke(BrushStrokeId("s"), emptyList(), fur, 0.1f) }
        assertFailsWith<IllegalArgumentException> { stroke("s").copy(softness = 1.5f) }
        assertFailsWith<IllegalArgumentException> { stroke("s").copy(softness = -0.1f) }
        assertFailsWith<IllegalArgumentException> { stroke("s").copy(softness = Float.NaN) }
        assertFailsWith<IllegalArgumentException> { MarkWidth(listOf(WidthStop(0.5f, 1f), WidthStop(0.2f, 1f))) }
        assertFailsWith<IllegalArgumentException> { SlotColor(1.5f, 0f, 0f) }
        assertFailsWith<IllegalArgumentException> { Palette(listOf(PaletteSlot(fur, SlotColor(0f, 0f, 0f)), PaletteSlot(fur, SlotColor(1f, 1f, 1f)))) }
        assertFailsWith<IllegalArgumentException> { MarkGroup(MarkGroupId("g"), "G", listOf(GroupRole(MarkRole.LID), GroupRole(MarkRole.LID))) }
        assertFailsWith<IllegalArgumentException> { MarkGroup(MarkGroupId("g"), "G", listOf(GroupRole(MarkRole.LID)), listOf(RoleClip(MarkRole.LID, MarkRole.PUPIL))) }
        assertFailsWith<IllegalArgumentException> { MarkGroup(MarkGroupId("g"), "G", listOf(GroupRole(MarkRole.LID)), listOf(RoleClip(MarkRole.LID, MarkRole.LID))) }
        assertFailsWith<IllegalArgumentException> { SurfaceWork(marks = listOf(mark("a", 0f, 1f), mark("a", 0f, 1f))) }
        assertFailsWith<IllegalArgumentException> { SetMark(head.id, MarkId("other"), mark("a", 0f, 1f)) }
    }

    @Test
    fun anEyeGroupDrawsInItsFixedOrderWithTheLidClipping() {
        val eye = eyeGroup(MarkGroupId("eye"), "Eye")
        assertEquals(
            listOf(MarkRole.WHITE, MarkRole.IRIS, MarkRole.PUPIL, MarkRole.HIGHLIGHT, MarkRole.LID, MarkRole.LASHES, MarkRole.BROW),
            eye.roles.map { it.role },
        )
        assertEquals(setOf(MarkRole.WHITE, MarkRole.IRIS, MarkRole.PUPIL), eye.clips.filter { it.by == MarkRole.LID }.map { it.clipped }.toSet())
        assertEquals(5, mouthGroup(MarkGroupId("mouth"), "Mouth").roles.size)
    }

    @Test
    fun commandsSerialize() {
        val group = mouthGroup(MarkGroupId("mouth"), "Mouth")
        val commands = listOf<DocumentCommand>(
            SetBaseSlot(head.id, fur, thisSide = true),
            SetAreaFill(head.id, "inner", cream, thisSide = true),
            SetGrain(head.id, Grain(listOf(GrainComb(u = 0f, v = 0f, du = 1f, dv = 0f)))),
            SetMark(head.id, MarkId("a"), mark("a", 0.1f, 0.2f).copy(versions = mapOf(CameraBin("side") to MarkVersion(anchors(0f, 1f)))), index = 2),
            SetMark(head.id, MarkId("a"), null, stored = true),
            SetMarkGroup(head.id, group.id, group),
            SetMarkControls(head.id, group.id, MarkControls(open = 0.5f, width = 1.2f)),
            SetRegion(head.id, RegionId("r"), region("r"), thisSide = true),
            SetBrushStroke(head.id, BrushStrokeId("s"), stroke("s")),
            SetBrushStroke(head.id, BrushStrokeId("soft"), stroke("soft").copy(softness = 0.75f)),
            SetPaletteSlot(fur, SlotColor(0.1f, 0.2f, 0.3f), index = 0),
            SetExpression(ExpressionId("glad"), ExpressionSet(ExpressionId("glad"), "Glad", pose = FacePose(listOf(JointAngle(head.id, "jaw", 12f))))),
            SetExpressionMarks(ExpressionId("glad"), head.id, group.id, MarkRole.LIP_LINE, listOf(mark("lip", 0.1f, 0.2f))),
            SetFacePose(ExpressionId("glad"), FacePose()),
        )
        for (command in commands) {
            val text = Json.encodeToString(DocumentCommand.serializer(), command)
            assertEquals(command, Json.decodeFromString(DocumentCommand.serializer(), text))
        }
    }

    @Test
    fun theSurfaceWorkRoundTripsThroughSave() {
        val group = eyeGroup(MarkGroupId("eye"), "Eye")
        val glad = ExpressionSet(ExpressionId("glad"), "Glad", pose = FacePose(listOf(JointAngle(head.id, "jaw", 12f))))
        val saved = listOf<DocumentCommand>(
            SetBaseSlot(head.id, fur),
            SetAreaFill(head.id, "inner", cream),
            SetGrain(head.id, Grain(listOf(GrainComb(u = 0.1f, v = 0.2f, du = 0.3f, dv = 0.4f)))),
            SetMark(head.id, MarkId("a"), mark("a", 0.1f, 0.2f).copy(cameraLocked = true, width = MarkWidth(listOf(WidthStop(0.5f, 2f))))),
            SetMarkGroup(head.id, group.id, group),
            SetRegion(head.id, RegionId("r"), region("r")),
            SetBrushStroke(head.id, BrushStrokeId("s"), stroke("s")),
            SetPaletteSlot(fur, SlotColor(0.1f, 0.2f, 0.3f)),
            SetExpression(glad.id, glad),
            SetExpressionMarks(glad.id, head.id, group.id, MarkRole.BROW, listOf(mark("brow", 0.1f, 0.2f))),
        ).fold(placed) { d, c -> c.apply(d).document }
        assertEquals(saved, readDocument(Json.encodeToString(SquashDocument.serializer(), saved)))
    }
}
