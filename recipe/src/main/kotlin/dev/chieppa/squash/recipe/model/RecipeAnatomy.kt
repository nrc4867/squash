package dev.chieppa.squash.recipe.model

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.character.build.RestDirection
import dev.chieppa.squash.character.model.Hinge
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.SectionProfile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A joint placed by hand on a part, held as the part's `joint.<n>.*` parameters. */
@Serializable
@Doc("A hand-made joint, named joint.<n>: the rig makes a bone of it that owns the part from here to the next joint.")
data class RecipeJoint(
    @Doc("Its number: the joint is named joint.<n>, which poses name.")
    val n: Int,
    @Doc("Where along the part, 0 at its socket end to 1 at its far end.")
    val u: Float,
    @Doc("The part's axis it turns about: x across the part (a knee or elbow), y about the part's up, z about its length.")
    val hinge: Hinge = Hinge.X,
    @Doc("The least it turns, in degrees.")
    val low: Float = -40f,
    @Doc("The most it turns, in degrees.")
    val high: Float = 40f,
    @Doc("Whether a stretch handle may move it away from the joint before it.")
    val stretch: Boolean = false,
)

/** A landmark placed by hand on a part, held as the part's `landmark.<n>.*` parameters. */
@Serializable
@Doc("A hand-made landmark, named landmark.<n>: a named surface point mark groups anchor to.")
data class RecipeLandmark(
    val n: Int,
    @Doc("Where along the part.")
    val u: Float,
    @Doc("Where round the part.")
    val v: Float,
)

/** What a part of a generic template (tube, blob, sheet) chooses for itself, held as its `generic.*` parameters. */
@Serializable
@Doc("A generic part's own section, views and sampling (tube, blob and sheet only).")
data class RecipeGeneric(
    @Doc("The cross-section profile where Front draws none.")
    val section: RecipeSection? = null,
    @Doc("The views whose outlines shape it: side sets height, top width, front the section. All three when left out.")
    val views: List<View>? = null,
    @Doc("Rings along the part, up to 96: more for a long or detailed part.")
    val rings: Int? = null,
    @Doc("Control points round each ring, up to 32.")
    val segments: Int? = null,
)

/** The built-in section profiles by name, in the order a generic part's `generic.section` counts them. */
@Serializable
enum class RecipeSection(val profile: SectionProfile) {
    @SerialName("circle") CIRCLE(SectionProfile.CIRCLE),
    @SerialName("egg") EGG(SectionProfile.EGG),
    @SerialName("keel") KEEL(SectionProfile.KEEL),
    @SerialName("lens") LENS(SectionProfile.LENS),
}

/** A direction a part rests in of its own, from its socket end toward its tip, in place of its template's. */
@Serializable
enum class RecipeRest(val direction: RestDirection) {
    @SerialName("forward") FORWARD(RestDirection.FORWARD),
    @SerialName("back") BACK(RestDirection.BACK),
    @SerialName("up") UP(RestDirection.UP),
    @SerialName("down") DOWN(RestDirection.DOWN),

    /** Away from the body's middle plane on the left; the right side mirrors it. */
    @SerialName("out") OUT(RestDirection.OUT),
}
