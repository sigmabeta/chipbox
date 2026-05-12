package net.sigmabeta.chipbox.strings

fun ChipboxStringId.id(): Int = when (this) {
    ChipboxStringId.ACCY_OCL_CHECKBOX      -> R.string.accy_ocl_checkbox
    ChipboxStringId.ACCY_OCL_DROPDOWN      -> R.string.accy_ocl_dropdown
    ChipboxStringId.ACCY_OCL_RATING        -> R.string.accy_ocl_rating
    ChipboxStringId.ACCY_OCL_SINGLE_LINE   -> R.string.accy_ocl_single_line
    ChipboxStringId.ACCY_OCL_VALUE         -> R.string.accy_ocl_value
    ChipboxStringId.ACCY_ST_DESC_CHECKED   -> R.string.accy_st_desc_checked
    ChipboxStringId.ACCY_ST_DESC_LOADING   -> R.string.accy_st_desc_loading
    ChipboxStringId.ACCY_ST_DESC_UNCHECKED -> R.string.accy_st_desc_unchecked
    ChipboxStringId.APPUI_TAB_LIBRARY      -> R.string.appui_tab_library
    ChipboxStringId.APPUI_TAB_SEARCH       -> R.string.appui_tab_search
    ChipboxStringId.APPUI_TAB_SETTINGS     -> R.string.appui_tab_settings
    ChipboxStringId.LIBRARY_BROWSE_BY_GAME    -> R.string.library_browse_by_game
    ChipboxStringId.LIBRARY_BROWSE_BY_ARTIST  -> R.string.library_browse_by_artist
    ChipboxStringId.LIBRARY_BROWSE_ALL_TRACKS -> R.string.library_browse_all_tracks
    ChipboxStringId.GAME_DETAIL_SECTION_SONGS   -> R.string.game_detail_section_songs
    ChipboxStringId.GAME_DETAIL_SECTION_ARTISTS -> R.string.game_detail_section_artists
    ChipboxStringId.GAME_DETAIL_CTA_PLAY_ALL    -> R.string.game_detail_cta_play_all
    ChipboxStringId.GAME_DETAIL_CTA_SHUFFLE_ALL -> R.string.game_detail_cta_shuffle_all
    ChipboxStringId.SETTINGS_SCREEN_TITLE        -> R.string.settings_screen_title
    ChipboxStringId.SETTINGS_SECTION_APPEARANCE  -> R.string.settings_section_appearance
    ChipboxStringId.SETTINGS_SECTION_LIBRARY     -> R.string.settings_section_library
    ChipboxStringId.SETTINGS_SECTION_ABOUT       -> R.string.settings_section_about
    ChipboxStringId.SETTINGS_SECTION_DEBUG       -> R.string.settings_section_debug
    ChipboxStringId.SETTINGS_LABEL_BRAND_FONT    -> R.string.settings_label_brand_font
    ChipboxStringId.SETTINGS_LABEL_PLAIN_FONT    -> R.string.settings_label_plain_font
    ChipboxStringId.SETTINGS_LABEL_ADD_FOLDER       -> R.string.settings_label_add_folder
    ChipboxStringId.SETTINGS_CAPTION_ADD_FOLDER     -> R.string.settings_caption_add_folder
    ChipboxStringId.SETTINGS_LABEL_RESCAN_LIBRARY   -> R.string.settings_label_rescan_library
    ChipboxStringId.SETTINGS_CAPTION_RESCAN_LIBRARY -> R.string.settings_caption_rescan_library
    ChipboxStringId.SETTINGS_LABEL_CLEAR_LIBRARY    -> R.string.settings_label_clear_library
    ChipboxStringId.SETTINGS_CAPTION_CLEAR_LIBRARY  -> R.string.settings_caption_clear_library
    ChipboxStringId.SETTINGS_LABEL_APP_VERSION   -> R.string.settings_label_app_version
    ChipboxStringId.SETTINGS_LABEL_BUILD_DATE    -> R.string.settings_label_build_date
    ChipboxStringId.SETTINGS_LABEL_LICENSES      -> R.string.settings_label_licenses
    ChipboxStringId.SETTINGS_LABEL_GITHUB        -> R.string.settings_label_github
    ChipboxStringId.SETTINGS_LABEL_APP_BRANCH    -> R.string.settings_label_app_branch
    ChipboxStringId.SETTINGS_LABEL_VERSION_CODE  -> R.string.settings_label_version_code
}
