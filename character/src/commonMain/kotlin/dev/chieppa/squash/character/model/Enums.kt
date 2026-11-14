package dev.chieppa.squash.character.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How a part meets the body on a character built as one surface. */
@Serializable
enum class SurfaceMethod {
    /** Its runs are members of the character's field, meshed with every other field part as one surface. */
    @SerialName("field") FIELD,

    /** Thin or hard: built as its own mesh and sitting on the body, as an ear or a claw. */
    @SerialName("separate") SEPARATE,
}

/** An end of a part's axis. */
@Serializable
enum class End {
    @SerialName("start") START,
    @SerialName("end") END,
}

/** What a view's outline sets on a part's slices. */
@Serializable
enum class Sets {
    @SerialName("height") HEIGHT,
    @SerialName("width") WIDTH,
    @SerialName("cap") CAP,
    @SerialName("section") SECTION,
}

@Serializable
enum class CapShape {
    @SerialName("round") ROUND,
    @SerialName("flat") FLAT,
    @SerialName("pointed") POINTED,
}

@Serializable
enum class Hinge {
    @SerialName("x") X,
    @SerialName("y") Y,
    @SerialName("z") Z,
}

/** The direction a child part leaves a socket. */
@Serializable
enum class Outward {
    @SerialName("axis") AXIS,
    @SerialName("normal") NORMAL,
}

/** The plane a paired socket or landmark mirrors across. */
@Serializable
enum class Mirror {
    @SerialName("x") X,
}

@Serializable
enum class AreaTag {
    @SerialName("rigid") RIGID,
    @SerialName("soft") SOFT,
    @SerialName("inner") INNER,

    /** Only a section to fill: weights and shading read nothing from it. */
    @SerialName("fill") FILL,
}
