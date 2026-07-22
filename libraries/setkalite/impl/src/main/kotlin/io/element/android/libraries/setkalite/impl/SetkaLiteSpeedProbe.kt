/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.setkalite.impl

import io.element.android.libraries.setkalite.api.SetkaLiteSpeedProbeResult
import dev.zacsweers.metro.Inject
import kotlin.math.roundToInt

class SetkaLiteSpeedProbe @Inject constructor(
    private val apiClient: SetkaLiteApiClient,
) {
    suspend fun measure(): SetkaLiteSpeedProbeResult? {
        val rttMs = apiClient.ping() ?: return null
        val probeBytes = PROBE_SIZE_BYTES
        val start = System.currentTimeMillis()
        val payload = apiClient.downloadProbe(probeBytes) ?: return null
        val elapsedMs = (System.currentTimeMillis() - start).coerceAtLeast(1L)
        val throughputKbps = ((payload.size.coerceAtMost(probeBytes) * 8.0) / elapsedMs).roundToInt()
        return SetkaLiteSpeedProbeResult(
            rttMs = rttMs,
            throughputKbps = throughputKbps,
            suggestedLiteMode = throughputKbps < SUGGEST_LITE_KBPS || rttMs > SUGGEST_LITE_RTT_MS,
        )
    }

    companion object {
        private const val PROBE_SIZE_BYTES = 16 * 1024
        private const val SUGGEST_LITE_KBPS = 128
        private const val SUGGEST_LITE_RTT_MS = 800L
    }
}
