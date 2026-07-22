/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.home.impl.recent

import dev.zacsweers.metro.Inject
import io.element.android.features.home.impl.datasource.RoomListRoomSummaryFactory
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.home.impl.roomlist.RoomListContentState
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.roomlist.LatestEventValue
import io.element.android.libraries.matrix.api.roomlist.RoomSummary
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Inject
class RecentRoomSummariesResolver(
    private val client: MatrixClient,
    private val roomListRoomSummaryFactory: RoomListRoomSummaryFactory,
) {
    suspend fun resolve(
        recentRoomIds: List<String>,
        contentState: RoomListContentState,
    ): ImmutableList<RoomListRoomSummary> = withContext(Dispatchers.Default) {
        if (recentRoomIds.isEmpty()) return@withContext persistentListOf()

        val loadedSummaries = (contentState as? RoomListContentState.Rooms)?.summaries.orEmpty()
        val byId = loadedSummaries.associateBy { it.roomId.value }

        recentRoomIds.mapNotNull { roomIdValue ->
            byId[roomIdValue] ?: buildSummaryForRoom(RoomId(roomIdValue))
        }.toImmutableList()
    }

    private suspend fun buildSummaryForRoom(roomId: RoomId): RoomListRoomSummary? {
        val room = client.getRoom(roomId) ?: return null
        val info = room.info()
        return roomListRoomSummaryFactory.create(
            RoomSummary(
                info = info,
                latestEvent = LatestEventValue.None,
            )
        )
    }
}
