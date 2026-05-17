package net.sigmabeta.chipbox.strings

fun ChipboxStringId.id(): Int = when (this) {
    ChipboxStringId.ACCY_OCL_CHECKBOX -> R.string.accy_ocl_checkbox

    ChipboxStringId.ACCY_OCL_DROPDOWN -> R.string.accy_ocl_dropdown

    ChipboxStringId.ACCY_OCL_RATING -> R.string.accy_ocl_rating

    ChipboxStringId.ACCY_OCL_SINGLE_LINE -> R.string.accy_ocl_single_line

    ChipboxStringId.ACCY_OCL_VALUE -> R.string.accy_ocl_value

    ChipboxStringId.ACCY_ST_DESC_CHECKED -> R.string.accy_st_desc_checked

    ChipboxStringId.ACCY_ST_DESC_LOADING -> R.string.accy_st_desc_loading

    ChipboxStringId.ACCY_ST_DESC_UNCHECKED -> R.string.accy_st_desc_unchecked

    ChipboxStringId.ACCY_CDESC_TOPBAR_BACK -> R.string.accy_cdesc_topbar_back

    ChipboxStringId.ACCY_CDESC_SEARCH_CLEAR -> R.string.accy_cdesc_search_clear

    ChipboxStringId.APPUI_TAB_LIBRARY -> R.string.appui_tab_library

    ChipboxStringId.APPUI_TAB_SEARCH -> R.string.appui_tab_search

    ChipboxStringId.APPUI_TAB_SETTINGS -> R.string.appui_tab_settings

    ChipboxStringId.SEARCH_SCREEN_TITLE -> R.string.search_screen_title

    ChipboxStringId.SEARCH_HINT -> R.string.search_hint

    ChipboxStringId.SEARCH_EMPTY_PROMPT -> R.string.search_empty_prompt

    ChipboxStringId.SEARCH_NO_RESULTS -> R.string.search_no_results

    ChipboxStringId.SEARCH_SECTION_GAMES -> R.string.search_section_games

    ChipboxStringId.SEARCH_SECTION_SONGS -> R.string.search_section_songs

    ChipboxStringId.SEARCH_SECTION_ARTISTS -> R.string.search_section_artists

    ChipboxStringId.LIBRARY_BROWSE_BY_GAME -> R.string.library_browse_by_game

    ChipboxStringId.LIBRARY_BROWSE_BY_GAME_EMPTY -> R.string.library_browse_by_game_empty

    ChipboxStringId.LIBRARY_BROWSE_BY_ARTIST -> R.string.library_browse_by_artist

    ChipboxStringId.LIBRARY_BROWSE_BY_ARTIST_EMPTY -> R.string.library_browse_by_artist_empty

    ChipboxStringId.LIBRARY_BROWSE_BY_PLATFORM -> R.string.library_browse_by_platform

    ChipboxStringId.LIBRARY_BROWSE_BY_PLATFORM_EMPTY -> R.string.library_browse_by_platform_empty

    ChipboxStringId.LIBRARY_BROWSE_ALL_TRACKS -> R.string.library_browse_all_tracks

    ChipboxStringId.LIBRARY_BROWSE_ALL_TRACKS_EMPTY -> R.string.library_browse_all_tracks_empty

    ChipboxStringId.LIBRARY_BROWSE_ALL_TRACKS_CTA_SHUFFLE_ALL -> R.string.library_browse_all_tracks_cta_shuffle_all

    ChipboxStringId.GAMES_FOR_PLATFORM_TITLE -> R.string.games_for_platform_title

    ChipboxStringId.GAMES_FOR_PLATFORM_EMPTY -> R.string.games_for_platform_empty

    ChipboxStringId.GAMES_FOR_PLATFORM_CTA_PLAY_ALL -> R.string.games_for_platform_cta_play_all

    ChipboxStringId.GAMES_FOR_PLATFORM_CTA_SHUFFLE_ALL -> R.string.games_for_platform_cta_shuffle_all

    ChipboxStringId.GAME_DETAIL_SECTION_SONGS -> R.string.game_detail_section_songs

    ChipboxStringId.GAME_DETAIL_SECTION_ARTISTS -> R.string.game_detail_section_artists

    ChipboxStringId.GAME_DETAIL_CTA_PLAY_ALL -> R.string.game_detail_cta_play_all

    ChipboxStringId.GAME_DETAIL_CTA_SHUFFLE_ALL -> R.string.game_detail_cta_shuffle_all

    ChipboxStringId.GAME_DETAIL_EMPTY -> R.string.game_detail_empty

    ChipboxStringId.ARTIST_DETAIL_SECTION_SONGS -> R.string.artist_detail_section_songs

    ChipboxStringId.ARTIST_DETAIL_SECTION_GAMES -> R.string.artist_detail_section_games

    ChipboxStringId.ARTIST_DETAIL_CTA_PLAY_ALL -> R.string.artist_detail_cta_play_all

    ChipboxStringId.ARTIST_DETAIL_CTA_SHUFFLE_ALL -> R.string.artist_detail_cta_shuffle_all

    ChipboxStringId.ARTIST_DETAIL_EMPTY -> R.string.artist_detail_empty

    ChipboxStringId.SETTINGS_SCREEN_TITLE -> R.string.settings_screen_title

    ChipboxStringId.SETTINGS_SECTION_APPEARANCE -> R.string.settings_section_appearance

    ChipboxStringId.SETTINGS_SECTION_LIBRARY -> R.string.settings_section_library

    ChipboxStringId.SETTINGS_SECTION_ABOUT -> R.string.settings_section_about

    ChipboxStringId.SETTINGS_SECTION_DEBUG -> R.string.settings_section_debug

    ChipboxStringId.SETTINGS_LABEL_BRAND_FONT -> R.string.settings_label_brand_font

    ChipboxStringId.SETTINGS_LABEL_PLAIN_FONT -> R.string.settings_label_plain_font

    ChipboxStringId.SETTINGS_LABEL_ADD_FOLDER -> R.string.settings_label_add_folder

    ChipboxStringId.SETTINGS_CAPTION_ADD_FOLDER -> R.string.settings_caption_add_folder

    ChipboxStringId.SETTINGS_LABEL_RESCAN_LIBRARY -> R.string.settings_label_rescan_library

    ChipboxStringId.SETTINGS_CAPTION_RESCAN_LIBRARY -> R.string.settings_caption_rescan_library

    ChipboxStringId.SETTINGS_LABEL_CLEAR_LIBRARY -> R.string.settings_label_clear_library

    ChipboxStringId.SETTINGS_CAPTION_CLEAR_LIBRARY -> R.string.settings_caption_clear_library

    ChipboxStringId.SETTINGS_LABEL_APP_VERSION -> R.string.settings_label_app_version

    ChipboxStringId.SETTINGS_LABEL_BUILD_DATE -> R.string.settings_label_build_date

    ChipboxStringId.SETTINGS_LABEL_LICENSES -> R.string.settings_label_licenses

    ChipboxStringId.SETTINGS_LABEL_APP_BRANCH -> R.string.settings_label_app_branch

    ChipboxStringId.SETTINGS_LABEL_VERSION_CODE -> R.string.settings_label_version_code

    ChipboxStringId.SETTINGS_LABEL_PLAYBACK_STATUS -> R.string.settings_label_playback_status

    ChipboxStringId.SETTINGS_CAPTION_PLAYBACK_STATUS -> R.string.settings_caption_playback_status

    ChipboxStringId.PLAYBACK_STATUS_SCREEN_TITLE -> R.string.playback_status_screen_title

    ChipboxStringId.PLAYBACK_STATUS_SECTION_TRACK -> R.string.playback_status_section_track

    ChipboxStringId.PLAYBACK_STATUS_SECTION_PLAYBACK -> R.string.playback_status_section_playback

    ChipboxStringId.PLAYBACK_STATUS_SECTION_SESSION -> R.string.playback_status_section_session

    ChipboxStringId.PLAYBACK_STATUS_LABEL_TITLE -> R.string.playback_status_label_title

    ChipboxStringId.PLAYBACK_STATUS_LABEL_ARTISTS -> R.string.playback_status_label_artists

    ChipboxStringId.PLAYBACK_STATUS_LABEL_GAME -> R.string.playback_status_label_game

    ChipboxStringId.PLAYBACK_STATUS_LABEL_TRACK_NUMBER -> R.string.playback_status_label_track_number

    ChipboxStringId.PLAYBACK_STATUS_LABEL_SOURCE -> R.string.playback_status_label_source

    ChipboxStringId.PLAYBACK_STATUS_LABEL_FADE -> R.string.playback_status_label_fade

    ChipboxStringId.PLAYBACK_STATUS_LABEL_PATH -> R.string.playback_status_label_path

    ChipboxStringId.PLAYBACK_STATUS_LABEL_STATE -> R.string.playback_status_label_state

    ChipboxStringId.PLAYBACK_STATUS_LABEL_POSITION_MS -> R.string.playback_status_label_position_ms

    ChipboxStringId.PLAYBACK_STATUS_LABEL_LENGTH_MS -> R.string.playback_status_label_length_ms

    ChipboxStringId.PLAYBACK_STATUS_LABEL_BUFFER_AHEAD_MS -> R.string.playback_status_label_buffer_ahead_ms

    ChipboxStringId.PLAYBACK_STATUS_LABEL_PLAYBACK_SPEED -> R.string.playback_status_label_playback_speed

    ChipboxStringId.PLAYBACK_STATUS_LABEL_SKIP_FORWARD -> R.string.playback_status_label_skip_forward

    ChipboxStringId.PLAYBACK_STATUS_LABEL_ERROR_MESSAGE -> R.string.playback_status_label_error_message

    ChipboxStringId.PLAYBACK_STATUS_LABEL_SESSION_ID -> R.string.playback_status_label_session_id

    ChipboxStringId.PLAYBACK_STATUS_LABEL_SESSION_TYPE -> R.string.playback_status_label_session_type

    ChipboxStringId.PLAYBACK_STATUS_LABEL_CONTENT_ID -> R.string.playback_status_label_content_id

    ChipboxStringId.PLAYBACK_STATUS_LABEL_CURRENT_POSITION -> R.string.playback_status_label_current_position

    ChipboxStringId.PLAYBACK_STATUS_LABEL_SHUFFLED -> R.string.playback_status_label_shuffled

    ChipboxStringId.PLAYBACK_STATUS_SECTION_GENERATOR -> R.string.playback_status_section_generator

    ChipboxStringId.PLAYBACK_STATUS_SECTION_SPEAKER -> R.string.playback_status_section_speaker

    ChipboxStringId.PLAYBACK_STATUS_SECTION_BUFFER -> R.string.playback_status_section_buffer

    ChipboxStringId.PLAYBACK_STATUS_LABEL_GEN_TRACK_ID -> R.string.playback_status_label_gen_track_id

    ChipboxStringId.PLAYBACK_STATUS_LABEL_GEN_TRACK_TITLE -> R.string.playback_status_label_gen_track_title

    ChipboxStringId.PLAYBACK_STATUS_LABEL_GEN_SAMPLE_RATE -> R.string.playback_status_label_gen_sample_rate

    ChipboxStringId.PLAYBACK_STATUS_LABEL_GEN_PRODUCED_MS -> R.string.playback_status_label_gen_produced_ms

    ChipboxStringId.PLAYBACK_STATUS_LABEL_GEN_FRAMES_PLAYED -> R.string.playback_status_label_gen_frames_played

    ChipboxStringId.PLAYBACK_STATUS_LABEL_GEN_LOOPING -> R.string.playback_status_label_gen_looping

    ChipboxStringId.PLAYBACK_STATUS_LABEL_GEN_LAST_EVENT -> R.string.playback_status_label_gen_last_event

    ChipboxStringId.PLAYBACK_STATUS_LABEL_GEN_LAST_ERROR -> R.string.playback_status_label_gen_last_error

    ChipboxStringId.PLAYBACK_STATUS_LABEL_GEN_SOURCE_DIAG -> R.string.playback_status_label_gen_source_diag

    ChipboxStringId.PLAYBACK_STATUS_LABEL_SPK_TRACK_ID -> R.string.playback_status_label_spk_track_id

    ChipboxStringId.PLAYBACK_STATUS_LABEL_SPK_POSITION_MS -> R.string.playback_status_label_spk_position_ms

    ChipboxStringId.PLAYBACK_STATUS_LABEL_SPK_CONSUME_LOOP -> R.string.playback_status_label_spk_consume_loop

    ChipboxStringId.PLAYBACK_STATUS_LABEL_SPK_LAST_EVENT -> R.string.playback_status_label_spk_last_event

    ChipboxStringId.PLAYBACK_STATUS_LABEL_SPK_UNDERRUNS -> R.string.playback_status_label_spk_underruns

    ChipboxStringId.PLAYBACK_STATUS_LABEL_SPK_LAST_ERROR -> R.string.playback_status_label_spk_last_error

    ChipboxStringId.PLAYBACK_STATUS_LABEL_BUF_SAMPLE_RATE -> R.string.playback_status_label_buf_sample_rate

    ChipboxStringId.PLAYBACK_STATUS_LABEL_BUF_CAPACITY -> R.string.playback_status_label_buf_capacity

    ChipboxStringId.PLAYBACK_STATUS_LABEL_BUF_FULL_QUEUED -> R.string.playback_status_label_buf_full_queued

    ChipboxStringId.PLAYBACK_STATUS_LABEL_BUF_EMPTY_AVAIL -> R.string.playback_status_label_buf_empty_avail

    ChipboxStringId.PLAYBACK_STATUS_LABEL_BUF_DRAIN_COUNT -> R.string.playback_status_label_buf_drain_count

    ChipboxStringId.PLAYBACK_STATUS_CTA_COPY_DEBUG_INFO -> R.string.playback_status_cta_copy_debug_info

    ChipboxStringId.NOW_PLAYING_SCREEN_TITLE -> R.string.now_playing_screen_title

    ChipboxStringId.NOW_PLAYING_SESSION_TYPE_GAME_PLAYING -> R.string.now_playing_session_type_game_playing

    ChipboxStringId.NOW_PLAYING_SESSION_TYPE_GAME_SHUFFLING -> R.string.now_playing_session_type_game_shuffling

    ChipboxStringId.NOW_PLAYING_SESSION_TYPE_ARTIST_PLAYING -> R.string.now_playing_session_type_artist_playing

    ChipboxStringId.NOW_PLAYING_SESSION_TYPE_ARTIST_SHUFFLING -> R.string.now_playing_session_type_artist_shuffling

    ChipboxStringId.NOW_PLAYING_SESSION_TYPE_PLAYLIST_PLAYING -> R.string.now_playing_session_type_playlist_playing

    ChipboxStringId.NOW_PLAYING_SESSION_TYPE_PLAYLIST_SHUFFLING -> R.string.now_playing_session_type_playlist_shuffling

    ChipboxStringId.NOW_PLAYING_SESSION_TYPE_PLATFORM_PLAYING -> R.string.now_playing_session_type_platform_playing

    ChipboxStringId.NOW_PLAYING_SESSION_TYPE_PLATFORM_SHUFFLING -> R.string.now_playing_session_type_platform_shuffling

    ChipboxStringId.NOW_PLAYING_SESSION_TYPE_ALL_TRACKS_PLAYING -> R.string.now_playing_session_type_all_tracks_playing

    ChipboxStringId.NOW_PLAYING_SESSION_TYPE_ALL_TRACKS_SHUFFLING ->
        R.string.now_playing_session_type_all_tracks_shuffling

    ChipboxStringId.PLATFORM_ARCADE -> R.string.platform_arcade

    ChipboxStringId.PLATFORM_DREAMCAST -> R.string.platform_dreamcast

    ChipboxStringId.PLATFORM_GAMEBOY -> R.string.platform_gameboy

    ChipboxStringId.PLATFORM_GAMEBOY_ADVANCE -> R.string.platform_gameboy_advance

    ChipboxStringId.PLATFORM_GENESIS -> R.string.platform_genesis

    ChipboxStringId.PLATFORM_NES -> R.string.platform_nes

    ChipboxStringId.PLATFORM_N64 -> R.string.platform_n64

    ChipboxStringId.PLATFORM_NDS -> R.string.platform_nds

    ChipboxStringId.PLATFORM_PC -> R.string.platform_pc

    ChipboxStringId.PLATFORM_PS2 -> R.string.platform_ps2

    ChipboxStringId.PLATFORM_PSX -> R.string.platform_psx

    ChipboxStringId.PLATFORM_SATURN -> R.string.platform_saturn

    ChipboxStringId.PLATFORM_SNES -> R.string.platform_snes

    ChipboxStringId.PLATFORM_OTHER -> R.string.platform_other
}
