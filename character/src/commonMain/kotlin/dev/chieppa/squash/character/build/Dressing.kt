package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.View
import dev.chieppa.squash.document.branchPartId
import dev.chieppa.squash.document.worn
import dev.chieppa.squash.geom.MeshBuilder
import dev.chieppa.squash.geom.OutlineProfile
import dev.chieppa.squash.geom.PartAxes
import dev.chieppa.squash.geom.PartMesh
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.ShellBuilder
import dev.chieppa.squash.geom.ShellInside
import dev.chieppa.squash.geom.ShellReach
import dev.chieppa.squash.geom.Slice
import dev.chieppa.squash.graph.Graph
import dev.chieppa.squash.graph.Node
import dev.chieppa.squash.graph.cancellationCheck
import kotlinx.coroutines.flow.Flow

/** How many rings every branch of a garment has, whatever its length or drawing. */
const val BRANCH_RINGS = 32

/** For a garment's branch, the [garment] it belongs to and the body [part] it was built over: its patch [bodyPatch], and the ring of it under each branch ring. */
class Covering(val garment: GarmentId, val part: PartId, val bodyPatch: Int, val bodyRings: IntArray)

/** What dressing the character reads from the document: the garments worn, innermost first, and each paired part's twin. */
data class Dressing(val worn: List<Garment> = emptyList(), val twins: Map<PartId, PartId> = emptyMap())

/** The dressing [character] wears. */
fun dressingOf(character: Character): Dressing {
    val worn = character.worn
    if (worn.isEmpty()) return Dressing()
    val twins = character.parts.mapNotNull { part -> character.twinOf(part)?.let { part.id to it.id } }.toMap()
    return Dressing(worn, twins)
}

/** [assembled] with the garments [dressing] holds, following both. See HLD: data-model, Garments. */
fun Graph.dressedNode(assembled: Node<*, AssembledCharacter>, dressing: Flow<Dressing>): Node<*, AssembledCharacter> = gather(
    dressing,
    sourcesOf = { mapOf(Unit to assembled) },
    stage = BuildStages.ASSEMBLY,
) { (worn, built) ->
    val checkCancelled = cancellationCheck()
    built.getValue(Unit).dressedIn(worn, checkCancelled)
}

/**
 * This character with a part for every branch of every garment [dressing] wears, on each covered part and its twin, placed where that
 * part is, and with the skin they cover hidden: a body triangle whose corners all lie inside a branch is left out of its part's mesh,
 * whose grid stays whole.
 */
fun AssembledCharacter.dressedIn(dressing: Dressing, checkCancelled: () -> Unit = {}): AssembledCharacter {
    if (dressing.worn.isEmpty()) return this
    val branches = dressing.worn.flatMap { garment ->
        garment.branches.flatMap { branch ->
            listOfNotNull(branch.part, dressing.twins[branch.part]).mapNotNull { id ->
                checkCancelled()
                part(id)?.let { branchOver(garment, branch, it) }
            }
        }
    }
    if (branches.isEmpty()) return this
    val shells = branches.map { it.shellInside() }
    val body = parts.map { part ->
        checkCancelled()
        if (part.id == null) part else part.coveredBy(shells)
    }
    return AssembledCharacter(body + branches, sockets)
}

/** The inside test of every garment's branch this character wears, in body space. */
fun AssembledCharacter.garmentShells(): List<ShellInside> = parts.filter { it.covering != null }.map { it.shellInside() }

private fun PlacedPart.shellInside(): ShellInside = ShellInside(surface.patches.single(), { origin + surface.shown(it) }, surface::shown)

