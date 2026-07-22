/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.setkalite.impl

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.SessionScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max

/**
 * Best-effort bandwidth throttle for Setka Lite HTTP/media traffic.
 */
@SingleIn(SessionScope::class)
class SetkaLiteHttpScheduler @Inject constructor() {
    private val mutex = Mutex()
    private var bytesPerSecond: Int = DEFAULT_BYTES_PER_SECOND
    private var pageDelayMs: Long = DEFAULT_PAGE_DELAY_MS
    private var nextAllowedAtMs: Long = 0L

    fun applyConfig(maxKbps: Int, pageDelayMs: Long) {
        bytesPerSecond = max(8 * 1024, (maxKbps.coerceIn(32, 512) * 1024) / 8)
        this.pageDelayMs = pageDelayMs.coerceIn(50L, 2_000L)
    }

    suspend fun beforeRequest(estimatedBytes: Int = 0) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            if (now < nextAllowedAtMs) {
                delay(nextAllowedAtMs - now)
            }
            if (estimatedBytes > 0) {
                val extraMs = ((estimatedBytes.toDouble() / bytesPerSecond) * 1000).toLong()
                nextAllowedAtMs = System.currentTimeMillis() + extraMs
            }
        }
    }

    suspend fun afterPage() {
        delay(pageDelayMs)
    }

    companion object {
        private const val DEFAULT_BYTES_PER_SECOND = 16 * 1024 // 128 kbps
        private const val DEFAULT_PAGE_DELAY_MS = 150L
    }
}
