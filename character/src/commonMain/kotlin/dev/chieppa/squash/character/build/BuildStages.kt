package dev.chieppa.squash.character.build

/** The stages of a part's derivation, in the order a change passes through them, as the graph reports them. */
object BuildStages {
    const val SLICES = "slices"
    const val MESH = "mesh"
    const val SURFACE = "surface"
    const val ASSEMBLY = "assembly"
    const val SURFACE_WORK = "surface work"

    val ORDER = listOf(SLICES, MESH, SURFACE, ASSEMBLY, SURFACE_WORK)
}
