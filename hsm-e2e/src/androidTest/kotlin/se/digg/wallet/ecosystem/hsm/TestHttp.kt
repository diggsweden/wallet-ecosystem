// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem.hsm

import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Test-only HTTP client; trusts any TLS cert so the emulator can reach the ecosystem. */
internal object TestHttp {

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val trustAll = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .sslSocketFactory(
            SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(trustAll), java.security.SecureRandom())
            }.socketFactory,
            trustAll,
        )
        .hostnameVerifier { _, _ -> true }
        .build()

    fun postJson(url: String, json: String, headers: Map<String, String> = emptyMap()): String = exec(
        request(url, headers).post(json.toRequestBody(JSON)).build()
    )

    fun get(url: String, headers: Map<String, String> = emptyMap()): String =
        exec(request(url, headers).get().build())

    private fun request(url: String, headers: Map<String, String>): Request.Builder =
        Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }

    private fun exec(request: Request): String = client.newCall(request).execute().use { r ->
        val body = r.body?.string().orEmpty()
        if (!r.isSuccessful) {
            throw java.io.IOException("HTTP ${r.code} from ${request.url}: $body")
        }
        body
    }
}
