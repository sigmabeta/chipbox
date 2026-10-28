package net.sigmabeta.chipbox.webroutes

import net.sigmabeta.chipbox.capabilities.AppCapabilities
import net.sigmabeta.chipbox.features.artistdetail.ArtistDetail
import net.sigmabeta.chipbox.features.browsealltracks.BrowseAllTracks
import net.sigmabeta.chipbox.features.browsebyartist.BrowseByArtist
import net.sigmabeta.chipbox.features.browsebygame.BrowseByGame
import net.sigmabeta.chipbox.features.browsebyplatform.BrowseByPlatform
import net.sigmabeta.chipbox.features.errorlog.ErrorLog
import net.sigmabeta.chipbox.features.favorites.Favorites
import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import net.sigmabeta.chipbox.features.gamesforplatform.GamesForPlatform
import net.sigmabeta.chipbox.features.home.Home
import net.sigmabeta.chipbox.features.library.Library
import net.sigmabeta.chipbox.features.nowplaying.NowPlaying
import net.sigmabeta.chipbox.features.playlistdetail.PlaylistDetail
import net.sigmabeta.chipbox.features.playlists.Playlists
import net.sigmabeta.chipbox.features.search.Search
import net.sigmabeta.chipbox.features.settings.Settings
import net.sigmabeta.chipbox.features.tracksforartist.TracksForArtist
import net.sigmabeta.chipbox.models.Platform

/** The three top-level tabs the shell exposes. */
enum class ChipboxTab { HOME, LIBRARY, SEARCH }

/**
 * A navigable location in the shell.
 *
 * [Tab] is a top-level tab root; [Deep] is a feature route key pushed onto the active tab's stack.
 * The distinction matters: a tab root selects a tab, a deep target is pushed/popped within one.
 */
sealed interface ChipboxTarget {
    data class Tab(val tab: ChipboxTab) : ChipboxTarget
    data class Deep(val destination: Any) : ChipboxTarget
}

/**
 * Path-based URL mirror of the shell's routes. Pure and browser-free: the web host supplies the
 * pathname and applies the returned target, and the Node tests cover the mapping.
 *
 * Rules:
 * - A tab root is a [ChipboxTarget.Tab]; a feature route is a [ChipboxTarget.Deep].
 * - Only routes the web shell exposes are mapped. Admin destinations (Manage Library, Folder
 *   Picker, Rescan Status) and debug destinations (Playback Status, Crash Log, Component Library)
 *   have no URL: decoding them is "unknown", and encoding them is refused.
 * - Picker-mode [Playlists] carries transient bulk-add arguments, so it is never encoded; the
 *   normal (empty) list maps to `/library/playlists`.
 * - Unknown/malformed paths have no target; the host falls back to [ChipboxTab.HOME].
 * - `?api=` (the dev base-URL override) and any other unrecognised query parameter is ignored.
 */
object RouteUrl {

    /**
     * The canonical URL for [target], or null when the target has no web URL (admin/debug-only
     * screens, picker-mode Playlists, unknown destinations). Callers should skip the history entry
     * rather than inventing one.
     */
    fun encode(target: ChipboxTarget): String? = when (target) {
        is ChipboxTarget.Tab -> when (target.tab) {
            ChipboxTab.HOME -> "/"
            ChipboxTab.LIBRARY -> "/library"
            ChipboxTab.SEARCH -> "/search"
        }

        is ChipboxTarget.Deep -> encodeDestination(target.destination)
    }

    /**
     * The target for [path] (a pathname, optionally with a query string; absolute URLs are also
     * accepted), or null when the path is unknown or the host's [capabilities] hide it. Callers
     * treat null as "go to the root".
     */
    fun decode(path: String, capabilities: AppCapabilities = AppCapabilities.Default): ChipboxTarget? {
        val parsed = parse(path) ?: return null
        val segments = parsed.segments
        // Tab roots are tabs, not pushed screens.
        when (segments) {
            emptyList<String>() -> return ChipboxTarget.Tab(ChipboxTab.HOME)
            listOf("library") -> return ChipboxTarget.Tab(ChipboxTab.LIBRARY)
            listOf("search") -> return ChipboxTarget.Tab(ChipboxTab.SEARCH)
        }
        val destination = destinationFor(parsed) ?: return null
        if (!capabilities.allowsNavigation(destination)) return null
        return ChipboxTarget.Deep(destination)
    }

    /**
     * True when [path] has no route (or the host withholds it), so a host that fell back to the
     * root must canonicalise the current history entry rather than leave the dead path in the URL
     * bar. A well-formed path decodes to a target and needs no rewrite.
     */
    fun needsCanonicalization(path: String, capabilities: AppCapabilities = AppCapabilities.Default): Boolean =
        decode(path, capabilities) == null

    // region encode ---------------------------------------------------------------------------

    private fun encodeDestination(destination: Any): String? = when (destination) {
        Home -> "/"

        Library -> "/library"

        Search -> "/search"

        Settings -> "/settings"

        ErrorLog -> "/settings/errors"

        NowPlaying -> "/now-playing"

        Favorites -> "/library/favorites"

        // Only the normal browse list is linkable. A picker invocation carries transient
        // pendingTrackIds/suggestedName (and its Screen key is hashed on them), so it must not
        // become a URL that a reload silently reopens in picker mode.
        is Playlists -> if (destination.pendingTrackIds.isEmpty() && destination.suggestedName == null) {
            "/library/playlists"
        } else {
            null
        }

        is PlaylistDetail -> idPath("/library/playlists", destination.id)

        BrowseByGame -> "/library/games"

        BrowseByArtist -> "/library/artists"

        BrowseByPlatform -> "/library/platforms"

        BrowseAllTracks -> "/library/tracks"

        is GameDetail -> idPath("/game", destination.id)

        is ArtistDetail -> idPath("/artist", destination.id)

        is TracksForArtist -> idPath("/artist", destination.artistId)?.let { base ->
            "$base/tracks?name=${percentEncode(destination.artistName)}"
        }

        is GamesForPlatform -> "/platform/${destination.platform.name}/games"

        // ManageLibrary / FolderPicker / RescanStatus / PlaybackStatus / CrashLog /
        // ComponentLibrary(+Mode) and anything unknown have no web URL.
        else -> null
    }

