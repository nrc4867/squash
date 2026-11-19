package dev.chieppa.squash.screens.shell

import androidx.compose.runtime.Composable
import dev.chieppa.squash.character.catalog.displayName
import dev.chieppa.squash.document.AddGarment
import dev.chieppa.squash.document.AddGarmentSlot
import dev.chieppa.squash.document.AddOutfit
import dev.chieppa.squash.document.CoverPart
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PickGarment
import dev.chieppa.squash.document.RemoveGarment
import dev.chieppa.squash.document.RemoveGarmentSlot
import dev.chieppa.squash.document.RemoveLooseEnd
import dev.chieppa.squash.document.RemoveOutfit
import dev.chieppa.squash.document.RenameGarment
import dev.chieppa.squash.document.RenameGarmentSlot
import dev.chieppa.squash.document.RenameOutfit
import dev.chieppa.squash.document.SetBodyPartChange
import dev.chieppa.squash.document.SetBranchAround
import dev.chieppa.squash.document.SetBranchEase
import dev.chieppa.squash.document.SetBranchOutline
import dev.chieppa.squash.document.SetBranchRange
import dev.chieppa.squash.document.SetBranchSurface
import dev.chieppa.squash.document.SetHemInk
import dev.chieppa.squash.document.SetGarmentFit
import dev.chieppa.squash.document.SetPartFit
import dev.chieppa.squash.document.SetGarmentPaint
import dev.chieppa.squash.document.RenameGarmentArea
import dev.chieppa.squash.document.SetGarmentMark
import dev.chieppa.squash.document.SetGarmentRegion
import dev.chieppa.squash.document.SetGarmentAreaColor
import dev.chieppa.squash.document.SetTuck
import dev.chieppa.squash.document.SetGarment
import dev.chieppa.squash.document.SetGarmentColor
import dev.chieppa.squash.document.SetGarmentSlot
import dev.chieppa.squash.document.SetJoinInk
import dev.chieppa.squash.document.SetLayers
import dev.chieppa.squash.document.SetLooseEnd
import dev.chieppa.squash.document.SetWardrobe
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.UncoverPart
import dev.chieppa.squash.document.View
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.dress_step_add_garment
import dev.chieppa.squash.uidesign.generated.dress_step_add_outfit
import dev.chieppa.squash.uidesign.generated.dress_step_add_slot
import dev.chieppa.squash.uidesign.generated.dress_step_around
import dev.chieppa.squash.uidesign.generated.dress_step_bare
import dev.chieppa.squash.uidesign.generated.dress_step_change
import dev.chieppa.squash.uidesign.generated.dress_step_color
import dev.chieppa.squash.uidesign.generated.dress_step_cover
import dev.chieppa.squash.uidesign.generated.dress_step_ease
import dev.chieppa.squash.uidesign.generated.dress_step_edge_ink
import dev.chieppa.squash.uidesign.generated.dress_step_fit
import dev.chieppa.squash.uidesign.generated.dress_step_part_fit
import dev.chieppa.squash.uidesign.generated.dress_step_paint
import dev.chieppa.squash.uidesign.generated.dress_step_area_name
import dev.chieppa.squash.uidesign.generated.dress_step_tuck
import dev.chieppa.squash.uidesign.generated.dress_step_garment
import dev.chieppa.squash.uidesign.generated.dress_step_join_ink
import dev.chieppa.squash.uidesign.generated.dress_step_layers
import dev.chieppa.squash.uidesign.generated.dress_step_loose_end
import dev.chieppa.squash.uidesign.generated.dress_step_remove_print
import dev.chieppa.squash.uidesign.generated.dress_step_print
import dev.chieppa.squash.uidesign.generated.dress_step_area_color
import dev.chieppa.squash.uidesign.generated.dress_step_outline
import dev.chieppa.squash.uidesign.generated.dress_step_pick
import dev.chieppa.squash.uidesign.generated.dress_step_range
import dev.chieppa.squash.uidesign.generated.history_remove_garment
import dev.chieppa.squash.uidesign.generated.dress_step_remove_loose_end
import dev.chieppa.squash.uidesign.generated.history_remove_outfit
import dev.chieppa.squash.uidesign.generated.dress_step_remove_slot
import dev.chieppa.squash.uidesign.generated.history_rename_garment
import dev.chieppa.squash.uidesign.generated.history_new_worn_garment
import dev.chieppa.squash.uidesign.generated.history_duplicate_outfit
import dev.chieppa.squash.document.AddWornGarment
import dev.chieppa.squash.document.DuplicateOutfit
import dev.chieppa.squash.uidesign.generated.history_rename_outfit
import dev.chieppa.squash.uidesign.generated.dress_step_rename_slot
import dev.chieppa.squash.uidesign.generated.dress_step_show
import dev.chieppa.squash.uidesign.generated.dress_step_slot
import dev.chieppa.squash.uidesign.generated.dress_step_surface
import dev.chieppa.squash.uidesign.generated.dress_step_uncover
import dev.chieppa.squash.uidesign.generated.dress_step_unpick
import dev.chieppa.squash.uidesign.generated.dress_step_wardrobe
import dev.chieppa.stretch.core.Command
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val FIRST = "\u0001"
private const val SECOND = "\u0002"

/**
 * How the History drawer names each wardrobe and garment command, or an empty label for any other command. Garment and part ids read as
 * names, and [views] names each view.
 */
