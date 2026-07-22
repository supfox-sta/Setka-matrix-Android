/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.setkalite.impl

import android.content.Context
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.setkalite.api.SetkaLiteMediaGate
import io.element.android.libraries.setkalite.api.SetkaLiteMediaTransferIds
import io.element.android.libraries.setkalite.api.SetkaTransferQueue
import io.element.android.libraries.setkalite.api.TransferKind
import io.element.android.libraries.setkalite.api.TransferQueueItem
import io.element.android.libraries.setkalite.api.TransferState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.element.android.libraries.matrix.api.MatrixClientImportEventsException
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import timber.log.Timber

@ContributesBinding(SessionScope::class)
@SingleIn(SessionScope::class)
class DefaultSetkaLiteMediaGate @Inject constructor(
    private val coordinatorState: SetkaLiteCoordinatorStateAccessor,
    private val transferQueue: SetkaTransferQueue,
    private val httpScheduler: SetkaLiteHttpScheduler,
) : SetkaLiteMediaGate {
    private val downloadMutex = Mutex()

    override val isLiteEnabled: StateFlow<Boolean> = coordinatorState.isEnabled

    override suspend fun beforeMediaDownload(label: String, estimatedBytes: Long?) {
        beforeMediaTransfer(label, estimatedBytes, TransferKind.DOWNLOAD)
    }

    override suspend fun beforeMediaUpload(label: String, estimatedBytes: Long?) {
        beforeMediaTransfer(label, estimatedBytes, TransferKind.UPLOAD)
    }

    private suspend fun beforeMediaTransfer(label: String, estimatedBytes: Long?, kind: TransferKind) {
        if (!isLiteEnabled.value) return
        val itemId = SetkaLiteMediaTransferIds.mediaItemId(label)
        transferQueue.upsert(
            TransferQueueItem(
                id = itemId,
                kind = kind,
                label = label,
                bytesDone = 0,
                bytesTotal = estimatedBytes?.coerceAtLeast(1L) ?: 1L,
                state = TransferState.ACTIVE,
            )
        )
        downloadMutex.withLock {
            httpScheduler.beforeRequest(estimatedBytes?.toInt() ?: 32 * 1024)
        }
    }
}

class SetkaLiteEventImporter @Inject constructor(
    private val matrixClient: MatrixClient,
    @ApplicationContext context: Context,
) {
    /**
     * Caches lite-sync events locally and triggers timeline pagination.
     *
     * Note: matrix-rust-sdk has no public backfill/import API yet, so events fetched via
     * `setka_lite` cannot be inserted into the encrypted store. Pagination only surfaces
     * history the SDK already synced through the normal fast path.
     */
    private val cacheRoot = File(context.cacheDir, "setka-lite/events").apply { mkdirs() }

    suspend fun importRoomEvents(roomId: String, eventsJson: JSONObject): Int {
        val events = eventsJson.optJSONArray("events") ?: JSONArray()
        if (events.length() == 0) return 0
        cacheRoomEvents(roomId, events)
        val prevToken = eventsJson.optString("prev_token", "").takeIf { it.isNotBlank() }
        val payload = events.toString()
        val sdkResult = matrixClient.importBackwardsRoomEvents(
            roomId = RoomId(roomId),
            eventsJson = payload,
            prevToken = prevToken,
        )
        sdkResult.onSuccess { result ->
            Timber.d("Setka Lite: SDK imported ${result.importedCount} events for $roomId")
            return result.importedCount
        }.onFailure { error ->
            if (error !is MatrixClientImportEventsException) {
                Timber.w(error, "Setka Lite: SDK import failed for $roomId")
            }
        }
        refreshTimeline(roomId)
        return events.length()
    }

    private fun cacheRoomEvents(roomId: String, events: JSONArray) {
        val file = cacheFileForRoom(roomId)
        val payload = buildString {
            append('[')
            for (index in 0 until events.length()) {
                if (index > 0) append(',')
                append(events.getJSONObject(index).toString())
            }
            append(']')
        }
        file.writeText(payload)
    }

    private suspend fun refreshTimeline(roomId: String) {
        val room = matrixClient.getJoinedRoom(RoomId(roomId)) ?: return
        repeat(TIMELINE_PAGINATE_ATTEMPTS) {
            room.liveTimeline.paginate(Timeline.PaginationDirection.BACKWARDS)
        }
    }

    private fun cacheFileForRoom(roomId: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(roomId.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(cacheRoot, "$digest.json")
    }

    companion object {
        private const val TIMELINE_PAGINATE_ATTEMPTS = 3
    }
}

class SetkaLiteCryptoFetcher @Inject constructor(
    private val apiClient: SetkaLiteApiClient,
    private val matrixClient: MatrixClient,
    @ApplicationContext context: Context,
) {
    private val cacheFile = File(context.cacheDir, "setka-lite/to_device.json")

    suspend fun fetchAndCache(): Int {
        var since: String? = null
        var total = 0
        val all = JSONArray()
        repeat(MAX_PAGES) {
            val page = apiClient.getToDeviceEvents(since) ?: return total
            val events = page.optJSONArray("events") ?: JSONArray()
            for (index in 0 until events.length()) {
                all.put(events.getJSONObject(index))
            }
            total += events.length()
            val hasMore = page.optBoolean("has_more", false)
            since = page.optString("next_since", "").takeIf { it.isNotBlank() }
            if (!hasMore || since == null) return@repeat
        }
        if (total > 0) {
            cacheFile.writeText(all.toString())
            matrixClient.importToDeviceEvents(all.toString())
                .onSuccess { imported ->
                    Timber.d("Setka Lite: imported $imported to-device events via SDK")
                }
                .onFailure { error ->
                    if (error !is MatrixClientImportEventsException) {
                        Timber.w(error, "Setka Lite: to-device SDK import failed")
                    }
                }
        }
        return total
    }

    companion object {
        private const val MAX_PAGES = 5
    }
}

/**
 * Exposes coordinator enabled flag to other Setka Lite bindings without a circular dependency.
 */
@SingleIn(SessionScope::class)
class SetkaLiteCoordinatorStateAccessor @Inject constructor() {
    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
    }
}
