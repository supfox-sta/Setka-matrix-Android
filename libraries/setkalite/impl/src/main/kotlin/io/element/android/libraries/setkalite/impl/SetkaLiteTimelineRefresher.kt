/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.setkalite.impl

import dev.zacsweers.metro.Inject
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.timeline.Timeline
import timber.log.Timber

class SetkaLiteTimelineRefresher @Inject constructor(
    private val matrixClient: MatrixClient,
) {
    suspend fun refreshAllJoinedRooms(pagesPerRoom: Int) {
        val roomIds = matrixClient.getJoinedRoomIds().getOrNull().orEmpty()
        if (roomIds.isEmpty()) return
        val pages = pagesPerRoom.coerceIn(1, 10)
        var refreshed = 0
        for (roomId in roomIds) {
            val room = matrixClient.getJoinedRoom(roomId) ?: continue
            repeat(pages) {
                room.liveTimeline.paginate(Timeline.PaginationDirection.BACKWARDS)
            }
            refreshed++
        }
        Timber.d("Setka Lite: refreshed timelines for $refreshed rooms ($pages pages each)")
    }
}
