package dev.chieppa.squash.document

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What a worn garment does to one body part under it. The change belongs to the garment, so taking it off gives the part back. */
@Serializable
sealed interface BodyPartChange {
    /** The part is not built while the garment is worn. */
    @Serializable
    @SerialName("hide")
    data object Hide : BodyPartChange

    /** The part comes out through a hole in the garment, with a [cuff] of [cuffSize] round it, inked when [inkCuff]. */
    @Serializable
    @SerialName("through")
    data class Through(val cuff: Boolean = true, val cuffSize: Float = DEFAULT_CUFF_SIZE, val inkCuff: Boolean = true) : BodyPartChange

    /** The part keeps its template and takes [data], the garment's own outlines, parameters and surface work, over its own. */
    @Serializable
    @SerialName("redraw")
    data class Redraw(val data: PartData = PartData()) : BodyPartChange

    /** The part takes [template] with [data] while the garment is worn. */
    @Serializable
    @SerialName("swap")
    data class Swap(val template: TemplateRef, val data: PartData = PartData()) : BodyPartChange

    companion object {
        const val DEFAULT_CUFF_SIZE = 0.25f
    }
}
