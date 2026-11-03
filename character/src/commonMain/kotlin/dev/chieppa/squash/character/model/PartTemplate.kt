package dev.chieppa.squash.character.model

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.squash.character.block.Block
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.SectionProfile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val TEMPLATE_FORMAT = 1

/** The parameter that sets a part's axis length; 1 when a template has none. */
const val SIZE_PARAM = "size"

@Serializable
@Doc("The rest direction before anything is drawn.")
data class AxisSpec(
    @Doc("From the socket end toward the tip, in body space.")
    val rest: Direction,
)

/** The body patch's fixed grid. */
@Serializable
data class Topology(
    @Doc("Rings along u.") @Range(min = 2.0)
    val rings: Int,
    @Doc("Control points of every section spline.") @Range(min = 3.0)
    val segments: Int,
)

/** The recipe for a part type. See HLD: data-model, Part template file format. */
@Serializable
@Doc("A part template: axis, open end, topology, blocks, parameters and which view sets what.")
data class PartTemplate(
    @SerialName("\$schema")
    val schemaPath: String? = null,
    @Doc("The grammar version of the file.") @Range(min = 1.0, max = 1.0)
    val format: Int,
    val id: TemplateId,
    @Doc("The template's own version; changes when its topology or joints change.") @Range(min = 1.0)
    val version: Int,
    val name: String,
    val axis: AxisSpec,
    @Doc("The end that sits on the socket plane; null for a closed part.")
    val openEnd: End? = null,
    val topology: Topology,
    val views: Map<View, Sets>,
    @Doc("The default section along u, used where no view sets it.")
    val section: SectionSpec = SectionSpec.Named(SectionProfile.CIRCLE),
    val params: Map<String, ParamSpec> = emptyMap(),
    val blocks: List<Block>,
) {
    val ref: TemplateRef get() = TemplateRef(id, version)

    /** The value of every parameter when nothing has changed it. */
    val defaults: Map<String, Float> get() = params.mapValues { it.value.default.value }
}
