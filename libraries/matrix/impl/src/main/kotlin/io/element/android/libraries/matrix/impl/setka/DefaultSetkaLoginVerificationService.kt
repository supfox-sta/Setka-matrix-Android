/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.impl.setka

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.setka.SetkaLoginRecoveryBackupResult
import io.element.android.libraries.matrix.api.setka.SetkaLoginRecoveryDeleteResult
import io.element.android.libraries.matrix.api.setka.SetkaLoginRecoveryStoreResult
import io.element.android.libraries.matrix.api.setka.SetkaLoginVerificationEmailResult
import io.element.android.libraries.matrix.api.setka.SetkaLoginVerificationFallbackMethod
import io.element.android.libraries.matrix.api.setka.SetkaLoginVerificationResult
import io.element.android.libraries.matrix.api.setka.SetkaLoginVerificationService
import io.element.android.libraries.matrix.api.setka.SetkaLoginVerificationStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@ContributesBinding(SessionScope::class)
@SingleIn(SessionScope::class)
class DefaultSetkaLoginVerificationService(
    private val matrixClient: MatrixClient,
) : SetkaLoginVerificationService {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override suspend fun getStatus(): Result<SetkaLoginVerificationStatus> {
        return requestBody<StatusResponse>(
            method = "POST",
            path = "/setka/login/status",
            body = "{}",
        ).map { it.toDomain() }
    }

    override suspend fun sendCodeToEmail(email: String?): Result<SetkaLoginVerificationEmailResult> {
        val body = if (email.isNullOrBlank()) {
            "{}"
        } else {
            json.encodeToString(
                buildJsonObject {
                    put("email", email.trim())
                }
            )
        }
        return requestBody<SendEmailResponse>(
            method = "POST",
            path = "/setka/login/send_email",
            body = body,
        ).map { it.toDomain() }
    }

    override suspend fun verifyCode(code: String): Result<SetkaLoginVerificationResult> {
        val body = json.encodeToString(
            buildJsonObject {
                put("code", code)
            }
        )
        return requestBody<ResultResponse>(
            method = "POST",
            path = "/setka/login/verify",
            body = body,
        ).map { it.toDomain() }
    }

    override suspend fun completeFallback(
        method: SetkaLoginVerificationFallbackMethod,
        recoveryKey: String?,
    ): Result<SetkaLoginVerificationResult> {
        val rawMethod = when (method) {
            SetkaLoginVerificationFallbackMethod.EMOJI -> "emoji"
            SetkaLoginVerificationFallbackMethod.RECOVERY_KEY -> "recovery_key"
        }
        val body = json.encodeToString(
            buildJsonObject {
                put("method", rawMethod)
                recoveryKey?.trim()?.takeIf { it.isNotEmpty() }?.let { put("recovery_key", it) }
            }
        )
        return requestBody<ResultResponse>(
            method = "POST",
            path = "/setka/login/complete_fallback",
            body = body,
        ).map { it.toDomain() }
    }

    override suspend fun storeRecoveryKey(recoveryKey: String): Result<SetkaLoginRecoveryStoreResult> {
        val body = json.encodeToString(
            buildJsonObject {
                put("recovery_key", recoveryKey)
            }
        )
        return requestBody<StoreRecoveryResponse>(
            method = "POST",
            path = "/setka/login/recovery/store",
            body = body,
        ).map { it.toDomain() }
    }

    override suspend fun fetchRecoveryKey(): Result<SetkaLoginRecoveryBackupResult> {
        return requestBody<FetchRecoveryResponse>(
            method = "POST",
            path = "/setka/login/recovery/fetch",
            body = "{}",
        ).map { it.toDomain() }
    }

    override suspend fun deleteRecoveryKey(): Result<SetkaLoginRecoveryDeleteResult> {
        return requestBody<DeleteRecoveryResponse>(
            method = "POST",
            path = "/setka/login/recovery/delete",
            body = "{}",
        ).map { it.toDomain() }
    }

    private suspend inline fun <reified T> requestBody(
        method: String,
        path: String,
        body: String,
    ): Result<T> {
        return matrixClient.executeAuthenticatedRequest(
            method = method,
            path = "/_matrix/client/v3$path",
            body = body.encodeToByteArray(),
        ).mapCatching { bytes ->
            json.decodeFromString<T>(bytes.decodeToString())
        }
    }
}

@Serializable
private data class StatusResponse(
    val required: Boolean = false,
    val verified: Boolean = false,
    val ip: String = "",
    val region: String = "",
    @SerialName("expires_at") val expiresAt: Long = 0,
    @SerialName("email_available") val emailAvailable: Boolean = false,
    @SerialName("email_hint") val emailHint: String = "",
    @SerialName("recovery_backup_available") val recoveryBackupAvailable: Boolean = false,
) {
    fun toDomain(): SetkaLoginVerificationStatus {
        return SetkaLoginVerificationStatus(
            required = required,
            verified = verified,
            ip = ip,
            region = region,
            expiresAt = expiresAt,
            emailAvailable = emailAvailable,
            emailHint = emailHint,
            recoveryBackupAvailable = recoveryBackupAvailable,
        )
    }
}

@Serializable
private data class SendEmailResponse(
    val sent: Boolean = false,
    val email: String = "",
) {
    fun toDomain(): SetkaLoginVerificationEmailResult {
        return SetkaLoginVerificationEmailResult(
            sent = sent,
            email = email,
        )
    }
}

@Serializable
private data class ResultResponse(
    val verified: Boolean = false,
    val method: String = "",
    @SerialName("recovery_backup_available") val recoveryBackupAvailable: Boolean = false,
) {
    fun toDomain(): SetkaLoginVerificationResult {
        return SetkaLoginVerificationResult(
            verified = verified,
            method = method,
            recoveryBackupAvailable = recoveryBackupAvailable,
        )
    }
}

@Serializable
private data class StoreRecoveryResponse(
    val stored: Boolean = false,
) {
    fun toDomain(): SetkaLoginRecoveryStoreResult {
        return SetkaLoginRecoveryStoreResult(stored = stored)
    }
}

@Serializable
private data class FetchRecoveryResponse(
    val available: Boolean = false,
    @SerialName("recovery_key") val recoveryKey: String = "",
) {
    fun toDomain(): SetkaLoginRecoveryBackupResult {
        return SetkaLoginRecoveryBackupResult(
            available = available,
            recoveryKey = recoveryKey,
        )
    }
}

@Serializable
private data class DeleteRecoveryResponse(
    val deleted: Boolean = false,
) {
    fun toDomain(): SetkaLoginRecoveryDeleteResult {
        return SetkaLoginRecoveryDeleteResult(deleted = deleted)
    }
}
