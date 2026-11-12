package dev.chieppa.squash.screens.twin

import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.build.RestDirection
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetBaseSlot
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.screens.fennec.loop
import dev.chieppa.squash.screens.fennec.p
import dev.chieppa.squash.screens.woman.FEET_SLOT
import dev.chieppa.squash.screens.woman.ownedPair
import dev.chieppa.squash.screens.woman.propShape

val PUMPS = GarmentId("pumps")
val PUMP_VAMPS = GarmentId("pump-vamps")
val FLATS = GarmentId("flats")
val FLAT_VAMPS = GarmentId("flat-vamps")
val BOOTS = GarmentId("boots")
val HEEL_L = PartId("heel-l")
val HEEL_R = PartId("heel-r")
val PLATFORM_L = PartId("platform-l")
val PLATFORM_R = PartId("platform-r")

val PATENT = SlotName("patent")
val TAN = SlotName("tan")
val BOOT_LEATHER = SlotName("boot")
val PLATFORM_RUBBER = SlotName("platform")

/*
 * Shoes are worn over the blended foot. Along the foot u = (z + 0.014) / 0.27 + 0.21 for a place z metres forward of the ankle line in
 * the reference's numbers, so u 0.21 is the ankle and a shoe's toe box ends short of u 1, where a branch to u 1 closes over it; v 0.5 is the sole.
 */
private fun footU(z: Float) = (z + 0.014f) / 0.27f + 0.21f

/** The foot's parameters a closed shoe sets: no toes, a toe box running to [tip] u and rounding to [round], and the forefoot turned up [pitch]. */
private fun shoeFoot(tip: Float, round: Float, pitch: Float = 0f, lean: Float = 0f) = BodyPartChange.Redraw(
    PartData(
        params = buildMap {
            put("toes", 0f)
            put("toeBox", 1f)
            put("toeTip", tip)
            put("toeRound", round)
            if (pitch != 0f) put("pitch", pitch)
            if (lean != 0f) put(PlacementParams.LEAN, lean)
        },
    ),
)

/** The pump's foot: pointed toe box, the foot pitched 24 degrees down at the ankle and its forefoot turned back up to lie flat. */
private const val PUMP_PITCH = 24.06f
private val PUMP_FOOT = shoeFoot(tip = 0.7185f, round = 0.0563f, pitch = PUMP_PITCH, lean = -PUMP_PITCH)
private const val PUMP_EASE = 0.0032f

/** The pump's sides and back up to its opening, round the foot below v 0.3 to 0.7, and its vamp, closed over the toe box from the opening's front. */
private const val SIDES_FROM_V = 0.3f
private const val SIDES_TO_V = 0.7f
private val PUMP_VAMP_FROM = footU(0.082f)

/** A heel from the pump's raised seat to the ground: 0.013 m round at the seat and 0.0045 at the tip, 0.07 m long. */
private val HEEL_SIDE = loop(p(0f, 0.029f), p(0.060f, 0.018f), p(0.150f, 0.010f), p(0.158f, 0f), p(0.150f, -0.010f), p(0.060f, -0.018f), p(0f, -0.029f))
private const val HEEL_SOCKET = 1
private val HEEL_U = footU(-0.031f)
private const val SOLE_V = 0.5f
private const val HEEL_RINGS = 16
private const val HEEL_SEGMENTS = 10

/** The flat: a softer point, on the ground, its opening a little longer. */
private val FLAT_FOOT = shoeFoot(tip = 0.7f, round = 0.0622f)
private const val FLAT_EASE = 0.003f
private const val FLAT_FROM_V = 0.33f
private const val FLAT_TO_V = 0.67f
private val FLAT_VAMP_FROM = footU(0.085f)

/** The boot: a round toe box, closed over the whole foot, a shaft up the leg to mid-calf over the jeans and a platform sole. */
private val BOOT_FOOT = shoeFoot(tip = 0.663f, round = 0.0741f)
private const val BOOT_EASE = 0.0085f
private const val SHAFT_TOP = 0.31f
private const val SHAFT_EASE = 0.015f

/** The sockets on the foot a garment's soles sit on, one a garment: under the ankle line, on the ground. */
internal const val BOOT_SOLE_SOCKET = 2
internal const val SANDAL_SOLE_SOCKET = 3
private val SOLE_U = footU(-0.014f)
private const val SOLE_RINGS = 32
private const val SOLE_SEGMENTS = 16

/** Where the footprint runs from and to along the foot, from under the ankle line. */
internal const val PRINT_BACK = -0.0585f
internal const val PRINT_FRONT = 0.213f

/** The footprint of a sole grown 0.006 m round the foot, in Top from under the ankle line, metres: the reference's heel, mid-foot, ball and toe discs. */
internal val FOOTPRINT = loop(
    p(0.213f, 0f), p(0.1926f, -0.0271f), p(0.1576f, -0.0403f), p(0.1325f, -0.0467f), p(0.1127f, -0.0485f), p(0.0967f, -0.0472f), p(0.0842f, -0.0448f),
    p(0.074f, -0.0429f), p(0.0644f, -0.042f), p(0.054f, -0.0416f), p(0.0411f, -0.0412f), p(0.0228f, -0.0409f), p(-0.0148f, -0.0428f), p(-0.0476f, -0.0278f),
    p(-0.0585f, 0f), p(-0.0461f, 0.0274f), p(-0.0106f, 0.0407f), p(0.0302f, 0.0349f), p(0.0459f, 0.0353f), p(0.0565f, 0.0364f), p(0.0653f, 0.0383f),
    p(0.074f, 0.0415f), p(0.0847f, 0.0468f), p(0.0993f, 0.0525f), p(0.1185f, 0.0557f), p(0.1417f, 0.054f), p(0.1722f, 0.0473f), p(0.2041f, 0.0297f),
)

