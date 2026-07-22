/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.search

import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.appconfig.SetkaChannelConfig
import io.element.android.features.home.impl.datasource.RoomListRoomSummaryFactory
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.room.isDm
import io.element.android.libraries.matrix.api.roomlist.RoomList
import io.element.android.libraries.matrix.api.roomlist.RoomListFilter
import io.element.android.libraries.matrix.api.roomlist.RoomListService
import io.element.android.libraries.matrix.api.roomlist.RoomSummary
import io.element.android.libraries.matrix.api.roomlist.updateVisibleRange
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 30
private const val USER_SEARCH_LIMIT = 20L

@AssistedInject
class RoomListSearchDataSource(
    @Assisted coroutineScope: CoroutineScope,
    roomListService: RoomListService,
    private val matrixClient: MatrixClient,
    coroutineDispatchers: CoroutineDispatchers,
    private val roomSummaryFactory: RoomListRoomSummaryFactory,
) {
    private val queryFlow = MutableStateFlow("")
    private val filterFlow = MutableStateFlow(RoomListSearchFilter.All)
    private val userResultsFlow = MutableStateFlow<ImmutableList<RoomListSearchResultItem.User>>(persistentListOf())
    private val presenceSubtitlesFlow = MutableStateFlow<Map<String, String>>(emptyMap())

    @AssistedFactory
    interface Factory {
        fun create(coroutineScope: CoroutineScope): RoomListSearchDataSource
    }

    private val roomList = roomListService.createRoomList(
        pageSize = PAGE_SIZE,
        source = RoomList.Source.All,
        coroutineScope = coroutineScope
    )

    init {
        coroutineScope.launch {
            roomList.updateVisibleRange(0..200)
        }
    }

    val homeserver = matrixClient.sessionId.value.substringAfter(':', "")

    val searchResults: Flow<ImmutableList<RoomListSearchResultItem>> = combine(
        roomList.summaries,
        queryFlow,
        filterFlow,
        userResultsFlow,
        presenceSubtitlesFlow,
    ) { roomSummaries, query, filter, userResults, presenceSubtitles ->
        val normalizedQuery = query.trim().lowercase()
        val roomItems = roomSummaries
            .filter { roomSummary -> roomSummary.matchesSearchFilter(filter, homeserver) }
            .filter { roomSummary -> roomSummary.matchesSearchQuery(normalizedQuery, homeserver) }
            .map { roomSummary ->
                val summary = roomSummaryFactory.create(roomSummary)
                val subtitle = roomSubtitle(summary, roomSummary, presenceSubtitles)
                RoomListSearchResultItem.Room(summary = summary, subtitle = subtitle)
            }

        if (normalizedQuery.isBlank()) {
            roomItems.toImmutableList()
        } else {
            val existingDmUserIds = roomSummaries
                .filter { it.info.isDm }
                .flatMap { summary -> summary.info.heroes.map { it.userId.value } }
                .toSet()
            val filteredUsers = when (filter) {
                RoomListSearchFilter.Channels,
                RoomListSearchFilter.Spaces -> emptyList()
                RoomListSearchFilter.All,
                RoomListSearchFilter.People -> userResults.filter { user ->
                    user.matrixUser.userId.value !in existingDmUserIds
                }
            }
            (roomItems + filteredUsers).toImmutableList()
        }
    }
        .flowOn(coroutineDispatchers.computation)

    suspend fun updateVisibleRange(visibleRange: IntRange) {
        roomList.updateVisibleRange(visibleRange)
    }

    suspend fun setFilter(filter: RoomListSearchFilter) {
        filterFlow.value = filter
    }

    suspend fun setSearchQuery(searchQuery: String) = coroutineScope {
        queryFlow.value = searchQuery
        val normalizedQuery = searchQuery.trim()
        val listFilter = if (normalizedQuery.isBlank()) {
            RoomListFilter.all()
        } else {
            RoomListFilter.NormalizedMatchRoomName(normalizedQuery)
        }
        roomList.updateFilter(listFilter)
        roomList.updateVisibleRange(0..500)
        if (normalizedQuery.length < 2) {
            userResultsFlow.value = persistentListOf()
            presenceSubtitlesFlow.value = emptyMap()
            return@coroutineScope
        }

        launch {
            val users = matrixClient.searchUsers(normalizedQuery, USER_SEARCH_LIMIT)
                .getOrNull()
                ?.results
                .orEmpty()
            val userItems = users.map { user ->
                val presenceText = fetchPresenceText(matrixClient, user.userId)
                RoomListSearchResultItem.User(matrixUser = user, presenceText = presenceText)
            }.toImmutableList()
            userResultsFlow.value = userItems
        }

        launch {
            val subtitles = mutableMapOf<String, String>()
            val summaries = try {
                roomList.summaries.first()
            } catch (_: Exception) {
                emptyList()
            }
            summaries
                .filter { summary ->
                    summary.info.name.orEmpty().lowercase().contains(normalizedQuery.lowercase())
                }
                .filter { it.info.isDm }
                .forEach { summary ->
                    val otherUserId = summary.info.heroes.firstOrNull()?.userId ?: return@forEach
                    val presence = fetchPresenceText(matrixClient, otherUserId) ?: "Был(-а) давно"
                    subtitles[summary.roomId.value] = presence
                }
            presenceSubtitlesFlow.value = subtitles
        }
    }

    private fun roomSubtitle(
        summary: io.element.android.features.home.impl.model.RoomListRoomSummary,
        roomSummary: RoomSummary,
        presenceSubtitles: Map<String, String>,
    ): String? {
        if (summary.isSetkaChannel) {
            return formatMembersCountSubtitle(roomSummary.info.activeMembersCount, isSpace = false)
        }
        if (summary.isDm) {
            return presenceSubtitles[summary.id]
        }
        if (summary.isSpace || !summary.isDirect) {
            return formatMembersCountSubtitle(roomSummary.info.activeMembersCount, summary.isSpace)
        }
        return null
    }
}
