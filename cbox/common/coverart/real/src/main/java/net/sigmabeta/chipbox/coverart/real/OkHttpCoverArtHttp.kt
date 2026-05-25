package net.sigmabeta.chipbox.coverart.real

import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.IOException

/**
 * OkHttp-backed [CoverArtHttp] for the JVM and Android targets — the cover-art subsystem's single
 * point of contact with the network (everything else is commonMain). Construct it with a shared
 * [OkHttpClient]; the caller owns that client and is responsible for shutting it down.
 *
 * `okio.IOException` is `java.io.IOException` on the JVM, so the [IOException]s thrown here (and the
 * ones OkHttp raises on transport failure) are exactly what the commonMain logic catches.
 */
class OkHttpCoverArtHttp(private val client: OkHttpClient) : CoverArtHttp {
    override fun post(url: String, headers: Map<String, String>, contentType: String, body: String): String {
        val builder = Request.Builder().url(url).post(body.toRequestBody(contentType.toMediaType()))
        headers.forEach { (name, value) -> builder.header(name, value) }
        return execute(builder.build())
    }

    override fun postForm(url: String, fields: Map<String, String>): String {
        val form = FormBody.Builder().apply { fields.forEach { (key, value) -> add(key, value) } }.build()
        return execute(Request.Builder().url(url).post(form).build())
    }

    override fun getBytes(url: String): ByteArray {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Image download failed: ${response.code}")
            return response.body.bytes()
        }
    }

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { response ->
            val payload = response.body.string()
            if (!response.isSuccessful) throw IOException("Request to ${request.url} failed: ${response.code}: $payload")
            return payload
        }
    }
}
