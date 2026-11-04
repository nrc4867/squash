package dev.chieppa.squash.document

/** A fennec's torso with a mirrored pair of legs and of paws, for scenarios over what pairs share. */
abstract class PairedFennec {
    protected val legs = PairId("front-legs")
    protected val paws = PairId("front-paws")
    protected val torso = PartInstance(PartId("torso"), TemplateRef(TemplateId("torso"), 1))
    protected val legL = PartInstance(PartId("leg-l"), TemplateRef(TemplateId("leg"), 1), pair = legs, side = Side.LEFT)
    protected val legR = PartInstance(PartId("leg-r"), TemplateRef(TemplateId("leg"), 1), pair = legs, side = Side.RIGHT)
    protected val pawL = PartInstance(PartId("paw-l"), TemplateRef(TemplateId("paw"), 1), pair = paws, side = Side.LEFT)
    protected val pawR = PartInstance(PartId("paw-r"), TemplateRef(TemplateId("paw"), 1), pair = paws, side = Side.RIGHT)
    protected val placed = AddParts(listOf(torso, legL, legR, pawL, pawR)).apply(
        SquashDocument(Character(CharacterId("fennec"), "Fennec", BodyPlanRef(BodyPlanId("canine-quadruped"), 1))),
    ).document
    protected val slot = SlotName("fur")
    protected val other = SlotName("cream")

    protected fun SquashDocument.surfaceOf(part: PartInstance) = checkNotNull(character.resolved(part.id)).surface
    protected fun on(part: PartInstance, v: Float, u: Float = 0.5f) = Anchor(part.id, u = u, v = v)
    protected fun line(id: String, part: PartInstance) = Mark(MarkId(id), listOf(on(part, 0.25f), on(part, 0.75f, u = 0.75f)))
}
