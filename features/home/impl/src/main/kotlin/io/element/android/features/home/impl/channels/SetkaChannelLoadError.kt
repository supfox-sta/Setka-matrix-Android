/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.home.impl.channels

/**
 * Diagnostic codes shown in the channels tab when loading fails.
 *
 * E-01 — Setka channels API not deployed (HTTP 404)
 * E-02 — Auth failed (HTTP 401/403)
 * E-03 — Homeserver error (HTTP 5xx)
 * E-04 — Other HTTP error from Setka API
 * E-05 — Network / connection failure
 * E-06 — Invalid JSON response
 * E-07 — publicRooms fallback also failed
 * E-99 — Unknown error
 */
enum class SetkaChannelLoadError(val code: String) {
    ENDPOINT_NOT_FOUND("E-01"),
    AUTH_FAILED("E-02"),
    SERVER_ERROR("E-03"),
    CLIENT_HTTP("E-04"),
    NETWORK("E-05"),
    PARSE("E-06"),
    FALLBACK_FAILED("E-07"),
    UNKNOWN("E-99"),
    ;

    companion object {
        fun fromThrowable(throwable: Throwable, afterFallback: Boolean = false): SetkaChannelLoadError {
            if (afterFallback) return FALLBACK_FAILED
            val message = throwable.message.orEmpty()
            val httpCode = HTTP_CODE_PATTERN.find(message)?.groupValues?.get(1)?.toIntOrNull()
            return when {
                httpCode == 404 -> ENDPOINT_NOT_FOUND
                httpCode == 401 || httpCode == 403 -> AUTH_FAILED
                httpCode != null && httpCode >= 500 -> SERVER_ERROR
                httpCode != null -> CLIENT_HTTP
                message.contains("Unable to resolve host", ignoreCase = true) ||
                    message.contains("Failed to connect", ignoreCase = true) ||
                    message.contains("timeout", ignoreCase = true) ||
                    message.contains("UnknownHostException", ignoreCase = true) -> NETWORK
                message.contains("JSON", ignoreCase = true) ||
                    throwable is org.json.JSONException -> PARSE
                else -> UNKNOWN
            }
        }
    }
}

private val HTTP_CODE_PATTERN = Regex("HTTP (\\d{3})")

class SetkaChannelLoadException(
    val error: SetkaChannelLoadError,
    cause: Throwable? = null,
    val primaryError: SetkaChannelLoadError? = null,
) : Exception(error.code, cause) {
    val displayCode: String
        get() = if (error == SetkaChannelLoadError.FALLBACK_FAILED && primaryError != null) {
            "${error.code} (${primaryError.code})"
        } else {
            error.code
        }
}

data class SetkaChannelLoadFailure(
    val error: SetkaChannelLoadError,
    val displayCode: String,
)

fun Throwable.toSetkaChannelLoadError(afterFallback: Boolean = false): SetkaChannelLoadError =
    (this as? SetkaChannelLoadException)?.error
        ?: SetkaChannelLoadError.fromThrowable(this, afterFallback = afterFallback)

fun Throwable.toSetkaChannelLoadFailure(): SetkaChannelLoadFailure {
    val exception = this as? SetkaChannelLoadException
    val error = exception?.error ?: toSetkaChannelLoadError()
    val displayCode = exception?.displayCode ?: error.code
    return SetkaChannelLoadFailure(error = error, displayCode = displayCode)
}
