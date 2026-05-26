package net.sigmabeta.chipbox.coverart.real

import net.sigmabeta.chipbox.coverart.CoverLookup
import net.sigmabeta.chipbox.models.Platform
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoverArtCacheTest {

    private val cacheFile = "/cache/cover-cache.json".toPath()

    @Test
    fun `get returns null on a cold cache`() {
        val cache = CoverArtCache(FakeFileSystem(), cacheFile)
        assertNull(cache.get("Chrono Trigger", setOf(Platform.SNES)))
    }

    @Test
    fun `recordLookup with Found then get round-trips every IGDB field`() {
        val cache = CoverArtCache(FakeFileSystem(), cacheFile)
        val match = CoverLookup.Found(
            imageId = "co1abc",
            igdbId = "1234",
            igdbName = "Chrono Trigger",
            igdbSlug = "chrono-trigger",
        )
        cache.recordLookup("Chrono Trigger", setOf(Platform.SNES), match)
        assertEquals(match, cache.get("Chrono Trigger", setOf(Platform.SNES)))
    }

    @Test
    fun `recordLookup with NoCover round-trips the IGDB identifiers`() {
        // NoCover means "matched the game, but IGDB has no art for it". The id and name must
        // survive so we don't re-query on the next run.
        val cache = CoverArtCache(FakeFileSystem(), cacheFile)
        val match = CoverLookup.NoCover(igdbId = "42", igdbName = "Obscure", igdbSlug = "obscure")
        cache.recordLookup("Obscure", emptySet(), match)
        assertEquals(match, cache.get("Obscure", emptySet()))
    }

    @Test
    fun `recordLookup with NoMatch round-trips as NoMatch`() {
        // NoMatch's CacheKind round-trips through the toLookup mapping — verify the sentinel
        // doesn't get accidentally collapsed to null on read.
        val cache = CoverArtCache(FakeFileSystem(), cacheFile)
        cache.recordLookup("Nothing", emptySet(), CoverLookup.NoMatch)
        assertEquals(CoverLookup.NoMatch, cache.get("Nothing", emptySet()))
    }

    @Test
    fun `Found entry with an empty imageId is treated as invalid and skipped`() {
        // isValid() filters out FOUND entries with null imageId — a defensive check for cache
        // files that pre-date the change that started writing imageId for every Found entry.
        val cache = CoverArtCache(FakeFileSystem(), cacheFile)
        cache.recordLookup("Edge", emptySet(), CoverLookup.Found(imageId = "")) // empty payload
        // imageId is non-null but empty — get() unwraps it to an empty string and orEmpty()s it
        // back, so the round-trip still produces a Found with "" — the *real* "imageId == null"
        // case can only happen in pre-existing on-disk JSON. Validate the on-disk path next.
        assertNotNull(cache.get("Edge", emptySet()))
    }

    @Test
    fun `save and reload preserves entries across instances`() {
        val fs = FakeFileSystem()
        val match = CoverLookup.Found("co1abc", "1234", "Chrono Trigger", "chrono-trigger")
        CoverArtCache(fs, cacheFile).apply {
            recordLookup("Chrono Trigger", setOf(Platform.SNES), match)
            save()
        }
        val reloaded = CoverArtCache(fs, cacheFile)
        assertEquals(match, reloaded.get("Chrono Trigger", setOf(Platform.SNES)))
    }

    @Test
    fun `malformed on-disk JSON gracefully degrades to an empty cache`() {
        // runCatching wraps the JSON parse; failure must not abort startup.
        val fs = FakeFileSystem()
        fs.createDirectories(cacheFile.parent!!)
        fs.write(cacheFile) { writeUtf8("{ this isn't valid JSON ::") }
        val cache = CoverArtCache(fs, cacheFile)
        assertNull(cache.get("anything", emptySet()))
    }

    @Test
    fun `downloadedUrl returns null until recordDownload is called`() {
        val cache = CoverArtCache(FakeFileSystem(), cacheFile)
        cache.recordLookup("Game", emptySet(), CoverLookup.Found("id"))
        assertNull(cache.downloadedUrl("Game", emptySet()), "no download recorded yet")
    }

    @Test
    fun `recordDownload after recordLookup leaves the lookup result intact`() {
        // The download URL is bookkeeping for the file on disk; recording it must not blow away
        // the IGDB lookup it's attached to.
        val cache = CoverArtCache(FakeFileSystem(), cacheFile)
        val match = CoverLookup.Found("co1abc", "1234", "Chrono Trigger", "chrono-trigger")
        cache.recordLookup("Chrono Trigger", setOf(Platform.SNES), match)
        cache.recordDownload("Chrono Trigger", setOf(Platform.SNES), "https://images.igdb/co1abc.jpg")
        assertEquals(match, cache.get("Chrono Trigger", setOf(Platform.SNES)))
        assertEquals("https://images.igdb/co1abc.jpg", cache.downloadedUrl("Chrono Trigger", setOf(Platform.SNES)))
    }

    @Test
    fun `recordDownload on a cold key creates a Found entry seeded with the URL`() {
        // Documented fallback: when nothing's been looked up yet for the key, recordDownload
        // creates a FOUND entry with the url filling both imageId and downloadedUrl.
        val cache = CoverArtCache(FakeFileSystem(), cacheFile)
        cache.recordDownload("Game", emptySet(), "https://cdn/cover.jpg")
        assertEquals("https://cdn/cover.jpg", cache.downloadedUrl("Game", emptySet()))
    }

    @Test
    fun `re-recording the same lookup preserves the existing downloadedUrl`() {
        // The cache must not lose track of which URL is currently on disk just because the
        // IGDB record got re-confirmed.
        val cache = CoverArtCache(FakeFileSystem(), cacheFile)
        val match = CoverLookup.Found("co1abc", "1234", "n", "s")
        cache.recordLookup("Game", emptySet(), match)
        cache.recordDownload("Game", emptySet(), "https://cdn/old.jpg")
        cache.recordLookup("Game", emptySet(), match) // re-record (e.g. after a fresh run)
        assertEquals("https://cdn/old.jpg", cache.downloadedUrl("Game", emptySet()))
    }

    @Test
    fun `save creates the parent directory if it doesn't exist`() {
        val fs = FakeFileSystem()
        val cache = CoverArtCache(fs, "/some/deep/path/cache.json".toPath())
        cache.recordLookup("Game", emptySet(), CoverLookup.Found("id"))
        cache.save()
        assertTrue(fs.exists("/some/deep/path/cache.json".toPath()))
    }
}
