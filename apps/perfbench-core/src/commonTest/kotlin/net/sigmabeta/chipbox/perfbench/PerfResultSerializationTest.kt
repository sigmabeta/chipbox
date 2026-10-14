package net.sigmabeta.chipbox.perfbench

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class PerfResultSerializationTest {
    @Test
    fun `round-trips a fully populated result`() {
        val result = PerfResult(
            generated = "2026-10-13T12:00:00+00:00",
            chipboxCommit = "abc1234",
            sageCommit = "def5678",
            config = PerfConfig(repeat = 3, scenarios = listOf("audio.usf", "audio.vgm", "audio.psf")),
            machine = HostFingerprint(
                cpuModel = "AMD Ryzen 9 5900X 12-Core Processor",
                cpuCores = 24,
                os = "Arch Linux",
                kernel = "6.8.9-arch1-1",
                jvm = "OpenJDK 64-Bit Server VM 17.0.10",
                chipboxCommit = "abc1234",
                sageCommit = "def5678",
            ),
            corpus = CorpusRef(
                root = "/corpus",
                manifest = listOf(CorpusEntry(path = "game/track.usf", sha256 = "deadbeef", bytes = 1234L)),
            ),
            axes = mapOf(
                "audio" to AudioAxis(
                    config = AudioAxisConfig(sampleSeconds = 30, repeats = 3),
                    results = listOf(
                        AudioResult(
                            scenario = "audio.usf",
                            rtf = 12.5,
                            cpuSeconds = 2.01,
                            median = 2.4,
                            min = 2.3,
                            max = 2.5,
                            samples = listOf(2.3, 2.5, 2.4),
                        ),
                    ),
                ),
            ),
        )

        val json = PerfbenchJson.encodeToString(result)
        val back = PerfbenchJson.decodeFromString<PerfResult>(json)

        assertEquals(result, back)
        assertTrue(json.contains("\"class\": \"host\""), "fingerprint class discriminator missing")
        assertTrue(json.contains("\"audio\""), "audio axis key missing")
    }

    @Test
    fun `writes and reads the snake_case schema keys`() {
        val json = PerfbenchJson.encodeToString(minimalResult())

        assertTrue(json.contains("\"chipbox_commit\""))
        assertTrue(json.contains("\"sage_commit\""))
        assertTrue(json.contains("\"cpu_model\""))
        assertTrue(json.contains("\"sample_seconds\""))
        assertTrue(json.contains("\"cpu_s\""))
    }

    @Test
    fun `omitted corpus manifest and snapshot hash default cleanly`() {
        val result = minimalResult()
        assertNull(result.corpus.snapshotHash)

        val back = PerfbenchJson.decodeFromString<PerfResult>(PerfbenchJson.encodeToString(result))

        assertEquals(emptyList(), back.corpus.manifest)
        assertNull(back.corpus.snapshotHash)
    }

    @Test
    fun `keeps the axis subclass through the map`() {
        val result = minimalResult()

        val back = PerfbenchJson.decodeFromString<PerfResult>(PerfbenchJson.encodeToString(result))

        val axis = back.axes["audio"]
        assertIs<AudioAxis>(axis)
        assertEquals(1, axis.results.size)
    }

    @Test
    fun `round-trips a ui axis`() {
        val result = minimalResult().copy(
            axes = mapOf(
                "ui" to UiAxis(
                    config = UiAxisConfig(iterations = 5),
                    results = listOf(
                        UiResult(
                            scenario = "ui.browse_by_game_scroll",
                            frames = 240,
                            frameP50Ms = 8.0,
                            frameP90Ms = 12.0,
                            frameP99Ms = 30.0,
                            jankPercent = 4.5,
                            traceSections = listOf(
                                TraceSectionSum(name = "GridImage", sumMs = 120.0, count = 240),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val json = PerfbenchJson.encodeToString(result)
        val back = PerfbenchJson.decodeFromString<PerfResult>(json)

        assertEquals(result, back)
        assertIs<UiAxis>(back.axes["ui"])
        assertTrue(json.contains("\"frame_p50_ms\""))
        assertTrue(json.contains("\"jank_percent\""))
        assertTrue(json.contains("\"trace_sections\""))
    }

    @Test
    fun `round-trips a startup axis`() {
        val result = minimalResult().copy(
            axes = mapOf(
                "startup" to StartupAxis(
                    config = StartupAxisConfig(iterations = 5),
                    results = listOf(
                        StartupResult(
                            scenario = "startup.cold",
                            timeToFirstContentP50Ms = 320.0,
                            timeToFirstContentP90Ms = 410.0,
                            timeToFirstContentP99Ms = 500.0,
                            samplesMs = listOf(300.0, 320.0, 410.0),
                        ),
                        StartupResult(
                            scenario = "startup.warm",
                            timeToFirstContentP50Ms = 120.0,
                            timeToFirstContentP90Ms = 140.0,
                            timeToFirstContentP99Ms = 160.0,
                            samplesMs = listOf(110.0, 120.0, 140.0),
                        ),
                    ),
                ),
            ),
        )

        val json = PerfbenchJson.encodeToString(result)
        val back = PerfbenchJson.decodeFromString<PerfResult>(json)

        assertEquals(result, back)
        assertIs<StartupAxis>(back.axes["startup"])
        assertTrue(json.contains("\"startup\""))
        assertTrue(json.contains("\"time_to_first_content_p50_ms\""))
        assertTrue(json.contains("\"samples_ms\""))
    }

    @Test
    fun `round-trips a device fingerprint`() {
        val result = minimalResult().copy(
            machine = DeviceFingerprint(
                manufacturer = "Google",
                model = "Pixel 8",
                androidBuild = "AP1A.240505.004",
                abi = "arm64-v8a",
                thermalStatus = "none",
                chipboxCommit = "abc1234",
                sageCommit = "def5678",
            ),
        )

        val json = PerfbenchJson.encodeToString(result)
        val back = PerfbenchJson.decodeFromString<PerfResult>(json)

        assertTrue(json.contains("\"class\": \"device\""))
        assertEquals(result.machine, back.machine)
    }

    private fun minimalResult(): PerfResult = PerfResult(
        generated = "2026-10-13T12:00:00+00:00",
        chipboxCommit = "abc1234",
        sageCommit = "def5678",
        config = PerfConfig(repeat = 1, scenarios = listOf("audio.usf")),
        machine = HostFingerprint("CPU", 8, "Linux", "6.8", "JVM", "abc1234", "def5678"),
        corpus = CorpusRef(root = "/corpus"),
        axes = mapOf(
            "audio" to AudioAxis(
                config = AudioAxisConfig(sampleSeconds = 30, repeats = 1),
                results = listOf(
                    AudioResult("audio.usf", rtf = 10.0, cpuSeconds = 1.5, median = 3.0, min = 3.0, max = 3.0, samples = listOf(3.0)),
                ),
            ),
        ),
    )
}
