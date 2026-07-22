/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.home.impl.channels

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.appconfig.SetkaChannelConfig
import io.element.android.appconfig.SetkaMatrixConfig
import io.element.android.features.roomdirectory.api.RoomDescription
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomAlias
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import io.element.android.libraries.matrix.api.roomlist.RoomList
import io.element.android.libraries.matrix.api.roomlist.RoomListService
import io.element.android.libraries.matrix.api.roomlist.updateVisibleRange
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.text.Charsets.UTF_8

private const val SEARCH_BATCH_SIZE = 80

@Inject
class SetkaPublicChannelsPresenter(
    private val matrixClient: MatrixClient,
    private val dispatchers: CoroutineDispatchers,
    private val roomListService: RoomListService,
) : Presenter<SetkaPublicChannelsState> {
    @Composable
    override fun present(): SetkaPublicChannelsState {
        val isSetkaSession = remember(matrixClient.sessionId) {
            SetkaMatrixConfig.isSetkaHomeserver(matrixClient.sessionId.value.substringAfter(':', ""))
        }
        if (!isSetkaSession) {
            return SetkaPublicChannelsState.hidden(eventSink = { })
        }

        val coroutineScope = rememberCoroutineScope()
        val subscribedList = remember(coroutineScope) {
            roomListService.createRoomList(
                pageSize = 200,
                source = RoomList.Source.All,
                coroutineScope = coroutineScope,
            )
        }

        LaunchedEffect(subscribedList) {
            subscribedList.updateVisibleRange(0..500)
        }

        val subscribedChannels by remember {
            subscribedList.summaries.map { summaries ->
                summaries
                    .filter { summary ->
                        SetkaChannelConfig.isChannelTopic(summary.info.topic) &&
                            summary.info.currentUserMembership == CurrentUserMembership.JOINED
                    }
                    .map { it.toRoomDescription() }
                    .sortedByDescending { it.numberOfMembers }
                    .toImmutableList()
            }.flowOn(dispatchers.computation)
        }.collectAsState(persistentListOf())

        return SetkaPublicChannelsState(
            isLoading = false,
            channels = subscribedChannels,
            displayLoadMoreIndicator = false,
            eventSink = { },
        )
    }

    suspend fun loadPublicChannelsForDiscovery(): Result<ImmutableList<RoomDescription>> {
        val path = "/_matrix/client/v3/setka/channels/public?limit=$SEARCH_BATCH_SIZE"
        return matrixClient.executeAuthenticatedRequest(
            method = "GET",
            path = path,
            body = ByteArray(0),
        ).map { bytes ->
            JSONObject(bytes.decodeToString()).parseChannelsArray()
                .sortedByDescending { it.numberOfMembers }
                .toImmutableList()
        }
    }
}

private fun io.element.android.libraries.matrix.api.roomlist.RoomSummary.toRoomDescription(): RoomDescription {
    val info = info
    return RoomDescription(
        roomId = roomId,
        name = info.name,
        alias = info.canonicalAlias,
        topic = info.topic,
        avatarUrl = info.avatarUrl,
        numberOfMembers = info.activeMembersCount,
        joinRule = RoomDescription.JoinRule.PUBLIC,
    )
}

private fun JSONObject.parseChannelsArray(): ImmutableList<RoomDescription> {
    val array = optJSONArray("channels") ?: JSONArray()
    return buildList {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val roomIdRaw = item.optString("room_id").trim()
            if (roomIdRaw.isEmpty()) continue
            val aliasRaw = item.optString("canonical_alias").trim().takeIf { it.isNotBlank() }
            add(
                RoomDescription(
                    roomId = RoomId(roomIdRaw),
                    name = item.optString("name").takeIf { it.isNotBlank() },
                    alias = aliasRaw?.let { runCatching { RoomAlias(it) }.getOrNull() },
                    topic = item.optString("topic").takeIf { it.isNotBlank() },
                    avatarUrl = item.optString("avatar_url").takeIf { it.isNotBlank() },
                    numberOfMembers = item.optLong("num_joined_members", 0L),
                    joinRule = RoomDescription.JoinRule.PUBLIC,
                )
            )
        }
    }.toImmutableList()
}