@Composable
internal fun dressLabels(views: Map<View, String>): (Command<SquashDocument>) -> String {
    val texts = DRESS_STEPS.associateWith { stringResource(it, FIRST, SECOND) }
    fun text(step: StringResource, first: String = "", second: String = "") = texts.getValue(step).replace(FIRST, first).replace(SECOND, second)
    fun name(id: GarmentId) = displayName(id.value)
    fun name(id: PartId) = displayName(id.value)
    return { command ->
        when (command) {
            is SetWardrobe -> text(Res.string.dress_step_wardrobe)
            is AddGarment -> text(Res.string.dress_step_add_garment, command.garment.name)
            is RemoveGarment -> text(Res.string.history_remove_garment, name(command.garment))
            is SetGarment -> text(Res.string.dress_step_garment, command.garment.name)
            is RenameGarment -> text(Res.string.history_rename_garment, command.name)
            is AddWornGarment -> text(Res.string.history_new_worn_garment, command.garment.slot?.let { displayName(it.value) }.orEmpty())
            is CoverPart -> text(Res.string.dress_step_cover, name(command.garment), name(command.branch.part))
            is UncoverPart -> text(Res.string.dress_step_uncover, name(command.garment), name(command.part))
            is SetBranchRange -> text(Res.string.dress_step_range, name(command.garment))
            is SetBranchAround -> text(Res.string.dress_step_around, name(command.garment))
            is SetBranchEase -> text(Res.string.dress_step_ease, name(command.garment))
            is SetBranchOutline -> text(Res.string.dress_step_outline, name(command.garment), views[command.view].orEmpty())
            is SetBranchSurface -> text(Res.string.dress_step_surface, name(command.garment))
            is SetJoinInk -> text(Res.string.dress_step_join_ink, name(command.garment))
            is SetHemInk -> text(Res.string.dress_step_edge_ink, name(command.garment))
            is SetGarmentFit -> text(Res.string.dress_step_fit, name(command.garment))
            is SetPartFit -> text(Res.string.dress_step_part_fit, name(command.garment), name(command.part))
            is SetGarmentPaint -> text(Res.string.dress_step_paint, name(command.garment))
            is RenameGarmentArea -> text(Res.string.dress_step_area_name, command.name)
            is SetTuck -> text(Res.string.dress_step_tuck, name(command.garment), name(command.part))
            is SetGarmentColor -> text(Res.string.dress_step_color, name(command.garment))
            is SetBodyPartChange -> text(Res.string.dress_step_change, name(command.garment), name(command.part))
            is SetLooseEnd -> text(Res.string.dress_step_loose_end, name(command.garment))
            is SetGarmentAreaColor -> text(Res.string.dress_step_area_color, name(command.garment))
            is SetGarmentRegion -> text(if (command.region == null) Res.string.dress_step_remove_print else Res.string.dress_step_print, name(command.garment), command.id.value)
            is SetGarmentMark -> text(if (command.mark == null) Res.string.dress_step_remove_print else Res.string.dress_step_print, name(command.garment), command.id.value)
            is RemoveLooseEnd -> text(Res.string.dress_step_remove_loose_end, name(command.garment))
            is SetGarmentSlot -> text(Res.string.dress_step_slot, name(command.garment))
            is SetLayers -> text(Res.string.dress_step_layers)
            is AddGarmentSlot -> text(Res.string.dress_step_add_slot, command.slot.name)
            is RemoveGarmentSlot -> text(Res.string.dress_step_remove_slot)
            is RenameGarmentSlot -> text(Res.string.dress_step_rename_slot, command.name)
            is AddOutfit -> text(Res.string.dress_step_add_outfit, command.outfit.name)
            is RemoveOutfit -> text(Res.string.history_remove_outfit, displayName(command.outfit.value))
            is RenameOutfit -> text(Res.string.history_rename_outfit, command.name)
            is DuplicateOutfit -> text(Res.string.history_duplicate_outfit, displayName(command.source.value))
            is PickGarment -> command.garment?.let { text(Res.string.dress_step_pick, name(it)) } ?: text(Res.string.dress_step_unpick)
            is ShowOutfit -> command.outfit?.let { text(Res.string.dress_step_show, displayName(it.value)) } ?: text(Res.string.dress_step_bare)
            else -> ""
        }
    }
}

private val DRESS_STEPS = listOf(
    Res.string.dress_step_wardrobe, Res.string.dress_step_add_garment, Res.string.history_remove_garment, Res.string.dress_step_garment,
    Res.string.history_rename_garment, Res.string.history_new_worn_garment, Res.string.dress_step_cover, Res.string.dress_step_uncover, Res.string.dress_step_range,
    Res.string.dress_step_around, Res.string.dress_step_ease, Res.string.dress_step_outline, Res.string.dress_step_surface,
    Res.string.dress_step_join_ink, Res.string.dress_step_edge_ink, Res.string.dress_step_fit, Res.string.dress_step_part_fit, Res.string.dress_step_paint, Res.string.dress_step_area_name, Res.string.dress_step_tuck, Res.string.dress_step_color,
    Res.string.dress_step_change, Res.string.dress_step_loose_end, Res.string.dress_step_remove_loose_end, Res.string.dress_step_slot,
    Res.string.dress_step_area_color, Res.string.dress_step_print, Res.string.dress_step_remove_print,
    Res.string.dress_step_layers, Res.string.dress_step_add_slot, Res.string.dress_step_remove_slot, Res.string.dress_step_rename_slot,
    Res.string.dress_step_add_outfit, Res.string.history_remove_outfit, Res.string.history_rename_outfit, Res.string.history_duplicate_outfit, Res.string.dress_step_pick,
    Res.string.dress_step_unpick, Res.string.dress_step_show, Res.string.dress_step_bare,
)
