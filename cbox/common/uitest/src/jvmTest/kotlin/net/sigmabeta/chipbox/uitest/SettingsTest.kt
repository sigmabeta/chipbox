package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.settings.Settings
import kotlin.test.Test

/**
 * Settings shows its grouped sections; tapping the Theme dropdown row expands its options.
 *
 * (The library actions aren't asserted in isolation here: "Rescan library" starts the scanner, which
 * touches `Dispatchers.Main` outside the harness's controlled dispatcher; "Add folder" emits a
 * folder-picker event handled outside the harness; and "Manage Library" only appears once a folder
 * exists. The debug section's navigation rows are gated behind a debug toggle that's off by default —
 * the screens they reach are covered by their own tests.)
 */
class SettingsTest {
    @Test
    fun showsSections() = runChipboxUiTest {
        startAtScreen(Settings)

        assertSectionHeader("Appearance")
        assertNameCaptionItemDisplayed("Rescan library")
    }

    @Test
    fun expandingThemeDropdownShowsOptions() = runChipboxUiTest {
        startAtScreen(Settings)

        click("Theme")

        assertSingleTextItemDisplayed("Light")
    }
}
