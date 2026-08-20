/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.deeplink.impl

import android.content.Intent
import android.net.Uri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.appconfig.SetkaMatrixConfig
import io.element.android.libraries.androidutils.text.urlDecoded
import io.element.android.libraries.deeplink.api.DeeplinkData
import io.element.android.libraries.deeplink.api.DeeplinkParser
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.core.ThreadId
import io.element.android.libraries.matrix.api.setka.SetkaPlusPaymentReturnOutcome

@ContributesBinding(AppScope::class)
class DefaultDeeplinkParser : DeeplinkParser {
    override fun getFromIntent(intent: Intent): DeeplinkData? {
        return intent
            .takeIf { it.action == Intent.ACTION_VIEW }
            ?.data
            ?.toDeeplinkData()
    }

    private fun Uri.toDeeplinkData(): DeeplinkData? {
        parseSetkaPaymentReturn()?.let { return it }
        if (scheme != SCHEME) return null
        if (host != HOST) return null
        val pathBits = encodedPath.orEmpty().split("/").drop(1).map { it.urlDecoded() }
        val sessionId = pathBits.elementAtOrNull(0)?.let(::SessionId) ?: return null

        return when (val screenPathComponent = pathBits.elementAtOrNull(1)) {
            null -> DeeplinkData.Root(sessionId)
            SETKA_PLUS_PATH -> parseSetkaPlusPaymentReturn(sessionId, pathBits)
            else -> {
                val roomId = screenPathComponent.let(::RoomId)
                val threadId = pathBits.elementAtOrNull(2)?.takeIf { it.isNotBlank() }?.let(::ThreadId)
                val eventId = pathBits.elementAtOrNull(3)?.takeIf { it.isNotBlank() }?.let(::EventId)
                DeeplinkData.Room(sessionId, roomId, threadId, eventId)
            }
        }
    }

    private fun Uri.parseSetkaPaymentReturn(): DeeplinkData.SetkaPlusPaymentReturn? {
        if (scheme != "https") return null
        if (!SetkaMatrixConfig.isSetkaPaymentReturnHost(host.orEmpty())) return null
        val segments = encodedPath.orEmpty().split("/").filter { it.isNotBlank() }
        if (segments.size < 4) return null
        if (segments[0] != "setka" || segments[2] != "return") return null
        if (segments[1] != "payanyway" && segments[1] != "robokassa") return null
        val outcome = when (segments[3].lowercase()) {
            "success" -> SetkaPlusPaymentReturnOutcome.SUCCESS
            "fail", "failed" -> SetkaPlusPaymentReturnOutcome.FAIL
            else -> SetkaPlusPaymentReturnOutcome.UNKNOWN
        }
        val paymentId = getQueryParameter("payment_id")?.trim().orEmpty()
        if (paymentId.isBlank()) return null
        val sessionIdValue = getQueryParameter("user_id")?.trim().orEmpty()
        if (sessionIdValue.isBlank()) return null
        return DeeplinkData.SetkaPlusPaymentReturn(
            sessionId = SessionId(sessionIdValue),
            paymentId = paymentId,
            outcome = outcome,
        )
    }

    private fun Uri.parseSetkaPlusPaymentReturn(
        sessionId: SessionId,
        pathBits: List<String>,
    ): DeeplinkData.SetkaPlusPaymentReturn? {
        if (pathBits.elementAtOrNull(1) != SETKA_PLUS_PATH) return null
        if (pathBits.elementAtOrNull(2) != "payment") return null
        val outcome = when (pathBits.elementAtOrNull(3)?.lowercase()) {
            "success" -> SetkaPlusPaymentReturnOutcome.SUCCESS
            "fail", "failed" -> SetkaPlusPaymentReturnOutcome.FAIL
            else -> SetkaPlusPaymentReturnOutcome.UNKNOWN
        }
        val paymentId = getQueryParameter("payment_id")?.trim().orEmpty()
        if (paymentId.isBlank()) return null
        return DeeplinkData.SetkaPlusPaymentReturn(
            sessionId = sessionId,
            paymentId = paymentId,
            outcome = outcome,
        )
    }

    private companion object {
        const val SETKA_PLUS_PATH = "setka-plus"
    }
}
