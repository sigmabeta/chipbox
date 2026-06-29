package net.sigmabeta.chipbox.uitest

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.sigmabeta.chipbox.common.appui.api.ChipboxAppUi
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.director.PlayerState
import net.sigmabeta.chipbox.player.director.SessionRequest
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.RawGame
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.chipbox.scanner.state.ScannerEvent
import net.sigmabeta.chipbox.scanner.state.ScannerState
import net.sigmabeta.chipbox.strings.api.LocalChipboxStringProvider
import net.sigmabeta.chipbox.uitest.harness.createTestAppGraph
import net.sigmabeta.chipbox.uitest.harness.platformTestArgument
import net.sigmabeta.chipbox.uitest.harness.writeFailureArtifacts
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.perf.LocalLogger
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Entry point for the Chipbox UI test DSL. Hosts the real `ChipboxAppUi` shell over the fake
 * [net.sigmabeta.chipbox.uitest.harness.TestAppGraph] (pre-populated with a deterministic random
 * library) and exposes the verbs a test scripts against:
 *
 * ```
 * runChipboxUiTest {
 *     startAtScreen(GameDetail(gameId("Iron Quest")))
 *     assertTitle("Iron Quest")
 * }
 * ```
 *
 * The specs live in `src/jvmTest` (so Android Studio shows + runs them) and are mirrored onto
 * `androidDeviceTest`, so the same scripts run on the desktop JVM and on-device. See
 * arch-docs/architecture/ui-test-dsl.md.
 *
 * On any failure inside [block], the harness dumps a screenshot + semantics tree of the live scene
 * (paths logged) before rethrowing — see [writeFailureArtifacts].
 *
 * Pass `-Pchipbox.uitest.actionDelayMs=1500` to insert a real 1.5s pause before each click verb and
 * before the test ends, so the actions are observable on-device (`connectedAndroidDeviceTest`).
 * Omitted or zero → no delay.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooGenericExceptionCaught") // any failure (AssertionError included) should dump artifacts
fun runChipboxUiTest(block: ChipboxUiTest.() -> Unit) = runComposeUiTest {
    val test = ChipboxUiTest(this)
    test.launchShell()
    try {
        test.block()
        test.pauseForObservation() // let the final action's effect linger before the scene tears down
    } catch (failure: Throwable) {
        writeFailureArtifacts(failure, test.hatchet)
        throw failure
    }
}

@OptIn(ExperimentalTestApi::class)
class ChipboxUiTest internal constructor(private val compose: ComposeUiTest) {
    private val graph = createTestAppGraph()
    private val destinations = MutableSharedFlow<Any>(extraBufferCapacity = 1)
    private val navigations = mutableListOf<Any>()

    /** The harness logger (also the one screens log through), reused to announce failure artifacts. */
    internal val hatchet: Hatchet get() = graph.hatchet

    // Real wall-clock pause applied before each click verb and before the container ends, so the
    // effect of each action is observable when watching a device run. Zero (the default) is a no-op;
    // set via -Pchipbox.uitest.actionDelayMs (routed in as a system property on jvmTest and an
    // instrumentation arg on androidDeviceTest).
    private val actionDelay: Duration =
        (platformTestArgument(ACTION_DELAY_KEY)?.toLongOrNull() ?: 0L).coerceAtLeast(0L).milliseconds

    /** Block for [actionDelay] (no-op when unset/zero). The device UI thread keeps rendering during
     *  the sleep, so the previous action's settled result stays on screen to be observed. */
    internal fun pauseForObservation() {
        if (actionDelay > Duration.ZERO) Thread.sleep(actionDelay.inWholeMilliseconds)
    }

