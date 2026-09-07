// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem.hsm

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jose.util.Base64URL
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import se.digg.wallet.access_mechanism.api.OpaqueClient
import se.digg.wallet.access_mechanism.model.KeyInfo

/**
 * End-to-end check of the wallet HSM key operations: real [OpaqueClient] through
 * the real wallet-client-gateway against a live `just up` ecosystem. Unreachable
 * gateway ⇒ tests skip (`Assume`), not fail. Base URL: `gatewayBaseUrl` arg,
 * default `http://10.0.2.2:8082/wallet-client-gateway`.
 *
 * The worker allows one mutating op (`sign`/`createHsmKey`/`deleteHsmKey`) per
 * OPAQUE session, hence a fresh `authenticate` before each; the gateway `SESSION`
 * is opened once, in [registeredClient].
 */
@RunWith(AndroidJUnit4::class)
class HsmOperationsE2ETest {

    private val baseUrl: String =
        InstrumentationRegistry.getArguments().getString("gatewayBaseUrl")
            ?: "http://10.0.2.2:8082/wallet-client-gateway"

    private val pin = "1234" // consumed locally by OPAQUE; never sent to the server

    @Test
    fun registers_authenticates_and_lists_the_auto_provisioned_key() = e2e {
        val client = registeredClient()
        client.authenticate(pin)

        val keys = client.listHsmKeys()
        assertTrue("fresh device is auto-provisioned with an HSM key", keys.isNotEmpty())
        keys.forEach { assertEquals("P-256", it.publicKey.toECKey().curve.name) }
    }

    @Test
    fun signs_raw_bytes_and_verifies_against_the_hsm_key() = e2e {
        val client = registeredClient()
        client.authenticate(pin)

        val key = client.listHsmKeys().first()
        val data = "wallet-ecosystem hsm e2e".toByteArray()
        val sig = client.sign(key.publicKey.keyID, data)

        assertTrue("raw ECDSA signature verifies", verifyEcdsa(key, data, sig.signature))
    }

    /**
     * Builds an HSM-backed JWS the way wallet-app-android does
     * (`JwtUtils.signJwtWith`): raw [OpaqueClient.sign] on the signing input, then
     * append the signature. Not [OpaqueClient.signJws] — that assumes DER and is
     * broken against the current HSM's P1363 output (wallet-r2ps 23173855c).
     */
    @Test
    fun app_style_jws_signed_via_raw_sign_verifies_against_the_hsm_key() = e2e {
        val client = registeredClient()
        client.authenticate(pin)

        val key = client.listHsmKeys().first()
        val header = JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.publicKey.keyID).build()
        val signingInput = "${header.toBase64URL()}.${Base64URL.encode("""{"iss":"hsm-e2e"}""")}"

        val signature = client.sign(key.publicKey.keyID, signingInput.toByteArray(Charsets.US_ASCII))
        val jws = "$signingInput.${signature.signature}"

        assertTrue(
            "app-style JWS (raw sign + manual assembly) verifies against the HSM key",
            JWSObject.parse(jws).verify(ECDSAVerifier(key.publicKey.toECKey())),
        )
    }

    @Test
    fun creates_then_deletes_an_hsm_key() = e2e {
        val client = registeredClient()

        client.authenticate(pin)
        val before = client.listHsmKeys().map { it.publicKey.keyID }.toSet()
        client.createHsmKey()

        client.authenticate(pin)
        val created = client.listHsmKeys().map { it.publicKey.keyID }.toSet() - before
        assertEquals("exactly one key added", 1, created.size)
        val kid = created.first()

        client.authenticate(pin)
        client.deleteHsmKey(kid)

        client.authenticate(pin)
        assertFalse(
            "deleted key is gone",
            client.listHsmKeys().any { it.publicKey.keyID == kid },
        )
    }

    // --- helpers ---

    private suspend fun registeredClient(): OpaqueClient {
        // account/session key — random kid, wallet-account requires it unique per account
        val deviceKey = ECKeyGenerator(Curve.P_256).keyID(UUID.randomUUID().toString()).generate()
        val transport = GatewayOpaqueTransport(baseUrl, deviceKey)
        transport.enroll()

        // separate OPAQUE client key pair (backs the JOSE envelope)
        val client = OpaqueClient.create(
            clientKeyPair = p256(),
            pinStretchPrivateKey = p256().private,
            transport = transport,
        )
        client.registration(pin)
        return client
    }

    private fun p256() = KeyPairGenerator.getInstance("EC")
        .apply { initialize(ECGenParameterSpec("secp256r1")) }
        .generateKeyPair()

    // HSM returns a 64-byte P1363 (R||S) signature; SHA256withECDSA wants DER.
    private fun verifyEcdsa(key: KeyInfo, data: ByteArray, signatureB64: String): Boolean {
        val sig = decodeB64(signatureB64)
        val der = if (sig.size == 64) p1363ToDer(sig) else sig
        return Signature.getInstance("SHA256withECDSA").run {
            initVerify(key.publicKey.toECKey().toECPublicKey())
            update(data)
            verify(der)
        }
    }

    private fun decodeB64(s: String): ByteArray =
        runCatching { Base64.getDecoder().decode(s) }
            .getOrElse { Base64.getUrlDecoder().decode(s) }

    private fun p1363ToDer(rs: ByteArray): ByteArray {
        fun asn1Int(raw: ByteArray): ByteArray {
            var i = 0
            while (i < raw.size - 1 && raw[i].toInt() == 0) i++
            var v = raw.copyOfRange(i, raw.size)
            if (v[0].toInt() and 0x80 != 0) v = byteArrayOf(0) + v
            return byteArrayOf(0x02, v.size.toByte()) + v
        }
        val body = asn1Int(rs.copyOfRange(0, 32)) + asn1Int(rs.copyOfRange(32, 64))
        return byteArrayOf(0x30, body.size.toByte()) + body
    }

    // run [block]; turn "ecosystem not up" failures into a skip
    private fun e2e(block: suspend () -> Unit) = runBlocking {
        try {
            block()
        } catch (e: ConnectException) {
            Assume.assumeNoException("gateway unreachable at $baseUrl — skipping", e)
        } catch (e: SocketTimeoutException) {
            Assume.assumeNoException("gateway timed out at $baseUrl — skipping", e)
        } catch (e: UnknownHostException) {
            Assume.assumeNoException("host unresolved for $baseUrl — skipping", e)
        } catch (e: IOException) {
            val down = e.message?.let {
                it.contains("Failed to connect") || it.contains("Connection refused") ||
                    it.contains("HTTP 502") || it.contains("HTTP 503") || it.contains("HTTP 504")
            } ?: false
            if (down) {
                Assume.assumeNoException("gateway not ready at $baseUrl — skipping", e)
            } else {
                throw e
            }
        }
    }
}
