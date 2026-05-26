package net.sigmabeta.chipbox.coverart.real

import net.sigmabeta.chipbox.coverart.OverrideEntry
import net.sigmabeta.chipbox.models.Platform
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoverArtOverridesTest {

    private val file = "/cache/overrides.json".toPath()

    @Test
    fun `imageId returns null when no override exists`() {
        val overrides = CoverArtOverrides(FakeFileSystem(), file)
        assertNull(overrides.imageId("Anything", setOf(Platform.SNES)))
    }

    @Test
    fun `set then imageId round-trips`() {
        val overrides = CoverArtOverrides(FakeFileSystem(), file)
        val entry = OverrideEntry(igdbId = "1", igdbName = "Name", imageId = "co1abc", igdbSlug = "name")
        overrides.set("Game", setOf(Platform.PSX), entry)
        assertEquals("co1abc", overrides.imageId("Game", setOf(Platform.PSX)))
    }

    @Test
    fun `get returns the full OverrideEntry`() {
        val overrides = CoverArtOverrides(FakeFileSystem(), file)
        val entry = OverrideEntry("42", "Chrono Trigger", "co1abc", "chrono-trigger")
        overrides.set("Chrono Trigger", setOf(Platform.SNES), entry)
        assertEquals(entry, overrides.get("Chrono Trigger", setOf(Platform.SNES)))
    }

    @Test
    fun `set persists across instances on the same filesystem`() {
        // set() invokes save() internally; a fresh instance reading from the same FS must see it.
        val fs = FakeFileSystem()
        val entry = OverrideEntry("42", "n", "img", "slug")
        CoverArtOverrides(fs, file).set("Game", emptySet(), entry)
        val reloaded = CoverArtOverrides(fs, file)
        assertEquals(entry, reloaded.get("Game", emptySet()))
    }

    @Test
    fun `clear removes the override and returns true`() {
        val overrides = CoverArtOverrides(FakeFileSystem(), file)
        overrides.set("Game", emptySet(), OverrideEntry("1", "n", "i", "s"))
        assertTrue(overrides.clear("Game", emptySet()))
        assertNull(overrides.get("Game", emptySet()))
    }

    @Test
    fun `clear on a missing override returns false and is a no-op`() {
        // Documented return: only true when something actually existed. The "no-op" half matters
        // because clear() short-circuits the disk write — verify nothing was created.
        val fs = FakeFileSystem()
        val overrides = CoverArtOverrides(fs, file)
        assertFalse(overrides.clear("Game", emptySet()))
        assertFalse(fs.exists(file), "clear on a missing key must not touch the disk")
    }

    @Test
    fun `platform-set order in the key does not split overrides`() {
        // The override key uses coverArtKey, which sorts platforms by name — so a Set built
        // (SNES, PSX) must read back the same record as (PSX, SNES).
        val overrides = CoverArtOverrides(FakeFileSystem(), file)
        val entry = OverrideEntry("1", "n", "i", "s")
        overrides.set("Game", setOf(Platform.SNES, Platform.PSX), entry)
        assertEquals(entry, overrides.get("Game", setOf(Platform.PSX, Platform.SNES)))
    }

    @Test
    fun `malformed on-disk JSON degrades to no overrides`() {
        // runCatching swallows JSON parse errors — confirm the store still loads, just empty.
        val fs = FakeFileSystem()
        fs.createDirectories(file.parent!!)
        fs.write(file) { writeUtf8("not json :-)") }
        val overrides = CoverArtOverrides(fs, file)
        assertNull(overrides.imageId("any", emptySet()))
    }

    @Test
    fun `set writes a non-empty file to disk`() {
        // Catches a regression where save() short-circuits before flushing the entry — the file
        // must exist with the override-bearing JSON the next instance can read.
        val fs = FakeFileSystem()
        val overrides = CoverArtOverrides(fs, file)
        overrides.set("Game", emptySet(), OverrideEntry("1", "n", "i", "s"))
        val written = fs.read(file)
        assertTrue(written.isNotBlank())
        assertTrue(written.contains("\"imageId\": \"i\""), "expected imageId in JSON; got:\n$written")
    }

    @Test
    fun `setting a second override appends it without dropping the first`() {
        val overrides = CoverArtOverrides(FakeFileSystem(), file)
        val first = OverrideEntry("1", "first", "img1", "slug1")
        val second = OverrideEntry("2", "second", "img2", "slug2")
        overrides.set("First Game", emptySet(), first)
        overrides.set("Second Game", emptySet(), second)
        assertEquals(first, overrides.get("First Game", emptySet()))
        assertEquals(second, overrides.get("Second Game", emptySet()))
    }

    @Test
    fun `set overwrites the existing entry for the same key`() {
        val overrides = CoverArtOverrides(FakeFileSystem(), file)
        val v1 = OverrideEntry("1", "first", "img1", "slug1")
        val v2 = OverrideEntry("2", "second", "img2", "slug2")
        overrides.set("Game", emptySet(), v1)
        overrides.set("Game", emptySet(), v2)
        assertEquals(v2, overrides.get("Game", emptySet()))
    }

    /** Read [path]'s UTF-8 content — small wrapper around Okio's read-with-block form. */
    private fun FakeFileSystem.read(path: okio.Path): String = read(path) { readUtf8() }
}
