package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.build.GenericParams
import dev.chieppa.squash.character.build.sectionProfile
import dev.chieppa.squash.character.catalog.VIEW_ORDER
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.SectionProfile
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.generic_circle
import dev.chieppa.squash.uidesign.generated.generic_egg
import dev.chieppa.squash.uidesign.generated.generic_keel
import dev.chieppa.squash.uidesign.generated.generic_lens
import dev.chieppa.squash.uidesign.generated.generic_rings
import dev.chieppa.squash.uidesign.generated.generic_section
import dev.chieppa.squash.uidesign.generated.generic_segments
import dev.chieppa.squash.uidesign.generated.generic_title
import dev.chieppa.squash.uidesign.generated.generic_views
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.ui.NumberField
import dev.chieppa.stretch.ui.OptionChip
import dev.chieppa.stretch.ui.Segmented
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private val PROFILE_LABELS: Map<SectionProfile, StringResource> = mapOf(
    SectionProfile.CIRCLE to Res.string.generic_circle,
    SectionProfile.EGG to Res.string.generic_egg,
    SectionProfile.KEEL to Res.string.generic_keel,
    SectionProfile.LENS to Res.string.generic_lens,
)

/** What a generic part has chosen, from [params] over its [template]'s defaults. */
internal data class GenericChoice(val profile: SectionProfile, val views: Set<View>, val rings: Int, val segments: Int) {
    companion object {
        fun of(template: PartTemplate, params: Map<String, Float>): GenericChoice = GenericChoice(
            params[GenericParams.SECTION]?.roundToInt()?.let(GenericParams.PROFILES::getOrNull) ?: template.sectionProfile() ?: SectionProfile.CIRCLE,
            GenericParams.READINGS.keys.filter { view -> params[GenericParams.view(view)]?.let { it != 0f } ?: (view in template.views) }.toSet(),
            params[GenericParams.RINGS]?.roundToInt() ?: template.topology.rings,
            params[GenericParams.SEGMENTS]?.roundToInt() ?: template.topology.segments,
        )
    }
}

/**
 * A generic part's own shape: its section profile, which views set it, one always staying on, and its rings and segments within its
 * template's limits. Each change is one [GenericParams] command, to this side alone with [thisSide]. A part of any other template
 * shows nothing here.
 */
@Composable
internal fun GenericSection(session: CharacterSession, part: PartId, template: PartTemplate, params: Map<String, Float>, thisSide: Boolean) {
    val generic = template.generic ?: return
    val choice = GenericChoice.of(template, params)
    val labels = PROFILE_LABELS.mapValues { stringResource(it.value) }
    DrawerSection(stringResource(Res.string.generic_title)) {
        Column(Modifier.fillMaxWidth().testTag(ShellTags.GENERIC), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText(stringResource(Res.string.generic_section), style = Stretch.type.body.merge(TextStyle(color = Stretch.colors.inkControl)))
            Segmented(
                GenericParams.PROFILES,
                choice.profile,
                { session.send(GenericParams.section(part, it, thisSide)) },
                { labels.getValue(it) },
                Modifier.fillMaxWidth().testTag(ShellTags.GENERIC_SECTION),
                fill = true,
            )
            SettingRow(stringResource(Res.string.generic_views)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (view in VIEW_ORDER) {
                        OptionChip(viewLabel(view), view in choice.views, { wanted ->
                            if (wanted || choice.views.size > 1) session.send(GenericParams.showView(part, view, wanted, thisSide))
                        }, Modifier.testTag(ShellTags.genericView(view.name)))
                    }
                }
            }
            CountRow(stringResource(Res.string.generic_rings), choice.rings, generic.rings.min..generic.rings.max, ShellTags.GENERIC_RINGS) {
                session.send(GenericParams.rings(part, it, thisSide))
            }
            CountRow(stringResource(Res.string.generic_segments), choice.segments, generic.segments.min..generic.segments.max, ShellTags.GENERIC_SEGMENTS) {
                session.send(GenericParams.segments(part, it, thisSide))
            }
        }
    }
}

/** A whole number between [range]'s ends; a change to another value sends [onChange] once. */
@Composable
private fun CountRow(label: String, value: Int, range: IntRange, tag: String, onChange: (Int) -> Unit) {
    SettingRow(label) {
        Spacer(Modifier.weight(1f))
        NumberField(
            value.toFloat(),
            { typed -> typed.roundToInt().coerceIn(range).takeIf { it != value }?.let(onChange) },
            label,
            Modifier.testTag(tag),
            range = range.first.toFloat()..range.last.toFloat(),
            format = { it.roundToInt().toString() },
        )
    }
}