/** The same grown 0.012 m, for the platform. */
private val PLATFORM_PRINT = loop(
    p(0.2209f, 0f), p(0.2015f, -0.0291f), p(0.1663f, -0.0445f), p(0.1392f, -0.052f), p(0.1177f, -0.0548f), p(0.1001f, -0.0541f), p(0.0858f, -0.0518f),
    p(0.074f, -0.0498f), p(0.0629f, -0.0487f), p(0.0508f, -0.0481f), p(0.0359f, -0.0478f), p(0.0137f, -0.0481f), p(-0.0249f, -0.0476f), p(-0.0553f, -0.0295f),
    p(-0.066f, 0f), p(-0.0535f, 0.0291f), p(-0.022f, 0.0462f), p(0.0202f, 0.0429f), p(0.0402f, 0.0423f), p(0.0531f, 0.0434f), p(0.0636f, 0.0455f),
    p(0.074f, 0.049f), p(0.0864f, 0.0545f), p(0.1028f, 0.0598f), p(0.1235f, 0.0621f), p(0.1484f, 0.0594f), p(0.1805f, 0.0513f), p(0.2119f, 0.0315f),
)

/** A slab in Side from just above the ground to [thick] below it, along the footprint from [back] to [front], its ends rounded. */
internal fun slab(back: Float, front: Float, thick: Float): OutlineCurve = loop(
    p(front, -thick / 2f), p(front - 0.006f, 0.001f), p((back + front) / 2f, 0.001f), p(back + 0.006f, 0.001f), p(back, -thick / 2f),
    p(back + 0.006f, -thick), p((back + front) / 2f, -thick), p(front - 0.006f, -thick),
)

/**
 * A sole [garment] owns under each foot: a blob from the foot's ground line under the ankle, drawn as [print] in Top and a slab [thick]
 * deep in Side, in [color], the drawing in metres of a foot at [size]. It rides the foot's bone rigidly and does not wrap the foot's sides.
 */
internal fun sole(
    garment: GarmentId, socket: Int, left: PartId, right: PartId, print: OutlineCurve, back: Float, front: Float, thick: Float, color: SlotName,
    size: Float = 1f,
): List<DocumentCommand> = buildList {
    add(HandSockets.add(FOOT_L, socket, SOLE_U, SOLE_V, 0f, paired = false, sink = 0f))
    val seat = HandSockets.name(socket)
    add(ownedPair(garment, "${garment.value}-soles", left to SocketRef(FOOT_L, seat), right to SocketRef(FOOT_R, seat), "blob"))
    addAll(propShape(left, slab(back, front, thick), print, size, SOLE_RINGS, SOLE_SEGMENTS))
    add(SetBaseSlot(left, color))
}

/**
 * The shoes: pumps and flats each a shell round the foot's sides and back below their opening with a vamp over the toe box worn in the
 * straps' slot, the pumps pitched on a heel each; and boots closed over the foot with a shaft to mid-calf on a platform sole.
 */
fun twinShoes(): List<DocumentCommand> = listOf(cloth(PATENT, 0x1E1B20), cloth(TAN, 0xB07A45), cloth(BOOT_LEATHER, 0x1D1A20), cloth(PLATFORM_RUBBER, 0x141215)) +
    twinGarment(PUMPS, "Pumps", FEET_SLOT, PATENT, listOf(GarmentBranch(FOOT_L, fromV = SIDES_FROM_V, toV = SIDES_TO_V, ease = PUMP_EASE)), changes = mapOf(FOOT_L to PUMP_FOOT)) +
    buildList {
        add(HandSockets.add(FOOT_L, HEEL_SOCKET, HEEL_U, SOLE_V, 0f, paired = false, sink = 0f))
        val seat = HandSockets.name(HEEL_SOCKET)
        add(ownedPair(PUMPS, "heels", HEEL_L to SocketRef(FOOT_L, seat), HEEL_R to SocketRef(FOOT_R, seat), "tube"))
        add(RestDirection.restIn(HEEL_L, RestDirection.DOWN))
        addAll(propShape(HEEL_L, HEEL_SIDE, HEEL_SIDE, FOOT_SIZE, HEEL_RINGS, HEEL_SEGMENTS))
        add(SetBaseSlot(HEEL_L, PATENT))
    } +
    twinGarment(PUMP_VAMPS, "Pump vamps", STRAPS_SLOT, PATENT, listOf(GarmentBranch(FOOT_L, PUMP_VAMP_FROM, 1f, ease = PUMP_EASE)), layer = OVER_LAYER) +
    twinGarment(FLATS, "Flats", FEET_SLOT, TAN, listOf(GarmentBranch(FOOT_L, fromV = FLAT_FROM_V, toV = FLAT_TO_V, ease = FLAT_EASE)), changes = mapOf(FOOT_L to FLAT_FOOT)) +
    twinGarment(FLAT_VAMPS, "Flat vamps", STRAPS_SLOT, TAN, listOf(GarmentBranch(FOOT_L, FLAT_VAMP_FROM, 1f, ease = FLAT_EASE)), layer = OVER_LAYER) +
    twinGarment(
        BOOTS, "Boots", FEET_SLOT, BOOT_LEATHER,
        listOf(GarmentBranch(FOOT_L, ease = BOOT_EASE), GarmentBranch(LEG_L, legU(SHAFT_TOP), 1f, ease = SHAFT_EASE)),
        layer = OVER_LAYER, changes = mapOf(FOOT_L to BOOT_FOOT),
    ) +
    sole(BOOTS, BOOT_SOLE_SOCKET, PLATFORM_L, PLATFORM_R, PLATFORM_PRINT, -0.066f, 0.2209f, 0.028f, PLATFORM_RUBBER)
