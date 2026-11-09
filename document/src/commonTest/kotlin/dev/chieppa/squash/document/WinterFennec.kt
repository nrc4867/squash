package dev.chieppa.squash.document

/** A small fennec with a Winter wardrobe built by commands: a hat, a sweater and boots in three slots, a Winter and a Bare outfit. */
internal object WinterFennec {
    val TORSO = PartId("torso")
    val HEAD = PartId("head")
    val EAR_L = PartId("ear-l")
    val EAR_R = PartId("ear-r")
    val LEG_L = PartId("front-leg-l")
    val LEG_R = PartId("front-leg-r")
    val PAW_L = PartId("front-paw-l")
    val PAW_R = PartId("front-paw-r")
    val EARS = PairId("ears")
    val LEGS = PairId("front-legs")
    val PAWS = PairId("front-paws")

    val HAT = GarmentId("hat")
    val SWEATER = GarmentId("sweater")
    val BOOTS = GarmentId("boots")
    val SANDALS = GarmentId("sandals")
    val HEAD_SLOT = GarmentSlotId("head")
    val BODY_SLOT = GarmentSlotId("body")
    val FEET_SLOT = GarmentSlotId("feet")
    val WINTER = OutfitId("winter")
    val BARE = OutfitId("bare")

    fun ref(id: String, version: Int = 1) = TemplateRef(TemplateId(id), version)

    /** The fennec's body, nothing worn. */
    fun body(): SquashDocument {
        val start = SquashDocument(Character(CharacterId("fennec"), "Fennec", BodyPlanRef(BodyPlanId("canine-quadruped"), 1)))
        return AddParts(
            listOf(
                PartInstance(TORSO, ref("torso", 2)),
                PartInstance(HEAD, ref("head", 2), SocketRef(TORSO, "neck")),
                PartInstance(EAR_L, ref("ear"), SocketRef(HEAD, "ear"), pair = EARS, side = Side.LEFT),
                PartInstance(EAR_R, ref("ear"), SocketRef(HEAD, "ear"), pair = EARS, side = Side.RIGHT),
                PartInstance(LEG_L, ref("leg"), SocketRef(TORSO, "front-leg"), pair = LEGS, side = Side.LEFT),
                PartInstance(LEG_R, ref("leg"), SocketRef(TORSO, "front-leg"), pair = LEGS, side = Side.RIGHT),
                PartInstance(PAW_L, ref("paw", 4), SocketRef(LEG_L, "paw"), pair = PAWS, side = Side.LEFT),
                PartInstance(PAW_R, ref("paw", 4), SocketRef(LEG_R, "paw"), pair = PAWS, side = Side.RIGHT),
            ),
        ).apply(start).document
    }

    /** The commands that dress [body]: slots, garments, their branches and changes, and the outfits. */
    val dressing: List<DocumentCommand> = listOf(
        AddGarmentSlot(GarmentSlot(HEAD_SLOT, "Head")),
        AddGarmentSlot(GarmentSlot(BODY_SLOT, "Body")),
        AddGarmentSlot(GarmentSlot(FEET_SLOT, "Feet")),
        AddGarment(Garment(HAT, "Hat", HEAD_SLOT, layer = 1)),
        CoverPart(HAT, GarmentBranch(HEAD, from = 0.5f)),
        SetBodyPartChange(HAT, EAR_L, BodyPartChange.Through()),
        AddGarment(Garment(SWEATER, "Sweater", BODY_SLOT, layer = 1)),
        CoverPart(SWEATER, GarmentBranch(TORSO)),
        CoverPart(SWEATER, GarmentBranch(LEG_L, to = 0.8f)),
        AddGarment(Garment(BOOTS, "Boots", FEET_SLOT, layer = 1)),
        CoverPart(BOOTS, GarmentBranch(PAW_L)),
        SetBodyPartChange(BOOTS, PAW_L, BodyPartChange.Swap(ref("foot"))),
        AddGarment(Garment(SANDALS, "Sandals", FEET_SLOT, layer = 1)),
        SetBodyPartChange(SANDALS, PAW_L, BodyPartChange.Redraw(PartData(params = mapOf("toes" to 0f)))),
        AddOutfit(Outfit(WINTER, "Winter")),
        PickGarment(WINTER, HEAD_SLOT, HAT),
        PickGarment(WINTER, BODY_SLOT, SWEATER),
        PickGarment(WINTER, FEET_SLOT, BOOTS),
        AddOutfit(Outfit(BARE, "Bare")),
        ShowOutfit(WINTER),
    )

    /** [body] dressed by [dressing], showing Winter. */
    fun dressed(): SquashDocument = dressing.fold(body()) { document, command -> command.apply(document).document }
}
