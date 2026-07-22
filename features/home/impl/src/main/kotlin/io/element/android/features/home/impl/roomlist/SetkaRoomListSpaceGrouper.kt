/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.home.impl.roomlist

import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.spaces.SpaceServiceFilter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

internal object SetkaRoomListSpaceGrouper {
    fun group(
        summaries: List<RoomListRoomSummary>,
        spaceFilters: List<SpaceServiceFilter>,
    ): ImmutableList<RoomListDisplayItem> {
        if (spaceFilters.isEmpty()) {
            return summaries.map { RoomListDisplayItem.Room(it) }.toImmutableList()
        }

        val topLevelSpaces = spaceFilters.filter { it.level == 0 }
        val spaceById = topLevelSpaces.associateBy { it.spaceRoom.roomId }
        val roomToSpaceId = mutableMapOf<RoomId, RoomId>()
        for (filter in topLevelSpaces) {
            for (descendantId in filter.descendants) {
                roomToSpaceId.putIfAbsent(descendantId, filter.spaceRoom.roomId)
            }
        }

        val spaceRoomIds = spaceById.keys
        val ungrouped = summaries.filter { summary ->
            !summary.isSpace && summary.roomId !in roomToSpaceId && summary.roomId !in spaceRoomIds
        }
        val groupedBySpace = topLevelSpaces.mapNotNull { filter ->
            val roomsInSpace = summaries.filter { summary ->
                !summary.isSpace && roomToSpaceId[summary.roomId] == filter.spaceRoom.roomId
            }
            if (roomsInSpace.isEmpty()) null else filter to roomsInSpace
        }

        return buildList {
            ungrouped.forEach { add(RoomListDisplayItem.Room(it)) }
            groupedBySpace.forEach { (filter, rooms) ->
                add(
                    RoomListDisplayItem.SpaceHeader(
                        name = filter.spaceRoom.displayName,
                        spaceId = filter.spaceRoom.roomId,
                    )
                )
                rooms.forEach { add(RoomListDisplayItem.Room(it)) }
            }
        }.toImmutableList()
    }
}

sealed interface RoomListDisplayItem {
    data class SpaceHeader(
        val name: String,
        val spaceId: RoomId,
    ) : RoomListDisplayItem

    data class Room(
        val summary: RoomListRoomSummary,
    ) : RoomListDisplayItem
}
