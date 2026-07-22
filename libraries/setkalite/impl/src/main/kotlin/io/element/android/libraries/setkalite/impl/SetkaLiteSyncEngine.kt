/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.setkalite.impl

import io.element.android.libraries.setkalite.api.SetkaLiteSyncPhase
import io.element.android.libraries.setkalite.api.SetkaLiteSyncProgress
import io.element.android.libraries.setkalite.api.SetkaTransferQueue
import io.element.android.libraries.setkalite.api.TransferKind
import io.element.android.libraries.setkalite.api.TransferQueueItem
import io.element.android.libraries.setkalite.api.TransferState
import org.json.JSONArray
import dev.zacsweers.metro.Inject

class SetkaLiteSyncEngine @Inject constructor(
    private val apiClient: SetkaLiteApiClient,
    private val transferQueue: SetkaTransferQueue,
    private val httpScheduler: SetkaLiteHttpScheduler,
    private val eventImporter: SetkaLiteEventImporter,
    private val cryptoFetcher: SetkaLiteCryptoFetcher,
    private val timelineRefresher: SetkaLiteTimelineRefresher,
) {
    suspend fun runSync(onProgress: (SetkaLiteSyncProgress) -> Unit): SetkaLiteSyncProgress {
        val syncItemId = "setka-lite-sync"
        transferQueue.upsert(
            TransferQueueItem(
                id = syncItemId,
                kind = TransferKind.SYNC,
                label = "Setka Lite",
                bytesDone = 0,
                bytesTotal = 100,
                state = TransferState.ACTIVE,
            )
        )

        var eventsImported = 0

        fun emit(progress: SetkaLiteSyncProgress) {
            transferQueue.updateProgress(syncItemId, progress.percent.toLong(), 100)
            onProgress(progress)
        }

        emit(SetkaLiteSyncProgress(percent = 2, phase = SetkaLiteSyncPhase.CONFIG))
        val config = apiClient.getConfig()
        if (config == null) {
            transferQueue.markFailed(syncItemId)
            return SetkaLiteSyncProgress(percent = 0, phase = SetkaLiteSyncPhase.IDLE)
        }
        val pageSize = config.optInt("max_events_per_page", DEFAULT_EVENTS_PAGE).coerceIn(5, 30)
        val roomsPageSize = config.optInt("max_rooms_per_page", DEFAULT_ROOMS_PAGE).coerceIn(10, 50)
        val maxEventPages = config.optInt("max_event_pages_per_room", DEFAULT_MAX_EVENT_PAGES).coerceIn(1, 10)
        val timelineRefreshPages = config.optInt("timeline_refresh_pages", DEFAULT_TIMELINE_REFRESH_PAGES)
            .coerceIn(1, 10)
        httpScheduler.applyConfig(
            maxKbps = config.optInt("max_kbps", DEFAULT_MAX_KBPS),
            pageDelayMs = config.optLong("page_delay_ms", DEFAULT_PAGE_DELAY_MS),
        )

        emit(SetkaLiteSyncProgress(percent = 8, phase = SetkaLiteSyncPhase.ROOMS))
        val allRooms = mutableListOf<org.json.JSONObject>()
        var roomsSince: String? = null
        var hasMoreRooms = true
        while (hasMoreRooms) {
            val roomsJson = apiClient.getRooms(roomsPageSize, roomsSince) ?: break
            val rooms = roomsJson.optJSONArray("rooms") ?: JSONArray()
            for (i in 0 until rooms.length()) {
                allRooms.add(rooms.getJSONObject(i))
            }
            hasMoreRooms = roomsJson.optBoolean("has_more", false)
            roomsSince = roomsJson.optString("next_since", "").takeIf { it.isNotBlank() }
            httpScheduler.afterPage()
        }

        val roomsTotal = allRooms.size.coerceAtLeast(1)
        var roomsDone = 0
        var eventsWeight = 0
        val eventsBudget = (roomsTotal * maxEventPages).coerceAtLeast(1)

        for (room in allRooms) {
            val roomId = room.optString("room_id")
            val roomName = room.optString("name", roomId)
            roomsDone++
            val roomListPercent = 15 + ((roomsDone * 20) / roomsTotal)
            emit(
                SetkaLiteSyncProgress(
                    percent = roomListPercent,
                    phase = SetkaLiteSyncPhase.EVENTS,
                    roomsDone = roomsDone,
                    roomsTotal = roomsTotal,
                    currentRoomName = roomName,
                    eventsImported = eventsImported,
                )
            )

            var before: String? = null
            var pages = 0
            while (pages < maxEventPages) {
                val eventsJson = apiClient.getRoomEvents(roomId, pageSize, before) ?: break
                eventsImported += eventImporter.importRoomEvents(roomId, eventsJson)
                pages++
                eventsWeight++
                val eventPercent = 35 + ((eventsWeight * 55) / eventsBudget)
                emit(
                    SetkaLiteSyncProgress(
                        percent = eventPercent.coerceAtMost(94),
                        phase = SetkaLiteSyncPhase.EVENTS,
                        roomsDone = roomsDone,
                        roomsTotal = roomsTotal,
                        currentRoomName = roomName,
                        eventsImported = eventsImported,
                    )
                )
                before = eventsJson.optString("prev_token", "").takeIf { it.isNotBlank() }
                val hasMore = eventsJson.optBoolean("has_more", false)
                if (!hasMore || before == null) break
                httpScheduler.afterPage()
            }
        }

        emit(
            SetkaLiteSyncProgress(
                percent = 96,
                phase = SetkaLiteSyncPhase.CRYPTO,
                roomsDone = roomsTotal,
                roomsTotal = roomsTotal,
                eventsImported = eventsImported,
            )
        )
        cryptoFetcher.fetchAndCache()
        emit(
            SetkaLiteSyncProgress(
                percent = 98,
                phase = SetkaLiteSyncPhase.CRYPTO,
                roomsDone = roomsTotal,
                roomsTotal = roomsTotal,
                eventsImported = eventsImported,
            )
        )
        timelineRefresher.refreshAllJoinedRooms(timelineRefreshPages)
        val done = SetkaLiteSyncProgress(
            percent = 100,
            phase = SetkaLiteSyncPhase.DONE,
            roomsDone = roomsTotal,
            roomsTotal = roomsTotal,
            eventsImported = eventsImported,
        )
        transferQueue.markDone(syncItemId)
        emit(done)
        return done
    }

    companion object {
        private const val DEFAULT_EVENTS_PAGE = 15
        private const val DEFAULT_ROOMS_PAGE = 30
        private const val DEFAULT_MAX_EVENT_PAGES = 3
        private const val DEFAULT_TIMELINE_REFRESH_PAGES = 5
        private const val DEFAULT_PAGE_DELAY_MS = 150L
        private const val DEFAULT_MAX_KBPS = 128
    }
}
