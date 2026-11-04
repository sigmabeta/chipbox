package net.sigmabeta.chipbox.abrender

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device counterpart to the desktop `abrender render` command. Runs the exact same [runRender]
 * engine on a real device so the **arm64** emulator `.so` files are exercised — the desktop harness
 * only ever proves x86_64. Output is identical in shape (a labelled run dir with `metrics.tsv` + per-
 * track WAVs), so the desktop `abrender diff` can A/B an on-device run against an x86_64 oracle with
 * no Android-specific analysis code.
 *
 * It is self-instrumenting (no app under test): the corpus is adb-staged into **shared storage**
 * (`/sdcard/Music/chipbox-test/abrender/in`) and the run is written beside it for adb to pull.
 * Shared storage, not the app's external files dir: on Android 11+ the adb shell cannot create the
 * corpus tree under another app's `Android/data`, and the app cannot read a shell-created tree
 * there. The manifest requests `MANAGE_EXTERNAL_STORAGE`, which `record.sh` grants with
 * `adb shell appops set <pkg> MANAGE_EXTERNAL_STORAGE allow` before instrumenting. Drive it with
 * record.sh, or by hand:
 *
 * ```
 * adb shell am instrument -w \
 *   -e dir  /sdcard/Music/chipbox-test/abrender/in \
 *   -e out  /sdcard/Music/chipbox-test/abrender/out \
 *   -e ext usf,miniusf -e seconds 30 -e label arm64 \
 *   net.sigmabeta.chipbox.abrender.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 *
 * Every knob mirrors a desktop flag, passed as an instrumentation arg (`-e <key> <value>`): `dir`,
 * `out`, `label`, `seconds`, `ext`, `game`, `every-song`, `subsong`, `limit`, `overwrite`,
 * `max-wall-seconds`, `shards`, `shard`. Both paths default to the shared root above
 * (`.../abrender/{in,out}`).
 */
@RunWith(AndroidJUnit4::class)
class DeviceRenderTest {
    @Test
    fun renderCorpus() {
        val bundle = InstrumentationRegistry.getArguments()

        fun arg(vararg keys: String): String? = keys.firstNotNullOfOrNull { bundle.getString(it) }
        fun flag(vararg keys: String): Boolean = arg(*keys)?.equals("true", ignoreCase = true) == true

        val corpusDir = File(arg("dir") ?: "$SHARED_ROOT/in")
        val outDir = File(arg("out") ?: "$SHARED_ROOT/out")
        corpusDir.mkdirs()
        outDir.mkdirs()

        println("abrender-device: corpus = $corpusDir")
        println("abrender-device: out    = $outDir")

        // Fail loudly (with the path to fix) if the harness never pushed a corpus — otherwise the run
        // is a silent no-op that looks like a pass.
        val supported = EmulatorBank().supportedExtensions
        val playable = corpusDir.walkTopDown().count { it.isFile && it.extension.lowercase() in supported }
        assertTrue(
            "No playable files under $corpusDir — push a corpus there first, " +
                "e.g. `adb push <corpus>/. $corpusDir`.",
            playable > 0,
        )

        val args = Args(
            command = "render",
            label = arg("label") ?: "device",
            seconds = arg("seconds")?.toIntOrNull() ?: DEFAULT_SECONDS,
            outDir = outDir,
            corpusDir = corpusDir,
            extensions = arg("ext")
                ?.split(",")?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }?.toSet(),
            gameFilter = arg("game"),
            everySong = flag("every-song", "everySong"),
            subsong = arg("subsong")?.toIntOrNull() ?: 0,
            limit = arg("limit")?.toIntOrNull(),
            overwrite = flag("overwrite"),
            maxWallMillis = arg("max-wall-seconds", "maxWallSeconds")?.toLongOrNull()
                ?.times(MILLIS_PER_SECOND) ?: DEFAULT_MAX_WALL_MILLIS,
            shards = arg("shards")?.toIntOrNull() ?: 1,
            shard = arg("shard")?.toIntOrNull() ?: 0,
            runA = null,
            runB = null,
            topN = 0,
            tolerance = null,
            check = false,
        )

        val summary = runRender(args)
        println(
            "abrender-device: rendered=${summary.rendered} " +
                "skipped=${summary.skipped} failed=${summary.failed}",
        )

        // Open the run tree to other readers so `adb pull` (the shell user) can retrieve it. The
        // default shared-storage tree is already group-readable, but an explicit `out` under an
        // app-private dir is not, and a silently unreadable run would look like a failed render.
        outDir.walkTopDown().forEach { entry ->
            entry.setReadable(true, false)
            if (entry.isDirectory) entry.setExecutable(true, false)
        }

        assertTrue(
            "metrics.tsv was not written under $outDir/${args.label}",
            File(File(outDir, args.label), "metrics.tsv").exists(),
        )
    }

    private companion object {
        // The same shared root :benchmark uses; adb can create it and the app can read/write it
        // under All Files Access.
        const val SHARED_ROOT = "/sdcard/Music/chipbox-test/abrender"
        const val DEFAULT_SECONDS = 30
        const val MILLIS_PER_SECOND = 1000L
        const val DEFAULT_MAX_WALL_MILLIS = 120L * MILLIS_PER_SECOND
    }
}
