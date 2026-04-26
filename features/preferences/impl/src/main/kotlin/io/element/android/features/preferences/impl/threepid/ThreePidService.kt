package io.element.android.features.preferences.impl.threepid

import dev.zacsweers.metro.Inject
import android.net.Uri
import io.element.android.libraries.matrix.api.MatrixClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@Inject
class ThreePidService(
    private val matrixClient: MatrixClient,
) {
    suspend fun getSetkaPrivacy(): Result<SetkaPrivacy> {
        val encodedUserId = Uri.encode(matrixClient.sessionId.value)
        return matrixClient.executeAuthenticatedRequest(
            method = "GET",
            path = "/_matrix/client/v3/user/$encodedUserId/setka_privacy",
        ).mapCatching { bytes ->
            val json = JSONObject(bytes.decodeToString())
            SetkaPrivacy(
                discoverableEmail = json.optBoolean("discoverable_email", true),
                discoverableMsisdn = json.optBoolean("discoverable_msisdn", true),
                emailVisibility = SetkaVisibility.fromRaw(json.optString("email_visibility")),
                phoneVisibility = SetkaVisibility.fromRaw(json.optString("phone_visibility")),
                lastSeenVisibility = SetkaVisibility.fromRaw(json.optString("last_seen_visibility")),
            )
        }
    }

    suspend fun setSetkaPrivacy(
        discoverableEmail: Boolean? = null,
        discoverableMsisdn: Boolean? = null,
        emailVisibility: SetkaVisibility? = null,
        phoneVisibility: SetkaVisibility? = null,
        lastSeenVisibility: SetkaVisibility? = null,
    ): Result<SetkaPrivacy> {
        val encodedUserId = Uri.encode(matrixClient.sessionId.value)
        val body = JSONObject().apply {
            discoverableEmail?.let { put("discoverable_email", it) }
            discoverableMsisdn?.let { put("discoverable_msisdn", it) }
            emailVisibility?.let { put("email_visibility", it.raw) }
            phoneVisibility?.let { put("phone_visibility", it.raw) }
            lastSeenVisibility?.let { put("last_seen_visibility", it.raw) }
        }.toString().encodeToByteArray()
        return matrixClient.executeAuthenticatedRequest(
            method = "PUT",
            path = "/_matrix/client/v3/user/$encodedUserId/setka_privacy",
            body = body,
        ).mapCatching { bytes ->
            val json = JSONObject(bytes.decodeToString())
            SetkaPrivacy(
                discoverableEmail = json.optBoolean("discoverable_email", true),
                discoverableMsisdn = json.optBoolean("discoverable_msisdn", true),
                emailVisibility = SetkaVisibility.fromRaw(json.optString("email_visibility")),
                phoneVisibility = SetkaVisibility.fromRaw(json.optString("phone_visibility")),
                lastSeenVisibility = SetkaVisibility.fromRaw(json.optString("last_seen_visibility")),
            )
        }
    }

    suspend fun lookupMxidByThreePid(
        medium: ThreePidMedium,
        address: String,
    ): Result<String?> {
        val body = JSONObject()
            .put("medium", medium.raw)
            .put("address", address)
            .toString()
            .encodeToByteArray()
        return matrixClient.executeAuthenticatedRequest(
            method = "POST",
            path = "/_matrix/client/v3/setka/3pid/lookup",
            body = body,
        ).mapCatching { bytes ->
            val json = JSONObject(bytes.decodeToString())
            json.optString("mxid").trim().takeIf { it.isNotBlank() }
        }
    }
    suspend fun getThreePids(): Result<List<ThreePid>> {
        return matrixClient.executeAuthenticatedRequest(
            method = "GET",
            path = "/_matrix/client/v3/account/3pid",
        ).mapCatching { bytes ->
            val json = JSONObject(bytes.decodeToString())
            val threepids = json.optJSONArray("threepids") ?: JSONArray()
            buildList {
                for (i in 0 until threepids.length()) {
                    val obj = threepids.optJSONObject(i) ?: continue
                    val medium = ThreePidMedium.fromRaw(obj.optString("medium"))
                        ?: continue
                    val address = obj.optString("address").trim()
                    if (address.isBlank()) continue
                    add(
                        ThreePid(
                            medium = medium,
                            address = address,
                            addedAtMillis = obj.optLong("added_at", -1L).takeIf { it > 0 },
                            validatedAtMillis = obj.optLong("validated_at", -1L).takeIf { it > 0 },
                        )
                    )
                }
            }
        }
    }

    suspend fun requestEmailToken(
        email: String,
        sendAttempt: Int,
        clientSecret: String = randomClientSecret(),
        nextLink: String? = null,
    ): Result<Pair<String /*clientSecret*/, ThreePidRequestTokenResult>> {
        val body = JSONObject()
            .put("client_secret", clientSecret)
            .put("email", email)
            .put("send_attempt", sendAttempt)
        if (!nextLink.isNullOrBlank()) body.put("next_link", nextLink)
        return matrixClient.executeAuthenticatedRequest(
            method = "POST",
            path = "/_matrix/client/v3/account/3pid/email/requestToken",
            body = body.toString().encodeToByteArray(),
        ).mapCatching { bytes ->
            val json = JSONObject(bytes.decodeToString())
            val sid = json.optString("sid").trim()
            require(sid.isNotBlank()) { "Missing sid" }
            clientSecret to ThreePidRequestTokenResult(sid = sid)
        }
    }

    suspend fun requestMsisdnToken(
        country: String,
        phoneNumber: String,
        sendAttempt: Int,
        clientSecret: String = randomClientSecret(),
        nextLink: String? = null,
    ): Result<Pair<String /*clientSecret*/, ThreePidRequestTokenResult>> {
        // Setka-specific: request the verification code via email (not SMS),
        // but still bind the 3PID as medium=msisdn so lookups by phone work.
        // We keep the same client API shape for minimal UI changes.
        val body = JSONObject()
            .put("client_secret", clientSecret)
            .put("phone_number", phoneNumber)
            .put("send_attempt", sendAttempt)
        if (!nextLink.isNullOrBlank()) body.put("next_link", nextLink)
        // country is accepted by the UI but not required by the server flow.
        if (country.isNotBlank()) body.put("country", country)
        return matrixClient.executeAuthenticatedRequest(
            method = "POST",
            path = "/_matrix/client/v3/setka/3pid/msisdn/requestTokenEmail",
            body = body.toString().encodeToByteArray(),
        ).mapCatching { bytes ->
            val json = JSONObject(bytes.decodeToString())
            val sid = json.optString("sid").trim()
            require(sid.isNotBlank()) { "Missing sid" }
            clientSecret to ThreePidRequestTokenResult(
                sid = sid,
                msisdn = json.optString("msisdn").trim().takeIf { it.isNotBlank() },
            )
        }
    }

    suspend fun submitMsisdnTokenUnstable(
        sid: String,
        clientSecret: String,
        token: String,
    ): Result<Unit> {
        // Setka-specific: submit the email-delivered token and bind the msisdn locally.
        val body = JSONObject()
            .put("client_secret", clientSecret)
            .put("sid", sid)
            .put("token", token)
        return matrixClient.executeAuthenticatedRequest(
            method = "POST",
            path = "/_matrix/client/v3/setka/3pid/msisdn/submit_token",
            body = body.toString().encodeToByteArray(),
        ).map { Unit }
    }

    suspend fun addThreePidWithPassword(
        sid: String,
        clientSecret: String,
        password: String,
    ): Result<Unit> {
        // First, hit the endpoint without auth to get a UIA session.
        val initial = matrixClient.executeAuthenticatedRequest(
            method = "POST",
            path = "/_matrix/client/v3/account/3pid/add",
            body = JSONObject()
                .put("sid", sid)
                .put("client_secret", clientSecret)
                .toString()
                .encodeToByteArray(),
        )
        val uiaSession = initial.fold(
            onSuccess = { return Result.success(Unit) },
            onFailure = { throwable ->
                parseUiaSessionFromThrowable(throwable)
            }
        ) ?: return Result.failure(IllegalStateException("UIA session not provided by server"))

        val auth = JSONObject()
            .put("type", "m.login.password")
            .put(
                "identifier",
                JSONObject()
                    .put("type", "m.id.user")
                    .put("user", matrixClient.sessionId.value)
            )
            .put("password", password)
            .put("session", uiaSession.session)

        val body = JSONObject()
            .put("sid", sid)
            .put("client_secret", clientSecret)
            .put("auth", auth)
            .toString()
            .encodeToByteArray()

        return matrixClient.executeAuthenticatedRequest(
            method = "POST",
            path = "/_matrix/client/v3/account/3pid/add",
            body = body,
        ).map { Unit }
    }

    suspend fun deleteThreePid(
        medium: ThreePidMedium,
        address: String,
    ): Result<Unit> {
        val body = JSONObject()
            .put("medium", medium.raw)
            .put("address", address)
            .toString()
            .encodeToByteArray()
        val setkaResult = matrixClient.executeAuthenticatedRequest(
            method = "POST",
            // Setka extension: this endpoint is guaranteed to be available on our server
            // even when upstream 3PID changes are disabled or proxied.
            path = "/_matrix/client/v3/setka/3pid/delete",
            body = body,
        ).map { Unit }

        // Some deployments may not have the Setka extension enabled yet (or may route client APIs
        // differently). If we get a 404, fall back to the standard Matrix endpoint.
        if (setkaResult.isSuccess) return setkaResult
        val message = setkaResult.exceptionOrNull()?.message.orEmpty()
        return if (message.contains("HTTP 404") || message.contains("404", ignoreCase = true)) {
            matrixClient.executeAuthenticatedRequest(
                method = "POST",
                path = "/_matrix/client/v3/account/3pid/delete",
                body = body,
            ).map { Unit }
        } else {
            setkaResult
        }
    }

    private fun parseUiaSessionFromThrowable(throwable: Throwable): ThreePidUiaSession? {
        val message = throwable.message ?: return null
        // RustMatrixClient formats: "Request failed (METHOD PATH): HTTP <code> <details>"
        val idx = message.indexOf("HTTP ")
        if (idx == -1) return null
        val afterHttp = message.substring(idx + 5)
        val spaceIdx = afterHttp.indexOf(' ')
        if (spaceIdx == -1) return null
        val code = afterHttp.substring(0, spaceIdx).toIntOrNull() ?: return null
        if (code != 401) return null
        val details = afterHttp.substring(spaceIdx + 1).trim()
        if (details.isBlank() || details == "<empty>") return null
        val json = runCatching { JSONObject(details) }.getOrNull() ?: return null
        val session = json.optString("session").trim()
        if (session.isBlank()) return null
        return ThreePidUiaSession(session = session)
    }

    private fun randomClientSecret(): String {
        return UUID.randomUUID().toString().replace("-", "")
    }
}
