package net.sigmabeta.chipbox.perfbench

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * The `diff` read seam: a `results.json` written by a pre-v1.1 harness (an audio result with no
 * `path`) must be recognised as incomparable, not throw out of `main`.
 */
class DiffCommandTest {
    @Test
    fun `refuses a v1-shaped result missing the audio path instead of throwing`() {
        val file = v1ResultFile()

        val read = assertIs<ResultRead.Incompatible>(readResult(file))

        assertTrue(read.reason.isNotBlank(), "the refusal should carry the decode reason")
    }

    @Test
    fun `names the file and the v1_1 incompatibility in the refusal line`() {
        val file = v1ResultFile()

        val message = refusalLine(assertIs<ResultRead.Incompatible>(readResult(file)))

        assertTrue(message.startsWith("Refusing to compare:"), message)
        assertTrue(message.contains(file.absolutePath), message)
        assertTrue(message.contains("v1.1-compatible"), message)
    }

    /** A v1.1 result with its audio result's `path` field removed — the shape a v1 baseline has. */
    private fun v1ResultFile(): File {
        val root = PerfbenchJson.parseToJsonElement(
            PerfbenchJson.encodeToString(
                PerfResult(
                    generated = "2026-10-13T12:00:00+00:00",
                    config = PerfConfig(scenarios = listOf("game/track.spc")),
                    machine = HostFingerprint(
                        cpuModel = "CPU",
                        cpuCores = 8,
                        os = "Linux",
                        kernel = "6.8",
                        jvm = "17",
                        chipboxCommit = "abc1234",
                        sageCommit = "def5678",
                    ),
                    corpus = CorpusRef(root = "/corpus"),
                    axes = mapOf(
                        "audio" to AudioAxis(
                            config = AudioAxisConfig(sampleSeconds = 30, repeats = 3),
                            results = listOf(AudioResult(path = "game/track.spc", rtf = 12.5, median = 2.4)),
                        ),
                    ),
                ),
            ),
        ).jsonObject

        val axes = root.getValue("axes").jsonObject.toMutableMap()
        val audio = axes.getValue("audio").jsonObject.toMutableMap()
        val withoutPath = audio.getValue("results").jsonArray
            .map { JsonObject(it.jsonObject.toMutableMap().apply { remove("path") }) }
        audio["results"] = JsonArray(withoutPath)
        axes["audio"] = JsonObject(audio)

        return Files.createTempFile("perfbench-v1", ".json").toFile().apply {
            writeText(JsonObject(root.toMutableMap().apply { put("axes", JsonObject(axes)) }).toString())
            deleteOnExit()
        }
    }
}
