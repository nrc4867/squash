package dev.chieppa.squash.geom.blend

/**
 * The fields fitted over one run of fillets, each kept by the key it was asked under, so a patch several others fillet into is
 * fitted once. A key names a patch as it lies, so it must change whenever the patch or where it is placed does.
 */
class FieldCache {
    private val held = HashMap<Any, HermiteField?>()

    /** The field kept under [key], fitted by [fit] the first time it is asked for. */
    fun of(key: Any, fit: () -> HermiteField?): HermiteField? = if (key in held) held[key] else fit().also { held[key] = it }
}