    /** Host the real shell once, on the Home tab, over the test graph. */
    internal fun launchShell() {
        // metroViewModel<>() needs a ViewModelStoreOwner; the app gets it from the Compose Window /
        // Voyager per-screen store — neither exists here, so supply a plain one.
        val storeOwner = object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }
        // Real windows (Android Activity / desktop Window) provide a LocalLifecycleOwner; the bare
        // runComposeUiTest scene doesn't, and the shell's entries run a LifecycleResumeEffect — so
        // supply a resumed owner here. createUnsafe bypasses the registry's main-thread check.
        val lifecycleOwner = object : LifecycleOwner {
            override val lifecycle: Lifecycle = LifecycleRegistry.createUnsafe(this).apply {
                currentState = Lifecycle.State.RESUMED
            }
        }
        compose.setContent {
            CompositionLocalProvider(
                LocalMetroViewModelFactory provides graph.metroViewModelFactory,
                LocalViewModelStoreOwner provides storeOwner,
                LocalLifecycleOwner provides lifecycleOwner,
                LocalChipboxStringProvider provides graph.stringProvider,
                LocalLogger provides graph.hatchet,
            ) {
                ChipboxAppUi(
                    onOpenUrl = {},
                    onCopyToClipboard = { _, _ -> },
                    activeTabDestinations = destinations,
                    onNavigate = { navigations += it },
                )
            }
        }
        compose.waitForIdle()
    }

    /** The id of the artist named [name] in the library (e.g. to assert a navigation target). */
    fun artistId(name: String): Long = runBlocking {
        withTimeout(LOAD_TIMEOUT_MS) {
            val data = graph.memoryRepository
                .getAllArtists(withTracks = false, withGames = false)
                .first { it is Data.Succeeded }
            @Suppress("UNCHECKED_CAST")
            (data as Data.Succeeded<List<Artist>>).data.first { it.name == name }.id
        }
    }

    /** The id of the game titled [title] in the pre-populated library — to drive to a specific known
     *  game without seeding one. */
    fun gameId(title: String): Long = runBlocking {
        withTimeout(LOAD_TIMEOUT_MS) {
            val data = graph.memoryRepository
                .getAllGames(withTracks = false, withArtists = false)
                .first { it is Data.Succeeded }
            @Suppress("UNCHECKED_CAST")
            (data as Data.Succeeded<List<Game>>).data.first { it.title == title }.id
        }
    }

    /** The id of the track titled [title] in the pre-populated library (e.g. to favorite it). */
    fun trackId(title: String): Long = runBlocking {
        withTimeout(LOAD_TIMEOUT_MS) {
            val data = graph.memoryRepository
                .getAllTracks(withGame = false, withArtists = false)
                .first { it is Data.Succeeded }
            @Suppress("UNCHECKED_CAST")
            (data as Data.Succeeded<List<Track>>).data.first { it.title == title }.id
        }
    }

    /**
     * A snapshot of the library's tracks — exactly what the screens see (the fake repository latches
     * its track list on first read, so tracks `seedGame`'d after the shell launches won't appear here;
     * pick from these existing tracks to seed a playlist/favorites with real, renderable content).
     */
    fun libraryTracks(): List<Track> = runBlocking {
        withTimeout(LOAD_TIMEOUT_MS) {
            val data = graph.memoryRepository
                .getAllTracks(withGame = true, withArtists = false)
                .first { it is Data.Succeeded }
            @Suppress("UNCHECKED_CAST")
            (data as Data.Succeeded<List<Track>>).data
        }
    }

    /** Mark the library track with [id] as a favorite, before opening a screen that reads favorites. */
    fun favoriteTrack(id: Long) = runBlocking { graph.fakeFavoritesRepository.setTrackFavorite(id, true) }

    /** Mark the library game with [id] as a favorite. */
    fun favoriteGame(id: Long) = runBlocking { graph.fakeFavoritesRepository.setGameFavorite(id, true) }

    /** Mark the library artist with [id] as a favorite. */
    fun favoriteArtist(id: Long) = runBlocking { graph.fakeFavoritesRepository.setArtistFavorite(id, true) }

    /** Whether the track with [id] is currently favorited — to assert a toggle took effect. */
    fun isTrackFavorited(id: Long): Boolean = runBlocking { graph.fakeFavoritesRepository.isTrackFavorite(id).first() }

    /** Seed a playlist named [name] holding [trackIds] (in order) and return its id — for opening a
     *  playlist screen, or driving the "Add to Playlist" picker to a known target. */
    fun seedPlaylist(name: String, trackIds: List<Long> = emptyList()): Long =
        graph.fakePlaylistsRepository.seed(name, trackIds)

    /** The id of the seeded playlist named [name] — e.g. to assert a navigation to its detail. */
    fun playlistId(name: String): Long = runBlocking {
        graph.fakePlaylistsRepository.playlists().first().first { it.name == name }.id
    }

    /** The ordered track ids stored in the playlist with [id] — to assert an add/remove/reorder. */
    fun playlistTrackIds(id: Long): List<Long> = runBlocking { graph.fakePlaylistsRepository.trackIds(id).first() }

    /** The names of every playlist currently stored — to assert a creation took effect. */
    fun playlistNames(): List<String> = runBlocking {
        graph.fakePlaylistsRepository.playlists().first().map { it.name }
    }

    /** Poll (pumping the clock) until the playlist with [id] holds exactly [count] tracks. A VM that
     *  adds/removes tracks does so in a coroutine, so the repo settles a beat after the click does. */
    fun waitForPlaylistTrackCount(id: Long, count: Int) {
        compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) { playlistTrackIds(id).size == count }
    }

    /** Poll until a playlist named [name] exists, then return its id — for a creation that lands in a
     *  VM coroutine after a "New Playlist" click. */
    fun waitForPlaylistNamed(name: String): Long {
        compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) { name in playlistNames() }
        return playlistId(name)
    }

    /**
     * Add a game to the library and return its id. Convenience over the populated default — use it
     * when a test needs a screen with known content to assert on.
     */
    fun seedGame(
        title: String,
        tracks: List<String> = listOf("Track 1"),
        artists: List<String> = listOf("Composer"),
    ): Long = runBlocking {
        graph.memoryRepository.upsertGame(
            RawGame(
                title = title,
                photoUrl = null,
                folderKey = "seed/$title",
                folderSignature = "seed",
                tracks = tracks.mapIndexed { index, trackTitle ->
                    RawTrack(
                        path = "/$title/$index",
                        source = "",
                        title = trackTitle,
                        // Cycle artists across tracks — a game with >1 distinct artist renders its
                        // songs as NameCaptionValue rows (artist in the caption) instead of plain
                        // label/value rows.
                        artist = artists[index % artists.size],
                        game = title,
                        length = 120_000L,
                        trackNumber = index + 1,
                        fadeLengthMs = 0L,
                    )
                },
            ),
        ).gameId
    }

    /** Navigate to [destination] (a feature route key) inside the active tab, where the chrome is. */
    fun startAtScreen(destination: Any) {
        destinations.tryEmit(destination)
        compose.waitForIdle()
    }

    /**
     * Wait (up to [LOAD_TIMEOUT_MS]) for a node displaying [text] to compose — for content that
     * loads asynchronously after [startAtScreen]. The fake library emits its `Loading → Succeeded`
     * off the compose clock, so `waitForIdle` alone can return before the rows arrive; poll until
     * they do, then assert.
     */
    fun waitForContent(text: String) {
        compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
            compose.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Seed the fake director into a known playing state — [session], its current [track], and a
     * non-idle [playbackState] — the baseline any screen that renders live playback (Now Playing,
     * the mini-player) reads from. Pair with [startAtScreen] to open such a screen; without a live
     * session the Now Playing screen bounces straight back to the previous one.
     */
    fun startFromSession(
        session: Session,
        track: Track,
        playbackState: PlayerState = PlayerState.PLAYING,
    ) {
        runBlocking {
            graph.fakeDirector.emitPlayback(playbackState)
            graph.fakeDirector.emitMetadata(track)
            graph.fakeDirector.emitSession(session)
        }
    }

    /** Drive the fake scanner into the "scanning" state — the scan-status Home card reacts to it. */
    fun beginScan() = runBlocking { graph.countingScanner.pushState(ScannerState.Scanning()) }

    /** Emit a per-folder scan heartbeat — surfaces as the card's "current folder" line. */
    fun scanReadingFolder(name: String) =
        runBlocking { graph.countingScanner.pushEvent(ScannerEvent.FolderScanned(name)) }

    /** Emit a per-file scan heartbeat — surfaces as the card's "currently reading" line. */
    fun scanReadingFile(name: String) =
        runBlocking { graph.countingScanner.pushEvent(ScannerEvent.FileScanned(name)) }

    /** Emit a "game added" scan change — surfaces as a row in the card's change list. */
    fun scanFoundGame(title: String, trackCount: Int, gameId: Long) =
        runBlocking { graph.countingScanner.pushEvent(ScannerEvent.GameFoundEvent(gameId, title, trackCount, null)) }

    /** Drive the scanner to a successful completion with the given totals. */
    fun completeScan(games: Int, tracks: Int) =
        runBlocking { graph.countingScanner.pushState(ScannerState.Complete(0, games, tracks, 0)) }

    /** Drive the scanner to a failure at [path]. */
    fun failScan(path: String) = runBlocking { graph.countingScanner.pushState(ScannerState.Failed(path)) }

    /**
     * Scroll the Home list so the scan-status card is on screen. The card is prepended at the top of
     * an already-laid-out Home list, so the lazy grid anchors it just above the fold; bring it into
     * view before asserting it's displayed or tapping it. Call only when the card is present.
     */
    fun revealScanCard() {
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .onFirst()
            .performScrollToNode(hasTestTag("ScanStatusCardListModel"))
        compose.waitForIdle()
    }

    /** Poll (pumping the clock) until no node displays [text] — e.g. after dismissing a card. */
    fun waitForContentGone(text: String) {
        compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
            compose.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
    }

    /**
     * Click the node tagged [tag]. Invokes the node's semantics `OnClick` action rather than
     * injecting a gesture, so it isn't blocked when the bottom mini-player overlaps the control (the
     * mini-player is hidden in production but the bare test scene can't honour that request). Uses
     * the unmerged tree, where tagged list-rows surface as their own nodes.
     */
    fun clickTag(tag: String) {
        pauseForObservation()
        compose.onNode(hasTestTag(tag)).performSemanticsAction(SemanticsActions.OnClick)
        // Unlike an injected gesture, performSemanticsAction doesn't pump frames, so advance the
        // clock to let the resulting state change settle through the VM and any crossfade animation
        // before the next step (well under the context menu's 5s auto-dismiss).
        compose.mainClock.advanceTimeBy(CLICK_SETTLE_MS)
        compose.waitForIdle()
    }

    /**
     * Assert a node displaying [text] exists in the unmerged tree — for content inside merged
     * list-rows (e.g. the Now Playing context-menu labels) that the default merged finders don't
     * surface as their own node.
     */
    fun assertTextInRow(text: String) {
        compose.onNode(hasText(text), useUnmergedTree = true).assertExists()
    }

    /** Assert no node displaying [text] exists in the unmerged tree — e.g. the context menu
     *  collapsed back to track info. */
    fun assertTextNotInRow(text: String) {
        compose.onNode(hasText(text), useUnmergedTree = true).assertDoesNotExist()
    }

    /** Click the [WideItem][net.sigmabeta.sage.components.WideItemListModel] row named [name]. */
    fun clickWideItem(name: String) = clickItem("WideItemListModel", name)

    /**
     * Click the grid cover ([GridImageListModel][net.sigmabeta.sage.components.GridImageListModel])
     * titled [name] — a game/artist cover cell in a grid.
     */
    fun clickGridImage(name: String) = clickItem("GridImageListModel", name)

    /**
     * Click the [NameCaptionValueItem][net.sigmabeta.sage.components.NameCaptionValueListModel] row
     * named [name].
     */
    fun clickNameCaptionValueItem(name: String) = clickItem("NameCaptionValueListModel", name)

    // Select by item type (the model's testTag, set in ListModel.Content) AND displayed name, so
    // the typed verbs target the right kind of row even when two item types show the same text.
    private fun clickItem(typeTag: String, name: String) {
        pauseForObservation()
        compose.onNode(hasTestTag(typeTag) and hasText(name)).performClick()
        compose.waitForIdle()
    }

    /**
     * Click the [IconNameCaptionItem][net.sigmabeta.sage.components.IconNameCaptionListModel] row
     * named [name] (e.g. a playlist row on the Playlists screen).
     */
    fun clickIconNameCaptionItem(name: String) = clickItem("IconNameCaptionListModel", name)

    /** Click the [Cta][net.sigmabeta.sage.components.CtaListModel] row reading [name] (e.g.
     *  "New Playlist", "Play All") — scoped to the CTA type so it can't collide with a same-named
     *  row (e.g. a playlist literally named "New Playlist"). */
    fun clickCta(name: String) = clickItem("CtaListModel", name)

    /**
     * Click the (single) node displaying [text], regardless of item type — for when the row kind
     * doesn't matter (e.g. a song row that's a label/value or name/caption/value depending on the
     * game's artist count). Prefer the typed `click*Item` verbs when the type is meaningful.
     */
    fun click(text: String) {
        pauseForObservation()
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    /**
     * Home-specific: click the *first* card in the horizontal scroller of the Home section titled
     * [sectionName] (e.g. `clickFirstCardInHomeSection("Games of the day")`). Home lays each section
     * out as a `SectionHeaderListModel` row followed by a `HorizontalScrollerListModel` sibling whose
     * children are the cards; the first card is always at the scroller's start, so it's reliably
     * on-screen regardless of viewport width or the section's (date-shuffled) order — unlike clicking
     * a card by name, which can be scrolled off a narrow device's viewport.
     */
    fun clickFirstCardInHomeSection(sectionName: String) {
        pauseForObservation()
        val header = hasTestTag("SectionHeaderListModel") and hasAnyDescendant(hasText(sectionName))
        val rows = compose.onNode(header).onParent().onChildren()
        val headerIndex = rows.fetchSemanticsNodes().indexOfFirst { header.matches(it) }
        check(headerIndex >= 0) { "No Home section header found for '$sectionName'" }
        // The scroller is the header's next sibling; its first child is the first card.
        rows[headerIndex + 1].onChildren().onFirst().performClick()
        compose.waitForIdle()
    }

    /**
     * Type [query] into the screen's text field (e.g. the Search field) and let the query settle.
     * Targets the single editable node (`hasSetTextAction`), then advances the test clock past the
     * search debounce (`waitForIdle` alone wouldn't — a scheduled debounce delay reads as "idle") so
     * results are loaded before the next assertion.
     */
    fun typeSearch(query: String) {
        compose.onNode(hasSetTextAction()).performTextInput(query)
        // The query debounce runs on the VM's coroutine scope (real time here, not the compose frame
        // clock), so let real time pass, then render the results.
        Thread.sleep(SEARCH_DEBOUNCE_WAIT_MS)
        compose.waitForIdle()
    }

    /** Assert the current screen's title (rendered in the chrome's top bar) is [text]. */
    fun assertTitle(text: String) {
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    /**
     * Assert *some* node displaying [text] is shown, regardless of what kind. Prefer the typed
     * `assert*Displayed` verbs below — they pin the list-model type, so they don't collide with the
     * same text appearing in a different kind of row (e.g. an artist name that's also a song caption).
     */
    fun assertDisplayed(text: String) {
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    /**
     * Assert the screen shows a section header (a `SectionHeaderListModel` row) reading [text] — e.g.
     * `assertSectionHeader("Songs")` on a detail screen. Detail content is a lazy list, so the header
     * may be below the fold and not yet composed; scroll the innermost vertical scroller to it first
     * (the detail screen nests a content scroller inside an outer page scroller — target the inner
     * one). Tolerates the header already being on screen / the content not scrolling.
     *
     * The header's title `Text` sits on a child node (`SectionHeader` doesn't merge its descendants),
     * so match the tagged row by a descendant carrying [text] rather than text on the row itself.
     */
    fun assertSectionHeader(text: String) {
        val header = hasTestTag("SectionHeaderListModel") and hasAnyDescendant(hasText(text))
        runCatching {
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
                .onLast()
                .performScrollToNode(header)
        }
        compose.onNode(header).assertIsDisplayed()
    }

    /** Assert a [WideItemListModel][net.sigmabeta.sage.components.WideItemListModel] row named [name]
     *  is displayed (e.g. an artist row on a detail screen). */
    fun assertWideItemDisplayed(name: String) = assertItemDisplayed("WideItemListModel", name)

    /** Assert a grid cover ([GridImageListModel]) titled [name] is displayed (a game/artist cover, or
     *  a Home "RNG" card). */
    fun assertGridImageItemDisplayed(name: String) = assertItemDisplayed("GridImageListModel", name)

    /** Assert an icon + label row ([IconNameListModel]) named [name] is displayed (e.g. a Library or
     *  Browse-by-Platform menu row). */
    fun assertIconNameItemDisplayed(name: String) = assertItemDisplayed("IconNameListModel", name)

    /** Assert a call-to-action button row ([CtaListModel]) reading [name] is displayed (e.g.
     *  "Shuffle all tracks", "Play All"). */
    fun assertCtaDisplayed(name: String) = assertItemDisplayed("CtaListModel", name)

    /** Assert an empty-state row ([EmptyStateListModel]) reading [text] is displayed (e.g.
     *  "No crashes recorded."). */
    fun assertEmptyStateDisplayed(text: String) = assertItemDisplayed("EmptyStateListModel", text)

    /** Assert a single-text row ([SingleTextListModel]) reading [text] is displayed (e.g. an expanded
     *  dropdown option like "Light"). */
    fun assertSingleTextItemDisplayed(text: String) = assertItemDisplayed("SingleTextListModel", text)

    /** Assert a name + caption row ([NameCaptionListModel]) is displayed — by [name], and [caption]
     *  when given (e.g. a Settings library row, or a Search song result). */
    fun assertNameCaptionItemDisplayed(name: String, caption: String? = null) =
        assertItemDisplayed("NameCaptionListModel", *listOfNotNull(name, caption).toTypedArray())

    /** Assert a name + caption + value row ([NameCaptionValueListModel]) is displayed — by [name], and
     *  [caption] when given (e.g. a track row: name = title, caption = artist, value = duration). */
    fun assertNameCaptionValueItemDisplayed(name: String, caption: String? = null) =
        assertItemDisplayed("NameCaptionValueListModel", *listOfNotNull(name, caption).toTypedArray())

    /** Assert an icon + name + caption row ([IconNameCaptionListModel]) is displayed — by [name], and
     *  [caption] when given (e.g. a playlist row: name = playlist name, caption = song count). */
    fun assertIconNameCaptionItemDisplayed(name: String, caption: String? = null) =
        assertItemDisplayed("IconNameCaptionListModel", *listOfNotNull(name, caption).toTypedArray())

    // Assert a list row of model type [typeTag] carrying every one of [texts] is displayed. Each text
    // may sit on the tagged row itself (most models merge it) or on a descendant (some don't), so
    // match either. Scoping by the model tag is what makes these more precise than `assertDisplayed`.
    private fun assertItemDisplayed(typeTag: String, vararg texts: String) {
        val matcher = texts.fold(hasTestTag(typeTag)) { acc, text ->
            acc and (hasText(text) or hasAnyDescendant(hasText(text)))
        }
        compose.onNode(matcher).assertIsDisplayed()
    }

    /**
     * Assert the shell navigated to [destination] (a typed route key) at some point. Route keys are
     * data classes, so this matches on value — e.g. `assertNavigationEvent(ArtistDetail(3023))`.
     */
    fun assertNavigationEvent(destination: Any) {
        compose.waitForIdle()
        check(destination in navigations) {
            "Expected a navigation to $destination, but saw: $navigations"
        }
    }

    /** Assert the shell navigated to a route of type [T], when the exact args aren't predictable —
     *  e.g. `assertNavigationEventOfType<GameDetail>()` for a "random game" jump. */
    inline fun <reified T : Any> assertNavigationEventOfType() = assertNavigationEventOfType(T::class)

    fun assertNavigationEventOfType(type: KClass<*>) {
        compose.waitForIdle()
        check(navigations.any { type.isInstance(it) }) {
            "Expected a navigation to a ${type.simpleName}, but saw: $navigations"
        }
    }

    /** Snapshot of the route keys the shell has navigated to (for [lastNavigationOfType]). */
    fun recordedNavigations(): List<Any> {
        compose.waitForIdle()
        return navigations.toList()
    }

    /** The most recent navigation of route type [T], or null — for asserting a route's *payload* when
     *  the args aren't predictable (e.g. the `Playlists` picker's pendingTrackIds / suggestedName). */
    inline fun <reified T : Any> lastNavigationOfType(): T? =
        recordedNavigations().filterIsInstance<T>().lastOrNull()

    /**
     * Assert the [Director] received [request] (by value) — e.g.
     * `assertDirectorReceived(SessionRequest.Play)`. For requests carrying a value you don't want
     * to spell out (a `Start` with a whole `Session`), use the type-only overload below.
     */
    fun assertDirectorReceived(request: SessionRequest) {
        compose.waitForIdle()
        check(request in graph.fakeDirector.requests) {
            "Expected the director to receive $request, but saw: ${graph.fakeDirector.requests}"
        }
    }

    /** Assert the [Director] received any request of type [T] — e.g.
     *  `assertDirectorReceived<SessionRequest.Start>()` for "playback started". */
    inline fun <reified T : SessionRequest> assertDirectorReceived() =
        assertDirectorReceivedOfType(T::class)

    fun assertDirectorReceivedOfType(type: KClass<out SessionRequest>) {
        compose.waitForIdle()
        val received = graph.fakeDirector.requests
        check(received.any { type.isInstance(it) }) {
            "Expected the director to receive a ${type.simpleName} request, but saw: $received"
        }
    }

    /**
     * Assert the [Director] was asked to start a [Session] satisfying [predicate] — for pinning *which*
     * session a control kicked off (its [type][Session.type], shuffle flag, starting position) when the
     * full `Session` value is impractical to spell out. [description] is shown if no match is found.
     */
    fun assertStartedSession(description: String = "a matching session", predicate: (Session) -> Boolean) {
        compose.waitForIdle()
        val started = graph.fakeDirector.requests.filterIsInstance<SessionRequest.Start>().map { it.session }
        check(started.any(predicate)) {
            "Expected the director to start $description, but saw started sessions: $started"
        }
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 10_000L

        /** Gradle `-P` flag, system property, and instrumentation-arg key for the observe delay (ms). */
        const val ACTION_DELAY_KEY = "chipbox.uitest.actionDelayMs"

        /** Comfortably past the Search VM's 300ms query debounce. */
        const val SEARCH_DEBOUNCE_WAIT_MS = 500L

        /** Frames to settle a [clickTag] semantics action (state hop + crossfade); under the Now
         *  Playing context menu's 5s auto-dismiss so the menu stays open for the next step. */
        const val CLICK_SETTLE_MS = 2_000L
    }
}
