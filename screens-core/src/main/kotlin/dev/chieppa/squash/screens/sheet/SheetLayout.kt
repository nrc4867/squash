package dev.chieppa.squash.screens.sheet

import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.sheet_layout_focus
import dev.chieppa.squash.uidesign.generated.sheet_layout_full_first
import dev.chieppa.squash.uidesign.generated.sheet_layout_sheet
import dev.chieppa.squash.uidesign.generated.sheet_layout_single
import org.jetbrains.compose.resources.StringResource

/** The workspace's layouts, in the Layout control's order. */
enum class SheetLayout(val label: StringResource, val built: Boolean = false) {
    SINGLE(Res.string.sheet_layout_single, built = true),
    FOCUS(Res.string.sheet_layout_focus),
    SHEET(Res.string.sheet_layout_sheet, built = true),
    FULL_FIRST(Res.string.sheet_layout_full_first, built = true),
}
