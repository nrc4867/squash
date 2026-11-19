package dev.chieppa.squash.recipe.export

import dev.chieppa.squash.character.build.DigitPatches
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.CharacterCreases
import dev.chieppa.squash.document.CharacterShading
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.LineRules
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.SurfaceWork
import dev.chieppa.squash.recipe.model.Recipe
import dev.chieppa.squash.recipe.model.RecipeCreases
import dev.chieppa.squash.recipe.model.RecipeChange
import dev.chieppa.squash.recipe.model.RecipeColor
import dev.chieppa.squash.recipe.model.RecipeCover
import dev.chieppa.squash.recipe.model.RecipeExpression
import dev.chieppa.squash.recipe.model.RecipeEye
import dev.chieppa.squash.recipe.model.RecipeGarment
import dev.chieppa.squash.recipe.model.RecipeHang
import dev.chieppa.squash.recipe.model.RecipePair
import dev.chieppa.squash.recipe.model.RecipePart
import dev.chieppa.squash.recipe.model.RecipeSlot
import dev.chieppa.squash.recipe.model.RecipeWardrobe

/** The shipped templates, which name the digit a socket placed on one sits on. */
private val shipped by lazy { TemplateFolder.shipped() }

/** A recipe written from a character, and what of the character it could not hold, one line each. */
class RecipeExport(val recipe: Recipe, val notCarried: List<String>)

/**
 * Writes [document]'s character as a recipe that builds it again: every part with its template version, socket, pair, role, outlines
 * as their own Bézier points, parameters, colour and surface work, then the eyes, expressions, wardrobe and display pose. What a recipe
 * has no place for is listed in [RecipeExport.notCarried] rather than dropped silently.
 */
fun exportRecipe(document: SquashDocument): RecipeExport {
    val character = document.character
    val notCarried = mutableListOf<String>()
    val parts = character.parts.filter { it.side != Side.RIGHT || character.twinOf(it) == null }.map { part(document, it, notCarried) }
    val wardrobe = character.wardrobe.takeIf { it.garments.isNotEmpty() || it.slots.isNotEmpty() }?.let { held ->
        RecipeWardrobe(held.slots, held.garments.map { garment(it, notCarried) }, held.outfits, held.shown?.value)
    }
    leftOut(document, notCarried)
    val recipe = Recipe(
        id = character.id.value,
        name = character.name,
        plan = character.bodyPlan.id.value,
        planVersion = character.bodyPlan.version,
        palette = character.palette.slots.map { RecipeSlot(it.name.value, RecipeColor.of(it.color), it.shadow?.let(RecipeColor::of), it.label, it.opacity) },
        parts = parts,
        eyes = character.paintedEyes.map { eye ->
            RecipeEye(eye.id, eye.anchor.part.value, eye.anchor.patch, eye.anchor.u, eye.anchor.v, null, eye.look, eye.front, eye.fromFront, eye.gazeForward, eye.gazeUp, eye.mirrored, eye.flat)
        },
        expressions = character.expressions.map { expression ->
            if (expression.marks.isNotEmpty()) notCarried += "expression ${expression.id.value}: its marks"
            RecipeExpression(expression.id.value, expression.name, expression.pose.joints, expression.open.mapKeys { it.key.value })
        },
        wardrobe = wardrobe,
        displayPose = bones(character.displayPose, "display pose", notCarried),
        creases = character.creases.takeIf { it != CharacterCreases() }?.let { RecipeCreases(it.skin, it.cloth, it.rest) },
    )
    return RecipeExport(recipe, notCarried)
}

/** The size [part]'s socket starts a part at, a row's share for a socket of a hand-made row, which a recipe's part with no size takes. */
private fun rowScale(document: SquashDocument, part: PartInstance): Float {
    val socket = part.socket ?: return 1f
    val row = HandSockets.rowOf(socket.socket) ?: return 1f
    val parent = document.character.part(socket.parent) ?: return 1f
    val params = document.character.resolvedShape(parent).params
    if (HandSockets.copiesOf(params, row) != null) return 1f
    return HandSockets.specs(params).firstOrNull { it.name == socket.socket }?.scale ?: 1f
}

