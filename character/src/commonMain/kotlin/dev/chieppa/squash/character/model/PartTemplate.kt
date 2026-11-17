package dev.chieppa.squash.character.model

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.squash.character.block.Block
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.SectionProfile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

const val TEMPLATE_FORMAT = 1

/** The parameter that sets a part's axis length; 1 when a template has none. */
const val SIZE_PARAM = "size"

private const val DEFAULT_BEND_AT = 0.5f

@Serializable
@Doc("The rest direction before anything is drawn.")
data class AxisSpec(
    @Doc("From the socket end toward the tip, in body space.")
    val rest: Direction,
    @Doc("How long the axis is with nothing drawn, in body units; the size parameter multiplies it.")
    val length: Float = 1f,
    @Doc("How far the axis sits toward the underside at bendAt with nothing drawn, as a fraction of its length.")
    val bend: Param? = null,
    @Doc("Where along the axis it bends.") @Range(min = 0.0, max = 1.0)
    val bendAt: Float = DEFAULT_BEND_AT,
    @Doc("Whether the part stands on the ground, so a lean on one such part shortens the others to keep them all on it.")
    val bearsWeight: Boolean = false,
    @Doc("Where a head's face looks at rest, in body space, when not along the axis: the way its angle versions and gaze read.")
    val faces: Direction? = null,
    /** Where a turned part's up stays, so its drawings keep their meaning. Never in a file. */
    @Transient val up: Direction? = null,
)

/** The point of a closed part that sits on its socket, in place of its axis start. */
@Serializable
@Doc("The point of a closed part that sits on its socket, in place of its axis start.")
data class MountSpec(
    @Doc("Along the axis.") @Range(min = 0.0, max = 1.0)
    val u: Float,
    @Doc("Around the surface; underneath is 0.5.") @Range(min = 0.0, max = 1.0)
    val v: Float,
)

/** How finely a part's drawing is sampled: the rings and segments of its body patch's grid. */
@Serializable
data class Topology(
    @Doc("Rings along u.") @Range(min = 2.0)
    val rings: Int,
    @Doc("Control points of every section spline.") @Range(min = 3.0)
    val segments: Int,
    @Doc("A stretch of u that holds more than its share of the rings, as a face does; rings space evenly without it.")
    val focus: FocusSpec? = null,
)

/** A stretch of u that holds [share] of the ring spacings. */
@Serializable
data class FocusSpec(
    @Doc("The stretch of u, as a share of the run.")
    val u: Span,
    @Doc("The share of the ring spacings inside it.") @Range(min = 0.0, max = 1.0)
    val share: Float,
)

/** The height view's up side read as a profile, so a nose, lips or a beak stand out as a ridge rather than swelling the whole ring. */
@Serializable
data class ProfileSpec(
    @Doc("Features of the profile shorter than this share of the drawn length stand out as a ridge; longer ones, a forehead or a muzzle, swell the ring.") @Range(min = 0.0, max = 1.0)
    val span: Float,
    @Doc("How wide the ridge is, as a share of the ring's half width.") @Range(min = 0.0, max = 1.0)
    val keel: Float,
)

/** The recipe for a part type. See HLD: data-model, Part template file format. */
@Serializable
@Doc("A part template: axis, open end, sampling, blocks, parameters and which view sets what.")
data class PartTemplate(
    @SerialName("\$schema")
    val schemaPath: String? = null,
    @Doc("The grammar version of the file.") @Range(min = 1.0, max = 1.0)
    val format: Int,
    val id: TemplateId,
    @Doc("The template's own version; changes when what the template builds changes.") @Range(min = 1.0)
    val version: Int,
    val name: String,
    @Doc("What a part of this type is to the body, which a part placed from it takes; none for a generic part, whose role is guessed from where it sits.")
    val role: Role? = null,
    val axis: AxisSpec,
    @Doc("The end that sits on the socket plane; null for a closed part.")
    val openEnd: End? = null,
    @Doc("The point of a closed part that sits on its socket, in place of its axis start: a head carried under its middle.")
    val mount: MountSpec? = null,
    val topology: Topology,
    val views: Map<View, Sets>,
    @Doc("Reads the height view's up side as a profile whose short features stand out as a ridge; null reads it as an outline like any other.")
    val profile: ProfileSpec? = null,
    @Doc("The default section along u, used where no view sets it.")
    val section: SectionSpec = SectionSpec.Named(SectionProfile.CIRCLE),
    val params: Map<String, ParamSpec> = emptyMap(),
    val grain: GrainSpec = GrainSpec(),
    @Doc("Present on a generic template, whose parts choose their own section, views, rings and segments.")
    val generic: GenericSpec? = null,
    @Doc("Whether the part is in the character's field, or thin or hard and built as its own mesh sitting on the body.")
    val surface: SurfaceMethod = SurfaceMethod.FIELD,
    val blocks: List<Block>,
) {
    val ref: TemplateRef get() = TemplateRef(id, version)

    /** The value of every parameter when nothing has changed it. */
    val defaults: Map<String, Float> get() = params.mapValues { it.value.default.value }

    /**
     * The view whose plane an outline drawn in [view] lies in: Side's for the view that sets height, Top's for the one that
     * sets width, so a wing's planform drawn in Top lies across its chord; [view]'s own otherwise.
     */
    fun planeOf(view: View): View = when (views[view]) {
        Sets.HEIGHT -> View.SIDE
        Sets.WIDTH -> View.TOP
        else -> view
    }

    /** Whether a part of this type is a sheet, two faces meeting at an edge, as a fin is. */
    val isSheet: Boolean get() = blocks.any { it.isSheet() }

    /** The views drawn in another view's plane, by [planeOf]. */
    val viewPlanes: Map<View, View> get() = View.entries.associateWith(::planeOf).filter { (view, plane) -> view != plane }
}
