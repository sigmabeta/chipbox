package net.sigmabeta.chipbox.browserstate

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull

/**
 * The on-disk envelope shared by every browser-local store: a schema header plus the store's
 * typed payload. Keeping the version outside the payload lets a newer app be recognised (and
 * ignored safely) even when the payload shape has changed and the current serializer can no
 * longer read it.
 */
@Serializable
data class VersionedEnvelope<T>(
    val schemaVersion: Int,
    val payload: T,
)

/** Why a stored blob could not be used. */
enum class DecodeFailure {
    /** Not JSON, or the envelope/payload shape is wrong. */
    CORRUPT,

    /** Written by a newer app version whose payload this build cannot understand. */
    UNSUPPORTED_VERSION,
}

/** Result of decoding one stored string. Never an exception — the caller chooses the fallback. */
sealed interface DecodeOutcome<out T> {
    /** No value was ever stored (or the store was cleared). Normal first-run state. */
    data object Missing : DecodeOutcome<Nothing>

    /** A usable payload. [migratedFrom] is set when the envelope was older than [VersionedJsonCodec.currentVersion]. */
    data class Success<T>(val value: T, val migratedFrom: Int? = null) : DecodeOutcome<T>

    /** The stored value cannot be used; fall back to the store's empty value and report [reason]. */
    data class Failed(val reason: DecodeFailure, val detail: String) : DecodeOutcome<Nothing>
}

/**
 * Encodes/decodes a [VersionedEnvelope] around a payload serializer.
 *
 * Missing and corrupt/older/newer data are all non-throwing outcomes: the repositories map
 * Missing to their empty value, migrate an older payload that still decodes, and report anything
 * else through [BrowserStateEvents]. The JSON reader ignores unknown keys so additive payload
 * changes remain readable.
 */
class VersionedJsonCodec<T>(
    private val payloadSerializer: KSerializer<T>,
    val currentVersion: Int,
    private val json: Json = DEFAULT_JSON,
) {
    fun encode(value: T): String =
        json.encodeToString(VersionedEnvelope.serializer(payloadSerializer), VersionedEnvelope(currentVersion, value))

    fun decode(raw: String?): DecodeOutcome<T> {
        if (raw.isNullOrBlank()) return DecodeOutcome.Missing

        val root = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
            ?: return DecodeOutcome.Failed(DecodeFailure.CORRUPT, "stored value is not a JSON object")

        val version = (root["schemaVersion"] as? JsonPrimitive)?.intOrNull
            ?: return DecodeOutcome.Failed(DecodeFailure.CORRUPT, "missing or non-numeric schemaVersion")
        if (version < 0) return DecodeOutcome.Failed(DecodeFailure.CORRUPT, "negative schemaVersion $version")

        if (version > currentVersion) {
            return DecodeOutcome.Failed(
                DecodeFailure.UNSUPPORTED_VERSION,
                "schemaVersion $version is newer than supported version $currentVersion",
            )
        }

        val payload = root["payload"]
            ?: return DecodeOutcome.Failed(DecodeFailure.CORRUPT, "missing payload")

        val decoded = runCatching { json.decodeFromJsonElement(payloadSerializer, payload) }.getOrNull()
            ?: return DecodeOutcome.Failed(DecodeFailure.CORRUPT, "payload does not match the v$version shape")

        return DecodeOutcome.Success(decoded, migratedFrom = version.takeIf { it < currentVersion })
    }

    companion object {
        val DEFAULT_JSON: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
