// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem.hsm

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.io.IOException
import java.security.interfaces.ECPublicKey
import java.util.Date
import java.util.concurrent.ThreadLocalRandom
import kotlinx.coroutines.delay
import org.json.JSONObject
import se.digg.wallet.access_mechanism.api.HSMOperationType
import se.digg.wallet.access_mechanism.api.OpaqueTransport
import se.digg.wallet.access_mechanism.model.HSMRequest
import se.digg.wallet.access_mechanism.model.StateResponse

/**
 * [OpaqueTransport] over the real wallet-client-gateway (`X-API-KEY` + a
 * challenge-response `SESSION`), the way wallet-app-android talks to it.
 * [enroll] once before use: create an account, then challenge/response for a
 * session. [deviceKey] is the account/session key, separate from OpaqueClient's
 * `clientKeyPair`.
 */
class GatewayOpaqueTransport(
    private val baseUrl: String,
    private val deviceKey: ECKey,
    private val apiKey: String = "apikey",
) : OpaqueTransport {

    private lateinit var session: String

    fun enroll() {
        val accountId = createAccount()
        val nonce = initChallenge(accountId, deviceKey.keyID)
        session = respondToChallenge(signChallenge(nonce))
    }

    private fun createAccount(): String {
        val body = JSONObject()
            .put("personalIdentityNumber", randomPersonalId())
            .put("emailAdress", "hsm-e2e@example.test")
            .put("telephoneNumber", "0701234567")
            .put("deviceKey", JSONObject(deviceKey.toPublicJWK().toJSONString()))
        val dto = JSONObject(
            TestHttp.postJson(url("v0/accounts"), body.toString(), mapOf(API_KEY to apiKey)),
        )
        return dto.getString("accountId")
    }

    private fun initChallenge(accountId: String, keyId: String): String =
        JSONObject(
            TestHttp.get(url("public/auth/session/challenge?accountId=$accountId&keyId=$keyId")),
        ).getString("nonce")

    private fun signChallenge(nonce: String): String {
        val jwt = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.ES256).keyID(deviceKey.keyID).build(),
            JWTClaimsSet.Builder()
                .claim("nonce", nonce)
                .expirationTime(Date(System.currentTimeMillis() + 60_000))
                .build(),
        )
        jwt.sign(ECDSASigner(deviceKey))
        return jwt.serialize()
    }

    private fun respondToChallenge(signedJwt: String): String =
        JSONObject(
            TestHttp.postJson(
                url("public/auth/session/response"),
                JSONObject().put("signedJwt", signedJwt).toString(),
            ),
        ).getString("sessionId")

    private fun authHeaders() = mapOf(API_KEY to apiKey, SESSION to session)

    override suspend fun registerState(
        publicKey: ECPublicKey,
        overwrite: Boolean,
        ttl: String?,
    ): StateResponse {
        val jwk = ECKey.Builder(Curve.P_256, publicKey).build()
        val kid = jwk.computeThumbprint().toString()
        val deviceKeyJson = JSONObject(jwk.toPublicJWK().toJSONString()).put("kid", kid)

        val body = JSONObject()
            .put("deviceKey", deviceKeyJson)
            .apply { if (ttl != null) put("ttl", ttl) }

        val dto = JSONObject(
            TestHttp.postJson(url("hsm/v0/device-states"), body.toString(), authHeaders()),
        )

        return StateResponse(
            status = dto.getString("status"),
            devAuthorizationCode = dto.optString("devAuthorizationCode", ""),
            serverJwsPublicKey = dto.optJSONObject("serverJwsPublicKey")
                ?.let { JWK.parse(it.toString()) },
            opaqueServerId = dto.optString("opaqueServerId", ""),
            clientId = dto.optString("clientId").ifEmpty { null },
        )
    }

    override suspend fun perform(request: HSMRequest, operation: HSMOperationType): String {
        val query = "?type=${gatewayType(operation)}"
        val body = JSONObject().put("outerRequestJws", request.outerRequestJws)

        var dto = JSONObject(
            TestHttp.postJson(url("hsm/v0/requests$query"), body.toString(), authHeaders()),
        )

        repeat(30) {
            when (dto.optString("status")) {
                "COMPLETE" -> return dto.getString("result")
                "ERROR" -> throw IOException("HSM $operation failed: ${dto.opt("result")}")
                else -> {
                    delay(1_000)
                    dto = JSONObject(
                        TestHttp.get(url("hsm/v0/requests/${dto.getString("id")}"), authHeaders()),
                    )
                }
            }
        }
        throw IOException("HSM $operation timed out waiting for a synchronous result")
    }

    // access-mechanism op -> gateway HsmRequestType query param (traceability only)
    private fun gatewayType(op: HSMOperationType): String = when (op) {
        HSMOperationType.CREATE_SESSION -> "CREATE_SESSION"
        HSMOperationType.CREATE_KEY -> "CREATE_KEY"
        HSMOperationType.LIST_KEYS -> "LIST_KEYS"
        HSMOperationType.DELETE_KEY -> "DELETE_KEY"
        HSMOperationType.REGISTER_PIN -> "REGISTER_PIN"
        HSMOperationType.CHANGE_PIN -> "CHANGE_PIN"
        HSMOperationType.SIGN -> "SIGN"
    }

    private fun url(path: String): String = baseUrl.trimEnd('/') + "/" + path

    private companion object {
        const val API_KEY = "X-API-KEY"
        const val SESSION = "SESSION"

        fun randomPersonalId(): String =
            ThreadLocalRandom.current().nextLong(100_000_000_000L, 1_000_000_000_000L).toString()
    }
}