    private fun idPath(prefix: String, id: Long): String? = if (id > 0L) "$prefix/$id" else null

    // endregion

    // region decode ---------------------------------------------------------------------------

    private fun destinationFor(parsed: ParsedPath): Any? {
        val segments = parsed.segments
        return when {
            segments == listOf("settings") -> Settings

            segments == listOf("settings", "errors") -> ErrorLog

            segments == listOf("now-playing") -> NowPlaying

            segments == listOf("library", "favorites") -> Favorites

            segments == listOf("library", "playlists") -> Playlists()

            segments == listOf("library", "games") -> BrowseByGame

            segments == listOf("library", "artists") -> BrowseByArtist

            segments == listOf("library", "platforms") -> BrowseByPlatform

            segments == listOf("library", "tracks") -> BrowseAllTracks

            segments.size == 3 && segments[0] == "library" && segments[1] == "playlists" ->
                positiveId(segments[2])?.let(::PlaylistDetail)

            segments.size == 2 && segments[0] == "game" -> positiveId(segments[1])?.let(::GameDetail)

            segments.size == 2 && segments[0] == "artist" -> positiveId(segments[1])?.let(::ArtistDetail)

            segments.size == 3 && segments[0] == "artist" && segments[2] == "tracks" ->
                positiveId(segments[1])?.let { artistId ->
                    // The name is a display title only; the screen refetches tracks by id. Tolerate
                    // a link with the query stripped rather than refusing the whole screen.
                    TracksForArtist(artistId, parsed.query["name"].orEmpty())
                }

            segments.size == 3 && segments[0] == "platform" && segments[2] == "games" ->
                platformNamed(segments[1])?.let(::GamesForPlatform)

            else -> null
        }
    }

    private fun positiveId(segment: String): Long? = segment.toLongOrNull()?.takeIf { it > 0L }

    private fun platformNamed(segment: String): Platform? =
        Platform.entries.firstOrNull { it.name.equals(segment, ignoreCase = true) }

    // endregion

    // region parsing --------------------------------------------------------------------------

    private data class ParsedPath(
        val segments: List<String>,
        val query: Map<String, String>,
    )

    private fun parse(rawPath: String): ParsedPath? {
        var path = rawPath.trim()
        if (path.startsWith("http://") || path.startsWith("https://")) {
            path = "/" + path.substringAfter("://").substringAfter('/', "")
        }
        path = path.substringBefore('#')
        val queryStart = path.indexOf('?')
        val pathOnly = if (queryStart >= 0) path.substring(0, queryStart) else path
        val queryOnly = if (queryStart >= 0) path.substring(queryStart + 1) else ""
        if (!pathOnly.startsWith("/") || pathOnly.startsWith("//")) return null

        val trimmed = pathOnly.trimEnd('/')
        val segments = if (trimmed.isEmpty()) {
            emptyList()
        } else {
            trimmed.removePrefix("/").split('/').map { segment ->
                percentDecode(segment, plusAsSpace = false) ?: return null
            }
        }
        if (segments.any { it.isEmpty() }) return null
        return ParsedPath(segments, parseQuery(queryOnly))
    }

    private fun parseQuery(rawQuery: String): Map<String, String> {
        if (rawQuery.isEmpty()) return emptyMap()
        return rawQuery.split('&')
            .mapNotNull { pair ->
                val key = percentDecode(pair.substringBefore('='), plusAsSpace = true) ?: return@mapNotNull null
                val value = percentDecode(pair.substringAfter('=', ""), plusAsSpace = true) ?: return@mapNotNull null
                if (key.isEmpty()) null else key to value
            }
            .toMap()
    }

    // endregion
}

private const val HEX_DIGITS = "0123456789ABCDEF"

private fun percentDecode(value: String, plusAsSpace: Boolean): String? {
    if ('%' !in value && (!plusAsSpace || '+' !in value)) return value
    val bytes = ArrayList<Byte>(value.length)
    var index = 0
    while (index < value.length) {
        val char = value[index]
        when {
            char == '%' -> {
                if (index + 2 >= value.length) return null
                val high = hexDigit(value[index + 1])
                val low = hexDigit(value[index + 2])
                if (high < 0 || low < 0) return null
                bytes += ((high shl 4) or low).toByte()
                index += 3
            }

            char == '+' && plusAsSpace -> {
                bytes += ' '.code.toByte()
                index += 1
            }

            else -> {
                value.substring(index, index + 1).encodeToByteArray().forEach { bytes += it }
                index += 1
            }
        }
    }
    return bytes.toByteArray().decodeToString()
}

private fun hexDigit(char: Char): Int = when (char) {
    in '0'..'9' -> char - '0'
    in 'a'..'f' -> char - 'a' + 10
    in 'A'..'F' -> char - 'A' + 10
    else -> -1
}

internal fun percentEncode(value: String): String = buildString {
    value.encodeToByteArray().forEach { byte ->
        val code = byte.toInt() and 0xFF
        val char = code.toChar()
        if (char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' ||
            char == '-' || char == '.' || char == '_' || char == '~'
        ) {
            append(char)
        } else {
            append('%')
            append(HEX_DIGITS[code shr 4])
            append(HEX_DIGITS[code and 0x0F])
        }
    }
}
