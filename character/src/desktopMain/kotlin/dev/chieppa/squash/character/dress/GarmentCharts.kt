package dev.chieppa.squash.character.dress

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.catalog.isLeader
import dev.chieppa.squash.character.shape.BodyChart
import dev.chieppa.squash.character.shape.ChartPlaces
import dev.chieppa.squash.character.build.pointAt
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.document.PartId

/** The leading side of each pair's other side in [character], by part. */
fun leadersOf(character: Character): Map<PartId, PartId> =
    character.parts.filterNot { it.isLeader }.mapNotNull { part -> character.twinOf(part)?.let { part.id to it.id } }.toMap()

/** [part], or the leader of its pair. */
fun Character.leaderOf(part: PartInstance): PartInstance = if (part.isLeader) part else twinOf(part) ?: part

/** [body]'s own parts as a chart, the other sides of pairs read as their leaders; the last one asked for is kept for the same body. */
fun bodyChart(character: Character, body: AssembledCharacter): BodyChart = ChartMemo.chart(character, body)

/** Where the cells of [body]'s chart lie, kept for the same body. */
fun chartPlaces(character: Character, body: AssembledCharacter): ChartPlaces = ChartMemo.places(character, body)

/** Where [anchor] lies on [body] at rest, or null on a part it does not hold. */
fun placeOnBody(body: AssembledCharacter, anchor: Anchor): Vec3? {
    val placed = body.part(anchor.part) ?: return null
    return placed.surface.pointAt(anchor)?.position?.plus(placed.origin)
}

/** The middle parts of [character], those in no pair and on no side, which Mirror paints on both sides of the body's middle. */
fun middlePartsOf(character: Character): Set<PartId> = character.parts.filter { it.pair == null && it.side == null }.mapTo(HashSet()) { it.id }

/** The last chart and its places, by the body and the parts they were made for. */
private object ChartMemo {
    private var body: AssembledCharacter? = null
    private var parts: Any? = null
    private var chart: BodyChart? = null
    private var places: ChartPlaces? = null

    @Synchronized
    fun chart(character: Character, of: AssembledCharacter): BodyChart {
        val held = chart
        if (held != null && body === of && parts == character.parts) return held
        val absent = character.parts.filter { it.garment == null && of.part(it.id) == null }.mapNotNull { part -> part.socket?.let { part.id to it } }.toMap()
        val made = BodyChart.of(of, leadersOf(character), absent) { it.owner == null }
        body = of
        parts = character.parts
        chart = made
        places = null
        return made
    }

    @Synchronized
    fun places(character: Character, of: AssembledCharacter): ChartPlaces {
        val chart = chart(character, of)
        return places ?: ChartPlaces.of(chart, of).also { places = it }
    }
}
