package dev.chieppa.squash.screens.woman

import dev.chieppa.squash.character.build.GenericParams
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.build.RestDirection
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.LooseEnd
import dev.chieppa.squash.document.LooseEndId
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.SetLooseEnd
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SurfaceWork
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.fennec.loop
import dev.chieppa.squash.screens.fennec.p

val SHADES = GarmentId("shades")
val BEANIE = GarmentId("beanie")
val SCARF = GarmentId("scarf")
val MITTENS = GarmentId("mittens")
val CHOKER = GarmentId("choker")
val MAKEUP = GarmentId("makeup")
val SCARF_END = LooseEndId("scarf-end")

val LENS = SlotName("lens")
val KNIT = SlotName("knit")
val SCARF_KNIT = SlotName("scarf")
val DARK_LIPS = SlotName("dark-lips")
val SHADOW = SlotName("shadow")

/**
 * The shades: a band over the front of the head through both eyes, round the front pole the face is drawn about, from the bridge out
 * past the outer corners and across the brow between them. A garment is a shell over a part's surface, so a frame and lenses that
 * stand off the face are out of reach.
 */
private val SHADES_FROM = FacePlane.uAt(0.68f)
private val SHADES_TO = FacePlane.uAt(0.3f)
private const val SHADES_ROUND = 0.27f
private const val SHADES_EASE = 0.006f

/** The beanie over the crown of the hair, from the back to the fringe, over the top. */
private const val BEANIE_TO = 0.82f
private const val BEANIE_ROUND = 0.34f
private const val BEANIE_EASE = 0.035f

/**
 * The scarf wraps the base of the neck over the coat's collar and the top of the shoulders, its loose end hanging at the front from the
 * shoulders' band: a loose end is as long as a share of the part its branch covers, so on the short neck it would barely show.
 */
private const val SCARF_FROM = 0.92f
private const val SCARF_UP = 0.6f
private const val SCARF_EASE = 0.045f
private const val SCARF_END_V = 0.04f
private const val SCARF_LAYER = 4

/** The choker, a narrow band round the middle of the neck. */
private const val CHOKER_FROM = 0.3f
private const val CHOKER_TO = 0.5f
private const val CHOKER_EASE = 0.005f

/** A mitten in Side and Top, from the wrist: a rounded paddle the hand is swapped for while it is worn. */
private val MITTEN_SIDE = loop(p(0f, 0.03f), p(0.06f, 0.034f), p(0.14f, 0.03f), p(0.2f, 0.018f), p(0.22f, 0f), p(0.2f, -0.02f), p(0.12f, -0.032f), p(0f, -0.034f))
private val MITTEN_TOP = loop(p(0f, 0.04f), p(0.06f, 0.06f), p(0.14f, 0.064f), p(0.2f, 0.045f), p(0.22f, 0f), p(0.2f, -0.045f), p(0.14f, -0.064f), p(0.06f, -0.06f), p(0f, -0.04f))
private const val MITTEN_RINGS = 32
private const val MITTEN_EASE = 0.006f

private val mitten = BodyPartChange.Swap(
    TemplateRef(TemplateId("blob"), 1),
    PartData(
        outlines = mapOf(View.SIDE to MITTEN_SIDE, View.TOP to MITTEN_TOP),
        params = mapOf(PlacementParams.REST to RestDirection.DOWN.ordinal.toFloat(), GenericParams.RINGS to MITTEN_RINGS.toFloat()),
        surface = SurfaceWork(baseSlot = KNIT),
    ),
)

/** The face the dark look paints over her own: dark lips over her lips, and shadow over each lid. */
private val makeup = BodyPartChange.Redraw(PartData(surface = SurfaceWork(regions = lipRegions(DARK_LIPS) + lidShadows(SHADOW))))

/** The accessories: shades, a beanie, a scarf with a loose end, mittens, a choker and a dark face to wear with them. */
fun accessories(): List<DocumentCommand> = listOf(
    cloth(LENS, 0.08f, 0.08f, 0.12f),
    cloth(KNIT, 0.90f, 0.84f, 0.70f),
    cloth(SCARF_KNIT, 0.86f, 0.70f, 0.24f),
    cloth(DARK_LIPS, 0.30f, 0.08f, 0.16f),
    cloth(SHADOW, 0.36f, 0.26f, 0.38f),
) + garment(SHADES, "Shades", EYES_SLOT, LENS, listOf(GarmentBranch(HEAD, SHADES_FROM, SHADES_TO, ease = SHADES_EASE, fromV = -SHADES_ROUND, toV = SHADES_ROUND))) +
    garment(BEANIE, "Beanie", HEAD_SLOT, KNIT, listOf(GarmentBranch(HAIR, 0f, BEANIE_TO, ease = BEANIE_EASE, fromV = -BEANIE_ROUND, toV = BEANIE_ROUND))) +
    garment(
        SCARF, "Scarf", NECK_SLOT, SCARF_KNIT,
        listOf(GarmentBranch(TORSO, SCARF_FROM, 1f, ease = SCARF_EASE), GarmentBranch(NECK, 0f, SCARF_UP, ease = SCARF_EASE)), layer = SCARF_LAYER,
    ) +
    SetLooseEnd(SCARF, LooseEnd(SCARF_END, TORSO, v = SCARF_END_V)) +
    garment(MITTENS, "Mittens", HANDS_SLOT, KNIT, listOf(GarmentBranch(HAND_L, ease = MITTEN_EASE)), changes = mapOf(HAND_L to mitten)) +
    garment(CHOKER, "Choker", NECK_SLOT, PATENT, listOf(GarmentBranch(NECK, CHOKER_FROM, CHOKER_TO, ease = CHOKER_EASE))) +
    garment(MAKEUP, "Dark face", FACE_SLOT, SHADOW, emptyList(), changes = mapOf(HEAD to makeup))
