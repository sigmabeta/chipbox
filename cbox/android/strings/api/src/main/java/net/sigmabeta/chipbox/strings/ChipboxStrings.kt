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
}
