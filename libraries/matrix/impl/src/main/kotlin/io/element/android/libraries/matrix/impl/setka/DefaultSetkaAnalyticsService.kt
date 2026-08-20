/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.impl.setka

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.appconfig.SetkaAnalyticsEvents
import io.element.android.appconfig.SetkaAnalyticsConfig
import io.element.android.appconfig.SetkaMatrixConfig
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.setka.SetkaAnalyticsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

@ContributesBinding(SessionScope::class)
@SingleIn(SessionScope::class)
class DefaultSetkaAnalyticsService(
    private val matrixClient: MatrixClient,
    private val buildMeta: BuildMeta,
    @SessionCoroutineScope private val scope: CoroutineScope,
) : SetkaAnalyticsService {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val mutex = Mutex()
    private val pendingEvents = mutableListOf<PendingEvent>()
    private var flushJob: Job? = null

    private val isEnabled: Boolean
        get() = SetkaAnalyticsConfig.SERVER_SYNC_ENABLED &&
            SetkaMatrixConfig.isSetkaHomeserver(matrixClient.sessionId.value.substringAfter(':', ""))

    override fun trackEvent(eventName: String, properties: Map<String, String>) {
        if (!isEnabled) return
        scope.launch {
            mutex.withLock {
                pendingEvents.add(
                    PendingEvent(
                        eventName = eventName,
                        properties = properties,
                        ts = System.currentTimeMillis(),
                    )
                )
                if (pendingEvents.size >= MAX_BATCH) {
                    flushLocked()
                } else {
                    scheduleFlushLocked()
                }
            }
        }
    }

    override fun trackSession() {
        trackEvent(SetkaAnalyticsEvents.SESSION_ACTIVE)
        scope.launch {
            flush()
        }
    }

    override suspend fun flush() {
        mutex.withLock {
            flushLocked()
        }
    }

    private fun scheduleFlushLocked() {
        if (flushJob?.isActive == true) return
        flushJob = scope.launch {
            delay(FLUSH_MS)
            mutex.withLock {
                flushLocked()
            }
        }
    }

    private suspend fun flushLocked() {
        flushJob?.cancel()
        flushJob = null
        if (pendingEvents.isEmpty()) return

        val batch = pendingEvents.take(MAX_BATCH)
        pendingEvents.removeAll(batch.toSet())

        val body = json.encodeToString(
            CaptureRequest(
                platform = SetkaAnalyticsConfig.PLATFORM,
                appVersion = buildMeta.versionName,
                events = batch.map { event ->
                    CaptureEvent(
                        eventName = event.eventName,
                        properties = event.properties,
                        ts = event.ts,
                    )
                },
            )
        )

        matrixClient.executeAuthenticatedRequest(
            method = "POST",
            path = "/_matrix/client/v3/setka/analytics/capture",
            body = body.encodeToByteArray(),
        ).onFailure { error ->
            Timber.w(error, "Setka analytics server sync failed")
            pendingEvents.addAll(0, batch)
        }
    }

    private data class PendingEvent(
        val eventName: String,
        val properties: Map<String, String>,
        val ts: Long,
    )

    @Serializable
    private data class CaptureRequest(
        val platform: String,
        @SerialName("app_version") val appVersion: String,
        val events: List<CaptureEvent>,
    )

    @Serializable
    private data class CaptureEvent(
        @SerialName("event_name") val eventName: String,
        val properties: Map<String, String> = emptyMap(),
        val ts: Long,
    )

    private companion object {
        private const val FLUSH_MS = 10_000L
        private const val MAX_BATCH = 25
    }
}
