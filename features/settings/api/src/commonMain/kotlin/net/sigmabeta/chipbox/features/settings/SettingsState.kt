package net.sigmabeta.chipbox.features.settings

import kotlinx.collections.immutable.toImmutableList
import net.sigmabeta.chipbox.settings.ThemeMode
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.chipbox.ui.fonts.ChipboxFont
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.appinfo.AppInfo
import net.sigmabeta.sage.components.DropdownSettingListModel
import net.sigmabeta.sage.components.LabelValueListModel
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.components.LoadingItemListModel
import net.sigmabeta.sage.components.LoadingType
import net.sigmabeta.sage.components.NameCaptionListModel
import net.sigmabeta.sage.components.SectionHeaderListModel
import net.sigmabeta.sage.components.SingleTextListModel
import net.sigmabeta.sage.components.TitleBarModel
import net.sigmabeta.sage.list.ListState
import net.sigmabeta.sage.ui.StringProvider

data class SettingsState(
    val brandFont: String? = null,
    val plainFont: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val rescanStatus: LCE<Unit> = LCE.Uninitialized,
    val clearLibraryStatus: LCE<Unit> = LCE.Uninitialized,
    val appInfo: AppInfo? = null,
    val formattedBuildDate: String? = null,
    val debugClickCount: Int = 0,
    val shouldShowDebug: Boolean? = null,
    val hasLibraryFolders: Boolean = false,
) : ListState() {
    override fun title(stringProvider: StringProvider) = TitleBarModel(
        title = stringProvider.getString(ChipboxStringId.SETTINGS_SCREEN_TITLE),
        shouldShowBack = true,
    )

    override fun toListItems(stringProvider: StringProvider): List<ListModel> = appearanceSection(stringProvider) +
            librarySection(stringProvider) +
            aboutSection(stringProvider) +
            debugSection(stringProvider)

    private fun appearanceSection(stringProvider: StringProvider): List<ListModel> = listOf(
        sectionHeader(stringProvider, ChipboxStringId.SETTINGS_SECTION_APPEARANCE),
        themeDropdown(stringProvider),
        fontDropdown(
            stringProvider,
            ChipboxStringId.SETTINGS_LABEL_BRAND_FONT,
            brandFont,
            ChipboxFont.DEFAULT_BRAND,
        ) { SettingsAction.BrandFontSelected(it) },
        fontDropdown(
            stringProvider,
            ChipboxStringId.SETTINGS_LABEL_PLAIN_FONT,
            plainFont,
            ChipboxFont.DEFAULT_PLAIN,
        ) { SettingsAction.PlainFontSelected(it) },
    )

    private fun librarySection(stringProvider: StringProvider): List<ListModel> = listOfNotNull(
        sectionHeader(stringProvider, ChipboxStringId.SETTINGS_SECTION_LIBRARY),
        libraryFolderRow(stringProvider),
        rescanRow(stringProvider),
        rescanStatusRowOrNull(stringProvider),
        clearLibraryRow(stringProvider),
    )

    // Only present while a scan is running (rescanStatus is Loading) — taps open the live scan
    // progress screen. The rescanRow above shows the inline spinner; this is the way in for detail.
    private fun rescanStatusRowOrNull(stringProvider: StringProvider): ListModel? {
        if (rescanStatus !is LCE.Loading) return null
        return NameCaptionListModel(
            dataId = ChipboxStringId.SETTINGS_LABEL_RESCAN_STATUS.hashCode().toLong(),
            name = stringProvider.getString(ChipboxStringId.SETTINGS_LABEL_RESCAN_STATUS),
            caption = stringProvider.getString(ChipboxStringId.SETTINGS_CAPTION_RESCAN_STATUS),
            clickAction = SettingsAction.RescanStatusClicked,
        )
    }

    // Once the library has at least one folder, the "Add folder to library" shortcut becomes a
    // "Manage Library" row that opens the dedicated screen (add via its CTA, remove by tapping a
    // folder). Empty library keeps the one-tap add shortcut.
    private fun libraryFolderRow(stringProvider: StringProvider): ListModel =
        if (hasLibraryFolders) manageLibraryRow(stringProvider) else addFolderRow(stringProvider)

    private fun manageLibraryRow(stringProvider: StringProvider) = NameCaptionListModel(
        dataId = ChipboxStringId.MANAGE_LIBRARY_TITLE.hashCode().toLong(),
        name = stringProvider.getString(ChipboxStringId.MANAGE_LIBRARY_TITLE),
        caption = stringProvider.getString(ChipboxStringId.MANAGE_LIBRARY_CAPTION),
        clickAction = SettingsAction.ManageLibraryClicked,
    )

    private fun aboutSection(stringProvider: StringProvider): List<ListModel> = listOf(
        sectionHeader(stringProvider, ChipboxStringId.SETTINGS_SECTION_ABOUT),
        appVersionRow(stringProvider),
        buildDateRow(stringProvider),
        licensesRow(stringProvider),
    )

    private fun debugSection(stringProvider: StringProvider): List<ListModel> {
        if (shouldShowDebug != true) return emptyList()
        return listOf(
            sectionHeader(stringProvider, ChipboxStringId.SETTINGS_SECTION_DEBUG),
            playbackStatusRow(stringProvider),
            appBranchRow(stringProvider),
            versionCodeRow(stringProvider),
        )
    }

    private fun sectionHeader(stringProvider: StringProvider, id: ChipboxStringId) =
        SectionHeaderListModel(title = stringProvider.getString(id))

    // Light / Dark / Match system. Option order mirrors `ThemeMode.entries`, so the picked
    // index maps straight back to a `ThemeMode`. The selection is routed through the action
    // sink (unlike the font dropdowns) and persisted by SettingsViewModel.
    private fun themeDropdown(stringProvider: StringProvider): ListModel = DropdownSettingListModel(
        settingId = ChipboxStringId.SETTINGS_LABEL_THEME.name,
        name = stringProvider.getString(ChipboxStringId.SETTINGS_LABEL_THEME),
        selectedPosition = themeMode.ordinal,
        settingsLabels = ThemeMode.entries
            .map { stringProvider.getString(it.labelId()) }
            .toImmutableList(),
        onNewOptionSelected = { index -> SettingsAction.ThemeModeSelected(ThemeMode.entries[index]) },
    )

    private fun ThemeMode.labelId(): ChipboxStringId = when (this) {
        ThemeMode.LIGHT -> ChipboxStringId.SETTINGS_THEME_LIGHT
        ThemeMode.DARK -> ChipboxStringId.SETTINGS_THEME_DARK
        ThemeMode.SYSTEM -> ChipboxStringId.SETTINGS_THEME_SYSTEM
    }

    // Renders the persisted font (falling back to [defaultFont] when unset, so the shown
    // selection matches what ChipboxAppUi actually applies) and dispatches [onSelected] with
    // the picked entry, which SettingsViewModel persists.
    private fun fontDropdown(
        stringProvider: StringProvider,
        labelId: ChipboxStringId,
        selectedFontName: String?,
        defaultFont: ChipboxFont,
        onSelected: (ChipboxFont) -> SettingsAction,
    ): ListModel {
        val fonts = ChipboxFont.entries
        val selectedFont = ChipboxFont.fromStorageValue(selectedFontName, defaultFont)
        return DropdownSettingListModel(
            settingId = labelId.name,
            name = stringProvider.getString(labelId),
            selectedPosition = fonts.indexOf(selectedFont),
            settingsLabels = fonts.map { it.fontName }.toImmutableList(),
            onNewOptionSelected = { index -> onSelected(fonts[index]) },
        )
    }

    private fun addFolderRow(stringProvider: StringProvider) = NameCaptionListModel(
        dataId = ChipboxStringId.SETTINGS_LABEL_ADD_FOLDER.hashCode().toLong(),
        name = stringProvider.getString(ChipboxStringId.SETTINGS_LABEL_ADD_FOLDER),
        caption = stringProvider.getString(ChipboxStringId.SETTINGS_CAPTION_ADD_FOLDER),
        clickAction = SettingsAction.AddFolderClicked,
    )

    // No inline loading state: tapping this starts a scan and opens the Rescan Status screen, which
    // is where progress is shown now.
    private fun rescanRow(stringProvider: StringProvider): ListModel = NameCaptionListModel(
        dataId = ChipboxStringId.SETTINGS_LABEL_RESCAN_LIBRARY.hashCode().toLong(),
        name = stringProvider.getString(ChipboxStringId.SETTINGS_LABEL_RESCAN_LIBRARY),
        caption = stringProvider.getString(ChipboxStringId.SETTINGS_CAPTION_RESCAN_LIBRARY),
        clickAction = SettingsAction.RescanLibraryClicked,
    )

    private fun clearLibraryRow(stringProvider: StringProvider): ListModel = when (clearLibraryStatus) {
        is LCE.Loading -> LoadingItemListModel(
            loadingType = LoadingType.TEXT_CAPTION,
            loadOperationName = clearLibraryStatus.operationName,
            loadPositionOffset = 0,
        )

        else -> NameCaptionListModel(
            dataId = ChipboxStringId.SETTINGS_LABEL_CLEAR_LIBRARY.hashCode().toLong(),
            name = stringProvider.getString(ChipboxStringId.SETTINGS_LABEL_CLEAR_LIBRARY),
            caption = stringProvider.getString(ChipboxStringId.SETTINGS_CAPTION_CLEAR_LIBRARY),
            clickAction = SettingsAction.ClearLibraryClicked,
        )
    }

    private fun appVersionRow(stringProvider: StringProvider) = LabelValueListModel(
        label = stringProvider.getString(ChipboxStringId.SETTINGS_LABEL_APP_VERSION),
        value = appInfo?.versionName,
        clickAction = SageAction.Noop,
    )

    private fun buildDateRow(stringProvider: StringProvider) = LabelValueListModel(
        label = stringProvider.getString(ChipboxStringId.SETTINGS_LABEL_BUILD_DATE),
        value = formattedBuildDate,
        clickAction = SettingsAction.BuildDateClicked,
    )

    private fun licensesRow(stringProvider: StringProvider) = SingleTextListModel(
        name = stringProvider.getString(ChipboxStringId.SETTINGS_LABEL_LICENSES),
        clickAction = SettingsAction.LicensesClicked,
    )

    private fun playbackStatusRow(stringProvider: StringProvider) = NameCaptionListModel(
        dataId = ChipboxStringId.SETTINGS_LABEL_PLAYBACK_STATUS.hashCode().toLong(),
        name = stringProvider.getString(ChipboxStringId.SETTINGS_LABEL_PLAYBACK_STATUS),
        caption = stringProvider.getString(ChipboxStringId.SETTINGS_CAPTION_PLAYBACK_STATUS),
        clickAction = SettingsAction.PlaybackStatusClicked,
    )

    private fun appBranchRow(stringProvider: StringProvider) = LabelValueListModel(
        label = stringProvider.getString(ChipboxStringId.SETTINGS_LABEL_APP_BRANCH),
        value = appInfo?.buildBranch,
        clickAction = SageAction.Noop,
    )

    private fun versionCodeRow(stringProvider: StringProvider) = LabelValueListModel(
        label = stringProvider.getString(ChipboxStringId.SETTINGS_LABEL_VERSION_CODE),
        value = appInfo?.versionCode?.toString(),
        clickAction = SageAction.Noop,
    )
}