private fun part(document: SquashDocument, part: PartInstance, notCarried: MutableList<String>): RecipePart {
    val character = document.character
    val twin = character.twinOf(part)
    val data = character.editBase(part, thisSide = false)
    val split = SplitParams(data.params) { digit -> shipped.template(part.template)?.let { DigitPatches.patchOf(it, digit) } }
    val surface = data.surface
    listOfNotNull(part, twin).filter { character.overridesOf(it) != PartData() }.forEach { notCarried += "${it.id.value}: values its side holds over its pair's" }
    if (twin != null && twin.blend != part.blend) notCarried += "${twin.id.value}: a blend apart from ${part.id.value}'s"
    surfaceLeftOut(part.id, surface, notCarried)
    val parent = part.socket?.parent?.let(character::part)
    val pairedOn = parent?.pair?.takeIf { twin != null && character.twinOf(parent)?.id == twin.socket?.parent }
    val on = pairedOn?.value ?: parent?.id?.value
    if (twin != null && twin.socket?.socket != part.socket?.socket) notCarried += "${twin.id.value}: a socket apart from ${part.id.value}'s"
    val pairId = part.pair?.value
    return RecipePart(
        id = pairId?.takeIf { twin != null } ?: part.id.value,
        template = part.template.id.value,
        version = part.template.version,
        on = on,
        socket = part.socket?.socket,
        pair = if (twin == null || pairId == null) null else RecipePair(part.id.value.takeIf { it != "$pairId-l" }, twin.id.value.takeIf { it != "$pairId-r" }),
        role = part.role.takeIf { !part.roleGuessed },
        garment = part.garment?.value,
        blend = part.blend,
        size = rowScale(document, part).let { scale -> if (scale == 1f) split.size else split.size?.takeIf { it != scale } ?: if (split.size == null) 1f else null },
        lean = split.lean,
        outward = split.outward,
        outlines = outlines(data.outlines),
        rest = split.rest,
        roll = split.roll,
        topMiddle = split.topMiddle,
        generic = split.generic,
        sockets = split.sockets,
        joints = split.joints,
        landmarks = split.landmarks,
        jointsAt = split.jointsAt,
        socketsAt = split.socketsAt,
        inkJoin = split.inkJoin,
        curve = split.curve,
        spiral = split.spiral,
        params = split.others,
        color = surface.baseSlot?.value,
        shades = surface.shades.map { it.value },
        areas = surface.areaFills.mapValues { it.value.value },
        regions = surface.regions.map { region(part.id, it) },
        marks = surface.marks.map { mark(part.id, it) },
        groups = surface.groups,
        fur = fur(surface),
        lineWeights = lineWeights(part.id, surface, notCarried),
    )
}

private fun garment(garment: Garment, notCarried: MutableList<String>): RecipeGarment {
    val where = "garment ${garment.id.value}"
    val painted = !paintsItsBoxes(garment)
    garment.branches.filter { it.surface != SurfaceWork() }.forEach { notCarried += "$where: surface work on its cover of ${it.part.value}" }
    return RecipeGarment(
        id = garment.id.value,
        name = garment.name,
        slot = garment.slot?.value,
        layer = garment.layer,
        color = garment.color?.value,
        fit = garment.fit,
        partFits = garment.partFits.mapKeys { it.key.value },
        inkHem = garment.inkHem,
        inkWeight = garment.inkWeight,
        hemRoll = garment.hemRoll,
        creases = garment.creases,
        wrinkles = garment.wrinkles,
        drape = garment.drape,
        eases = garment.eases.entries.associate { (part, profile) -> part.value to profile.keys.map { key -> listOfNotNull(key.u, key.ease, key.back) } },
        hang = garment.hang?.let { RecipeHang(it.limit, it.flare) },
        drops = garment.drops.mapKeys { it.key.value },
        joins = if (painted) emptyList() else garment.joins.map { it.value },
        areas = if (painted) garment.areas.map(::areaOf) else emptyList(),
        holes = holesOf(garment),
        tucks = tucksOf(garment),
        covers = garment.branches.map { RecipeCover(it.part.value, it.from, it.to, it.fromV, it.toV, it.ease, outlines(it.outlines)) },
        changes = garment.changes.entries.associate { (part, change) ->
            part.value to when (change) {
                BodyPartChange.Hide -> RecipeChange.Hide
                is BodyPartChange.Through -> RecipeChange.Through(change.cuff, change.cuffSize, change.inkCuff)
                is BodyPartChange.Redraw -> RecipeChange.Redraw(shape(part, change.data, notCarried, "$where on ${part.value}"))
                is BodyPartChange.Swap -> RecipeChange.Swap(change.template.id.value, change.template.version, shape(part, change.data, notCarried, "$where on ${part.value}"))
            }
        },
        looseEnds = garment.looseEnds,
        regions = garment.regions.map(::printedRegion),
        marks = garment.marks.map(::printedMark),
    )
}

private fun bones(pose: Pose, name: String, notCarried: MutableList<String>) = pose.bones.also {
    if (pose.planted.isNotEmpty() || pose.still.isNotEmpty() || pose.curve != null || pose.lookAt != null || pose.offset != Pose().offset) notCarried += "$name: planted paws, body curve, offset or look-at point"
}

private fun leftOut(document: SquashDocument, notCarried: MutableList<String>) {
    val character = document.character
    if (!character.pose.isRest) notCarried += "the working pose"
    if (character.shading != CharacterShading()) notCarried += "the character's shading choice"
    if (character.lineRules != LineRules()) notCarried += "the character's line rules"
    if (character.traces.isNotEmpty() || document.references.images.isNotEmpty()) notCarried += "reference and trace images"
    if (character.shownExpression != null) notCarried += "the shown expression"
}
