package dev.chieppa.squash.document

import dev.chieppa.stretch.core.Applied
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Sets the ease [garment]'s area [area] gathers its cloth in to, as a ribbed band; null lets the cloth stand off as the rest does. */
@Serializable
@SerialName("setAreaGather")
data class SetAreaGather(val garment: GarmentId, val area: GarmentAreaId, val gather: Float?) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.garmentChanged(garment) { held ->
        require(held.area(area) != null) { "Garment ${garment.value} has no area ${area.value}" }
        held.copy(areas = held.areas.map { if (it.id == area) it.copy(gather = gather?.coerceAtLeast(0f)) else it })
    }
}

/** Sets how thick [garment]'s area [area] folds back over the cloth beside it, as a rolled cuff; null lies it flat. */
@Serializable
@SerialName("setAreaFold")
data class SetAreaFold(val garment: GarmentId, val area: GarmentAreaId, val fold: Float?) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.garmentChanged(garment) { held ->
        require(held.area(area) != null) { "Garment ${garment.value} has no area ${area.value}" }
        held.copy(areas = held.areas.map { if (it.id == area) it.copy(fold = fold?.coerceIn(0f, MOST_FOLD)) else it })
    }
}

/** Makes [garment]'s area [area] sheer at [opacity], held to 0 to 1; null makes it whole. */
@Serializable
@SerialName("setAreaOpacity")
data class SetAreaOpacity(val garment: GarmentId, val area: GarmentAreaId, val opacity: Float?) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.garmentChanged(garment) { held ->
        require(held.area(area) != null) { "Garment ${garment.value} has no area ${area.value}" }
        held.copy(areas = held.areas.map { if (it.id == area) it.copy(opacity = opacity?.coerceIn(0f, 1f)) else it })
    }
}

/** Knits [garment]'s area [area] as [net]; null makes it plain cloth. */
@Serializable
@SerialName("setAreaNet")
data class SetAreaNet(val garment: GarmentId, val area: GarmentAreaId, val net: ClothNet?) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.garmentChanged(garment) { held ->
        require(held.area(area) != null) { "Garment ${garment.value} has no area ${area.value}" }
        held.copy(areas = held.areas.map { if (it.id == area) it.copy(net = net) else it })
    }
}
