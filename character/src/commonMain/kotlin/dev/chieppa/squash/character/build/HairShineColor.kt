package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.block.HairShine
import dev.chieppa.squash.character.block.SHINE_AREA
import dev.chieppa.squash.character.model.SIZE_PARAM
import dev.chieppa.squash.character.surface.isFieldPart
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.wornPart
import dev.chieppa.squash.geom.LevelCut
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.PatchCut
import dev.chieppa.squash.geom.SlottedCut
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.graph.Graph
import dev.chieppa.squash.graph.Node
import kotlinx.coroutines.flow.Flow

/** A hair volume that shows its band of shine: the [volume] part, its undrawn [shine] and the [slot] its shine area is filled with. */
data class ShineSource(val volume: PartId, val shine: HairShine, val slot: SlotName)

/** Each hair volume of [character] whose shine area is filled, through [templates]. */
fun shineSources(character: Character, templates: TemplateLibrary): List<ShineSource> = character.parts.mapNotNull { part ->
    val worn = character.wornPart(part)
    val template = templates.find(worn.template) ?: return@mapNotNull null
    val slot = character.resolved(part).surface.areaFills[SHINE_AREA] ?: return@mapNotNull null
    val values = template.defaults + character.resolvedShape(part).params
    val length = (values[SIZE_PARAM] ?: 1f) * template.axis.length
    val shine = template.blocks.firstNotNullOfOrNull { it.hairShine(values, length) } ?: return@mapNotNull null
    ShineSource(part.id, shine, slot)
}

/**
 * What a hair part shows of a volume's band of shine: [layers] on its own mesh, and for a part shown as copies, [copies], one list for
 * each copy, since each copy lies elsewhere in the band.
 */
class ShineLayers(val layers: List<SlottedCut<SlotName>>, val copies: List<List<SlottedCut<SlotName>>>? = null)

/**
 * [colors] with each hair part built as its own mesh in [character] showing the band of shine of the first of [sources] where the band
 * crosses it, read on the volume's sphere where the part lies (see [dev.chieppa.squash.geom.VolumeShape.sphereOf]), a layer over its own.
 */
fun Graph.shinedColorNode(colors: Node<*, BodyColor>, character: Node<*, AssembledCharacter>, sources: Flow<List<ShineSource>>): Node<*, BodyColor> {
    val memo = ShineMemo()
    val colorSource = follow<BodyColor, ShineInput>(colors, cacheSize = 1, stage = BuildStages.SURFACE_WORK) { ShineInput.Colors(it) }
    val bodySource = follow<AssembledCharacter, ShineInput>(character, cacheSize = 1, stage = BuildStages.SURFACE_WORK) { ShineInput.Body(it) }
    return gather(
        sources,
        sourcesOf = { mapOf(COLORS to colorSource, BODY to bodySource) },
        cacheSize = 1,
        stage = BuildStages.SURFACE_WORK,
    ) { (found, built) ->
        val body = (built.getValue(COLORS) as ShineInput.Colors).colors
        val placed = (built.getValue(BODY) as ShineInput.Body).body
        val source = found.firstOrNull() ?: return@gather body
        val shines = hairShines(placed, source, memo)
        if (shines.isEmpty()) return@gather body
        BodyColor(
            body.parts + shines.mapValues { (id, shine) ->
                val own = body.parts[id]
                PartColor(own?.baseSlot, own?.layers.orEmpty() + shine.layers, shine.copies?.map { own?.layers.orEmpty() + it })
            },
        )
    }
}

/** What the shine node reads: the body's color, or the placed character. */
private sealed interface ShineInput {
    class Colors(val colors: BodyColor) : ShineInput

    class Body(val body: AssembledCharacter) : ShineInput
}

private const val COLORS = "colors"
private const val BODY = "body"

/** Each hair part of [placed] built as its own mesh, with what it shows of [source]'s band of shine, those it shows none of left out. */
internal fun hairShines(placed: AssembledCharacter, source: ShineSource, memo: ShineMemo = ShineMemo()): Map<PartId, ShineLayers> {
    val volume = placed.part(source.volume) ?: return emptyMap()
    val axes = PartBuilder.axes(volume.template)
    fun inVolume(p: Vec3): Vec3 = source.shine.shape.sphereOf(volume.surface.shown(p - volume.origin), axes.along, axes.up, axes.side)
    val out = HashMap<PartId, ShineLayers>()
    memo.begin()
    for (part in placed.parts) {
        val id = part.id ?: continue
        if (part.role != Role.HAIR || id == source.volume || isFieldPart(part)) continue
        val copies = part.copies
        val layers = if (copies == null) {
            listOfNotNull(memo.cut(source, part.surface, part.origin - volume.origin) { local -> inVolume(part.origin + part.surface.shown(local)) })
                .let { ShineLayers(it) }
        } else {
            val own = copies.rule.surface
            ShineLayers(emptyList(), copies.places.map { place ->
                listOfNotNull(memo.cut(source, own, place.origin - volume.origin, place.turn) { local -> inVolume(place.placed(own.shown(local))) })
            })
        }
        if (layers.layers.isNotEmpty() || layers.copies?.any { it.isNotEmpty() } == true) out[id] = layers
    }
    memo.end()
    return out
}

/** The shine cuts made last time, by what they depend on, so a drag that moves the head and its hair together cuts nothing again. */
internal class ShineMemo {
    private data class Key(val source: ShineSource, val surface: PartSurface, val offset: Vec3, val turn: Any?)

    private var held: Map<Key, SlottedCut<SlotName>?> = emptyMap()
    private var made = HashMap<Key, SlottedCut<SlotName>?>()

    /** Starts a run: what the last run cut is kept for this one, and what this one does not ask for again is let go when it ends. */
    fun begin() {
        made = HashMap()
    }

    /** Ends a run, keeping what it cut for the next. */
    fun end() {
        held = made
    }

    /** [surface]'s cut where [sphere], the volume's sphere at a point of its own, lies inside [source]'s band, made once for each place. */
    fun cut(source: ShineSource, surface: PartSurface, offset: Vec3, turn: Any? = null, sphere: (Vec3) -> Vec3): SlottedCut<SlotName>? {
        val key = Key(source, surface, offset, turn)
        val cut = if (key in held) held[key] else shineCut(source, surface, sphere)
        made[key] = cut
        return cut
    }
}

/** [surface]'s own mesh cut where [sphere] of each of its points lies in [source]'s band, as a layer on its first patch, or null where none does. */
private fun shineCut(source: ShineSource, surface: PartSurface, sphere: (Vec3) -> Vec3): SlottedCut<SlotName>? {
    val mesh = surface.mesh
    val patch = surface.patches.firstOrNull() ?: return null
    val count = patch.nu * patch.nv
    if (mesh.vertexCount < count) return null
    val values = FloatArray(mesh.vertexCount) { i -> if (i < count) source.shine.shine.inside(sphere(patch.point(i))) else -1f }
    if (values.none { it >= 0f }) return null
    val cut = LevelCut.of(values, mesh.indices)
    if (cut.isEmpty) return null
    val us = FloatArray(mesh.vertexCount) { i -> if (i < count) patch.ringUs[i / patch.nv] else 0f }
    val vs = FloatArray(mesh.vertexCount) { i -> if (i < count) (i % patch.nv).toFloat() / patch.segments else 0f }
    return SlottedCut(source.slot, PatchCut(patch.name, cut.mesh(mesh), cut.values(us), cut.values(vs)))
}
