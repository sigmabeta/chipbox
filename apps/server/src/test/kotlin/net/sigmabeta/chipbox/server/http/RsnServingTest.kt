package net.sigmabeta.chipbox.server.http

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.sigmabeta.chipbox.contentsource.ContentSource
import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.contentsource.LocalFileContentSource
import net.sigmabeta.chipbox.repository.database.DatabaseRepository
import net.sigmabeta.chipbox.server.scanner.ServerRsnExtractor
import net.sigmabeta.chipbox.server.testing.RarEntry
import net.sigmabeta.chipbox.server.testing.allTracks
import net.sigmabeta.chipbox.server.testing.rar4
import net.sigmabeta.chipbox.server.testing.scanCorpus
import net.sigmabeta.chipbox.server.testing.spcFile
import net.sigmabeta.chipbox.server.testing.tempDir
import net.sigmabeta.sage.logging.BluntHatchet
import okio.FileSystem
import okio.Path.Companion.toOkioPath

class RsnServingTest {

    @Test
    fun `extracted members serve through api files`() {
        val first = spcFile(title = "First")
        val second = spcFile(title = "Second")
        val scan = scanFixture(first, second)
        val track = scan.repository.allTracks().single { it.title == "First" }

        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing {
                    libraryRoutes(scan.repository)
                    fileRoutes(scan.repository, scan.contentSources)
                }
            }

            val response = client.get("/api/files/${track.id}")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(
                ContentType.Application.OctetStream,
                response.contentType()?.withoutParameters(),
            )
            assertTrue(response.readBytes().contentEquals(first))
        }
    }

    @Test
    fun `track JSON exposes an api url and never the work directory`() {
        val first = spcFile(title = "First")
        val second = spcFile(title = "Second")
        val scan = scanFixture(first, second)
        val track = scan.repository.allTracks().single { it.title == "First" }

        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing {
                    libraryRoutes(scan.repository)
                    fileRoutes(scan.repository, scan.contentSources)
                }
            }

            val body = client.get("/api/tracks/${track.id}").bodyAsText()
            assertEquals(
                "/api/files/${track.id}",
                Json.parseToJsonElement(body).jsonObject.getValue("path").jsonPrimitive.content,
            )
            assertFalse(body.contains(scan.workDir.absolutePath), "absolute work dir leaked: $body")
            assertFalse(body.contains("/rsn/"), "extraction layout leaked: $body")
            // The same rewrite applies to nested tracks on collection endpoints.
            val gamesBody = client.get("/api/games?withTracks=true").bodyAsText()
            assertFalse(gamesBody.contains(scan.workDir.absolutePath), "absolute work dir leaked: $gamesBody")
            assertTrue(gamesBody.contains("/api/files/"))
        }
    }
}

private data class ServingFixture(
    val repository: DatabaseRepository,
    val contentSources: ContentSourceRegistry,
    val workDir: File,
)

private fun scanFixture(first: ByteArray, second: ByteArray): ServingFixture {
    val corpus = tempDir("rsn-corpus")
    val workDir = tempDir("rsn-work")
    File(corpus, "tiny.rsn").writeBytes(
        rar4(RarEntry("01 First.spc", first), RarEntry("02 Second.spc", second)),
    )
    val scan = scanCorpus(corpus, workDir, ServerRsnExtractor(workDir, BluntHatchet()))
    val source = LocalFileContentSource(
        FileSystem.SYSTEM,
        File(workDir, "locations.txt").toOkioPath(),
    )
    return ServingFixture(
        repository = scan.repository,
        contentSources = ContentSourceRegistry(setOf<ContentSource>(source)),
        workDir = workDir,
    )
}
