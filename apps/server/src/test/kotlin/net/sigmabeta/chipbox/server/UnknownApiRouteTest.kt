package net.sigmabeta.chipbox.server

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.database.fake.FakeDatabase
import net.sigmabeta.chipbox.entities.GameEntity
import net.sigmabeta.chipbox.repository.database.DatabaseRepository
import net.sigmabeta.chipbox.scanner.state.ScannerState
import net.sigmabeta.sage.logging.BluntHatchet

/**
 * The SPA fallback must not answer API requests with `index.html`.
 *
 * A browser client decodes every `/api/...` response as JSON. Before this contract, an unknown API
 * path fell through to the static tailcard and returned a 200 HTML page, so the client reported a
 * deserialization error instead of "Not found". Now any unmatched API request — any method —
 * gets the standard `{"error": …}` JSON 404, while real app routes still get the SPA shell.
 */
class UnknownApiRouteTest {

    @Test
    fun `unknown GET api path returns the standard JSON 404`() = withServer {
        val response = client.get("/api/does-not-exist")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        assertTrue(errorMessage(response.bodyAsText()).isNotBlank())
    }

    @Test
    fun `unknown POST api path returns the standard JSON 404`() = withServer {
        val response = client.post("/api/does-not-exist")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        assertTrue(errorMessage(response.bodyAsText()).isNotBlank())
    }

    @Test
    fun `deep unknown api path returns JSON 404`() = withServer {
        val response = client.get("/api/one/two/three")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
    }

    @Test
    fun `known api routes are not shadowed by the fallback`() = withServer {
        val list = client.get("/api/games")
        assertEquals(HttpStatusCode.OK, list.status)
        assertEquals(ContentType.Application.Json, list.contentType()?.withoutParameters())
        assertTrue(list.bodyAsText().trimStart().startsWith("["), "expected the JSON list")

        // A parameterized route must still resolve to its own handler, not the /api tailcard.
        val single = client.get("/api/games/1")
        assertEquals(HttpStatusCode.OK, single.status)
        assertEquals(ContentType.Application.Json, single.contentType()?.withoutParameters())

        val health = client.get("/api/health")
        assertEquals(HttpStatusCode.OK, health.status)
        assertEquals(ContentType.Application.Json, health.contentType()?.withoutParameters())
    }

    @Test
    fun `app deep paths still serve the SPA shell`() = withServer {
        val response = client.get("/artists/42/tracks")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Text.Html, response.contentType()?.withoutParameters())
        assertEquals("<html>chipbox test shell</html>", response.bodyAsText().trim())
    }

    private fun errorMessage(body: String): String =
        Json.parseToJsonElement(body).jsonObject.getValue("error").jsonPrimitive.content
}

/** Boots the production routing stack over one seeded game and an empty content-source registry. */
private fun withServer(block: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) = runBlocking {
    val db = FakeDatabase()
    db.gameDao.insert(
        GameEntity(
            title = "Fixture Game",
            photoUrl = null,
            folderKey = "/library/fixture",
            folderSignature = "signature",
        ),
    )
    val repository = DatabaseRepository(
        artistDao = db.artistDao,
        gameDao = db.gameDao,
        trackDao = db.trackDao,
        gameArtistDao = db.gameArtistDao,
        trackArtistDao = db.trackArtistDao,
        searchHistoryDao = db.searchHistoryDao,
        hatchet = BluntHatchet(),
    )
    testApplication {
        application {
            configureChipboxServer(
                repository = repository,
                contentSources = ContentSourceRegistry(emptySet()),
                currentScannerState = { ScannerState.Idle },
                logCalls = false,
            )
        }
        block()
    }
}
