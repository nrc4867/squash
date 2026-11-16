package dev.chieppa.squash.screens.shell.library

import dev.chieppa.squash.screens.library.LibraryGroup
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.library_group_body
import dev.chieppa.squash.uidesign.generated.library_group_hands_feet
import dev.chieppa.squash.uidesign.generated.library_group_head
import dev.chieppa.squash.uidesign.generated.library_group_horns_claws
import dev.chieppa.squash.uidesign.generated.library_group_limbs
import dev.chieppa.squash.uidesign.generated.library_group_shapes
import dev.chieppa.squash.uidesign.generated.library_group_tail
import dev.chieppa.squash.uidesign.generated.library_group_wings_fins
import org.jetbrains.compose.resources.StringResource

/** A Library group's name. */
val LibraryGroup.label: StringResource
    get() = when (this) {
        LibraryGroup.BODY -> Res.string.library_group_body
        LibraryGroup.HEAD -> Res.string.library_group_head
        LibraryGroup.LIMBS -> Res.string.library_group_limbs
        LibraryGroup.HANDS_FEET -> Res.string.library_group_hands_feet
        LibraryGroup.TAIL -> Res.string.library_group_tail
        LibraryGroup.WINGS_FINS -> Res.string.library_group_wings_fins
        LibraryGroup.HORNS_CLAWS -> Res.string.library_group_horns_claws
        LibraryGroup.SHAPES -> Res.string.library_group_shapes
    }