/** This part with every triangle lying inside one of [shells] left out of its mesh, or itself when none does. */
private fun PlacedPart.coveredBy(shells: List<ShellInside>): PlacedPart {
    val mesh = surface.mesh
    val hidden = BooleanArray(mesh.vertexCount) { vertex ->
        val point = origin + mesh.position(vertex)
        shells.any { point in it }
    }
    if (hidden.none { it }) return this
    val indices = mesh.indices
    val kept = ArrayList<Int>(indices.size)
    for (t in 0 until indices.size / 3) {
        val a = indices[t * 3]
        val b = indices[t * 3 + 1]
        val c = indices[t * 3 + 2]
        if (hidden[a] && hidden[b] && hidden[c]) continue
        kept += a
        kept += b
        kept += c
    }
    if (kept.size == indices.size) return this
    val shown = PartSurface(surface.patches, surface.mirrored, RenderMesh(mesh.vertices, kept.toIntArray()), surface.silhouettes, surface.box, surface.extents)
    return showing(shown, turn)
}

/** [garment]'s [branch] built over [covered], or null when the part has no swept patch to build it on. */
private fun branchOver(garment: Garment, branch: GarmentBranch, covered: PlacedPart): PlacedPart? {
    val bodyPatch = covered.surface.patches.indexOfFirst { it.slices != null }
    if (bodyPatch < 0) return null
    val id = covered.id ?: return null
    val axes = PartBuilder.axes(covered.template)
    val reach = drawnReach(branch, axes)
    val shell = ShellBuilder.shell(branch.part.value, covered.surface.patches[bodyPatch], branch.from, branch.to, BRANCH_RINGS, branch.ease, reach)
    val partId = branchPartId(garment.id, id)
    val mesh = PartMesh(partId.value, listOf(MeshBuilder.build(shell.patch) { 0 }))
    return PlacedPart(
        partId, covered.template, covered.side, PartSurface.of(mesh, axes, covered.mirrored), covered.origin,
        lengthFactor = covered.lengthFactor, turn = covered.turn, covering = Covering(garment.id, id, bodyPatch, shell.bodyRings),
    )
}

/**
 * How [branch]'s drawn outlines reach out past its ease at a ring: a Side outline sets the height and the middle of a ring it stands
 * off, a Top outline the width, both read at the ring's place along the part, in the part's own axes as the part's outlines are.
 */
private fun drawnReach(branch: GarmentBranch, axes: PartAxes): (Slice) -> ShellReach {
    val side = branch.outlines[View.SIDE]?.let { OutlineProfile.of(it.toPath().flatten()) }
    val top = branch.outlines[View.TOP]?.let { OutlineProfile.of(it.toPath().flatten()) }
    if (side == null && top == null) return { ShellReach() }
    return { slice ->
        val center = slice.frame.center
        val x = center dot axes.along
        val height = side?.takeIf { x in it.start..it.end }
        val width = top?.takeIf { x in it.start..it.end }
        ShellReach(
            height = height?.extentAt(x),
            width = width?.extentAt(x),
            up = height?.let { it.midAt(x) - (center dot axes.up) } ?: 0f,
            side = width?.let { -it.midAt(x) - (center dot axes.side) } ?: 0f,
        )
    }
}

/** The base color of every branch [character] wears: its garment's slot, else its own surface work's. */
fun garmentColors(character: Character): Map<PartId, PartColor> {
    val dressing = dressingOf(character)
    return dressing.worn.flatMap { garment ->
        garment.branches.flatMap { branch ->
            val color = PartColor(garment.color ?: branch.surface.baseSlot, emptyList())
            listOfNotNull(branch.part, dressing.twins[branch.part]).map { branchPartId(garment.id, it) to color }
        }
    }.toMap()
}

/** [colors] with the garments' colors [garments] holds, following both. */
fun Graph.dressedColorNode(colors: Node<*, BodyColor>, garments: Flow<Map<PartId, PartColor>>): Node<*, BodyColor> = gather(
    garments,
    sourcesOf = { mapOf(Unit to colors) },
    stage = BuildStages.SURFACE_WORK,
) { (worn, built) ->
    val body = built.getValue(Unit)
    if (worn.isEmpty()) body else BodyColor(body.parts + worn)
}
