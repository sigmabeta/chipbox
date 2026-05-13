package net.sigmabeta.chipbox.features.settings

import kotlinx.collections.immutable.toImmutableList
import net.sigmabeta.chipbox.strings.ChipboxStringId
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
    val rescanStatus: LCE<Unit> = LCE.Uninitialized,
    val clearLibraryStatus: LCE<Unit> = LCE.Uninitialized,
    val appInfo: AppInfo? = null,
    val formattedBuildDate: String? = null,
    val debugClickCount: Int = 0,
    val shouldShowDebug: Boolean? = null,
    val playbackStatusAvailable: Boolean = false,
) : ListState() {
    override fun title(stringProvider: StringProvider) = TitleBarModel(
        title = stringProvider.getString(ChipboxStringId.SETTINGS_SCREEN_TITLE),
        shouldShowBack = true,
    )

    override fun toListItems(stringProvider: StringProvider): List<ListModel> =
        appearanceSection(stringProvider) +
            librarySection(stringProvider) +
            aboutSection(stringProvider) +
            debugSection(stringProvider)

    private fun appearanceSection(stringProvider: StringProvider): List<ListModel> = listOf(
        sectionHeader(stringProvider, ChipboxStringId.SETTINGS_SECTION_APPEARANCE),
        fontDropdown(stringProvider, ChipboxStringId.SETTINGS_LABEL_BRAND_FONT, brandFont),
        fontDropdown(stringProvider, ChipboxStringId.SETTINGS_LABEL_PLAIN_FONT, plainFont),
    )

    private fun librarySection(stringProvider: StringProvider): List<ListModel> = listOf(
        sectionHeader(stringProvider, ChipboxStringId.SETTINGS_SECTION_LIBRARY),
        addFolderRow(stringProvider),
        rescanRow(stringProvider),
        clearLibraryRow(stringProvider),
    )

    private fun aboutSection(stringProvider: StringProvider): List<ListModel> = listOf(
        sectionHeader(stringProvider, ChipboxStringId.SETTINGS_SECTION_ABOUT),
        appVersionRow(stringProvider),
        buildDateRow(stringProvider),
        licensesRow(stringProvider),
    )

    private fun debugSection(stringProvider: StringProvider): List<ListModel> {
        if (shouldShowDebug != true) return emptyList()
        return listOfNotNull(
            sectionHeader(stringProvider, ChipboxStringId.SETTINGS_SECTION_DEBUG),
            appBranchRow(stringProvider),
            versionCodeRow(stringProvider),
            if (playbackStatusAvailable) playbackStatusRow(stringProvider) else null,
        )
    }

    private fun sectionHeader(stringProvider: StringProvider, id: ChipboxStringId) =
        SectionHeaderListModel(title = stringProvider.getString(id))

    // No-op selection callback: dropdown renders the current persisted font but
    // doesn't yet write changes back. Wiring this up requires either routing the
    // dropdown's `(Int) -> Unit` lambda through the action sink or extending the
    // sage list-model surface — deferred until the typography layer actually
    // consumes the picked value.
    private fun fontDropdown(
        stringProvider: StringProvider,
        labelId: ChipboxStringId,
        selectedFontName: String?,
    ): ListModel {
        val fonts = ChipboxFont.entries
        val selectedIndex = fonts
            .indexOfFirst { it.name == selectedFontName }
            .coerceAtLeast(0)
        return DropdownSettingListModel(
            settingId = labelId.name,
            name = stringProvider.getString(labelId),
            selectedPosition = selectedIndex,
            settingsLabels = fonts.map { it.fontName }.toImmutableList(),
            onNewOptionSelected = { },
        )
    }

    private fun addFolderRow(stringProvider: StringProvider) = NameCaptionListModel(
        dataId = ChipboxStringId.SETTINGS_LABEL_ADD_FOLDER.hashCode().toLong(),
        name = stringProvider.getString(ChipboxStringId.SETTINGS_LABEL_ADD_FOLDER),
        caption = stringProvider.getString(ChipboxStringId.SETTINGS_CAPTION_ADD_FOLDER),
        clickAction = SettingsAction.AddFolderClicked,
    )

    private fun rescanRow(stringProvider: StringProvider): ListModel = when (rescanStatus) {
        is LCE.Loading -> LoadingItemListModel(
            loadingType = LoadingType.TEXT_CAPTION,
            loadOperationName = rescanStatus.operationName,
            loadPositionOffset = 0,
        )
        else -> NameCaptionListModel(
            dataId = ChipboxStringId.SETTINGS_LABEL_RESCAN_LIBRARY.hashCode().toLong(),
            name = stringProvider.getString(ChipboxStringId.SETTINGS_LABEL_RESCAN_LIBRARY),
            caption = stringProvider.getString(ChipboxStringId.SETTINGS_CAPTION_RESCAN_LIBRARY),
            clickAction = SettingsAction.RescanLibraryClicked,
        )
    }

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

    private fun playbackStatusRow(stringProvider: StringProvider) = NameCaptionListModel(
        dataId = ChipboxStringId.SETTINGS_LABEL_PLAYBACK_STATUS.hashCode().toLong(),
        name = stringProvider.getString(ChipboxStringId.SETTINGS_LABEL_PLAYBACK_STATUS),
        caption = stringProvider.getString(ChipboxStringId.SETTINGS_CAPTION_PLAYBACK_STATUS),
        clickAction = SettingsAction.PlaybackStatusClicked,
    )
}
