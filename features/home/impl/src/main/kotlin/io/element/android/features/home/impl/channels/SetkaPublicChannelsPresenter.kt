/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.home.impl.channels

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import io.element.android.appconfig.SetkaAnalyticsEvents
import io.element.android.libraries.matrix.api.setka.SetkaAnalyticsService
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomAlias
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import kotlin.text.Charsets.UTF_8

private const val SEARCH_BATCH_SIZE = 80

@Inject
class SetkaPublicChannelsPresenter(
    private val matrixClient: MatrixClient,
    private val setkaAnalyticsService: SetkaAnalyticsService,
) : Presenter<SetkaPublicChannelsState> {
    @Composable
    override fun present(): SetkaPublicChannelsState = presentChannelsTab(channelsTabSelected = false)

    @Composable
    fun presentChannelsTab(channelsTabSelected: Boolean): SetkaPublicChannelsState {
        val isSetkaSession = remember(matrixClient.sessionId) {
            SetkaMatrixConfig.isSetkaHomeserver(matrixClient.sessionId.value.substringAfter(':', ""))
        }
        if (!isSetkaSession) {
            return SetkaPublicChannelsState.hidden(eventSink = { })
        }

        var channels by remember { mutableStateOf<ImmutableList<RoomDescription>>(persistentListOf()) }
        var isLoading by remember { mutableStateOf(true) }
        var isLoadingMore by remember { mutableStateOf(false) }
        var loadError by remember { mutableStateOf<SetkaChannelLoadFailure?>(null) }
        var nextBatch by remember { mutableStateOf<String?>(null) }
        val coroutineScope = rememberCoroutineScope()

        suspend fun fetch(reset: Boolean) {
            if (reset) {
                isLoading = true
                loadError = null
                nextBatch = null
            } else {
                isLoadingMore = true
            }
            loadPublicChannelsForDiscovery(
                since = if (reset) null else nextBatch,
            ).onSuccess { page ->
                Timber.d("Setka public channels loaded: count=${page.channels.size}, next=${page.nextBatch != null}")
                channels = if (reset) {
                    page.channels
                } else {
                    (channels + page.channels).distinctBy { it.roomId }.toImmutableList()
                }
                nextBatch = page.nextBatch
                loadError = null
            }.onFailure { error ->
                val sinceToken = if (reset) null else nextBatch
                Timber.w(error, "Failed to load Setka public channels (since=%s)", sinceToken)
                if (reset) {
                    channels = persistentListOf()
                    nextBatch = null
                    loadError = error.toSetkaChannelLoadFailure()
                }
            }
            isLoading = false
            isLoadingMore = false
        }

        LaunchedEffect(Unit) {
            fetch(reset = true)
        }

        LaunchedEffect(channelsTabSelected) {
            if (channelsTabSelected) {
                setkaAnalyticsService.trackEvent(SetkaAnalyticsEvents.CHANNEL_DISCOVERY_VIEWED)
                fetch(reset = true)
            }
        }

        return SetkaPublicChannelsState(
            isLoading = isLoading,
            channels = channels,
            displayLoadMoreIndicator = nextBatch != null,
            loadError = loadError,
            eventSink = { event ->
                when (event) {
                    SetkaPublicChannelsEvents.LoadMore -> {
                        if (!isLoadingMore && nextBatch != null) {
                            coroutineScope.launch { fetch(reset = false) }
                        }
                    }
                    SetkaPublicChannelsEvents.Retry -> {
                        coroutineScope.launch { fetch(reset = true) }
                    }
                }
            },
        )
    }

    fun discoveryLoader(): SetkaChannelDiscoveryLoader {
        return SetkaChannelDiscoveryLoader { since, query ->
            loadPublicChannelsForDiscovery(since = since, query = query)
        }
    }

    suspend fun loadPublicChannelsForDiscovery(
        since: String? = null,
        query: String? = null,
    ): Result<SetkaChannelDiscoveryPage> {
        val params = buildList {
            add("limit=$SEARCH_BATCH_SIZE")
            since?.trim()?.takeIf { it.isNotBlank() }?.let { add("since=${encodeQuery(it)}") }
            query?.trim()?.takeIf { it.isNotBlank() }?.let { add("q=${encodeQuery(it)}") }
        }
        val path = "/_matrix/client/v3/setka/channels/public?" + params.joinToString("&")
        val setkaResult = matrixClient.executeAuthenticatedRequest(
            method = "GET",
            path = path,
        ).map { bytes ->
            val json = JSONObject(bytes.decodeToString())
            SetkaChannelDiscoveryPage(
                channels = json.parseChannelsArray()
                    .sortedByDescending { it.numberOfMembers }
                    .toImmutableList(),
                nextBatch = json.optString("next_batch").trim().takeIf { it.isNotBlank() },
            )
        }
        if (setkaResult.isSuccess || since != null) {
            return setkaResult
        }
        val primaryError = setkaResult.exceptionOrNull()
        Timber.w(primaryError, "Setka channels endpoint failed (path=%s), falling back to publicRooms", path)
        val fallback = loadPublicChannelsViaPublicRooms(query = query)
        fallback.onSuccess { page ->
            Timber.i(
                "Setka publicRooms fallback succeeded: count=%d (original error: %s)",
                page.channels.size,
                primaryError?.message ?: primaryError?.javaClass?.simpleName,
            )
        }
        if (fallback.isSuccess) {
            return fallback
        }
        val fallbackError = fallback.exceptionOrNull() ?: primaryError ?: Exception("Unknown channel load error")
        val primaryLoadError = primaryError?.toSetkaChannelLoadError()
        return Result.failure(
            SetkaChannelLoadException(
                error = SetkaChannelLoadError.FALLBACK_FAILED,
                cause = fallbackError,
                primaryError = primaryLoadError,
            )
        )
    }

    private suspend fun loadPublicChannelsViaPublicRooms(
        query: String? = null,
    ): Result<SetkaChannelDiscoveryPage> {
        val filterJson = JSONObject()
            .put("generic_search_term", query?.trim().orEmpty())
            .toString()
        val params = buildList {
            add("limit=$SEARCH_BATCH_SIZE")
            add("filter=${encodeQuery(filterJson)}")
        }
        val path = "/_matrix/client/v3/publicRooms?" + params.joinToString("&")
        return matrixClient.executeAuthenticatedRequest(
            method = "GET",
            path = path,
        ).map { bytes ->
            val json = JSONObject(bytes.decodeToString())
            val channels = json.parsePublicRoomsArray()
                .filter { SetkaChannelConfig.isChannelTopic(it.topic) }
                .let { rooms ->
                    val needle = query?.trim()?.lowercase().orEmpty()
                    if (needle.isBlank()) {
                        rooms
                    } else {
                        rooms.filter { room ->
                            listOfNotNull(
                                room.name?.lowercase(),
                                room.topic?.lowercase(),
                                room.alias?.value?.lowercase(),
                            ).any { haystack -> haystack.contains(needle) }
                        }
                    }
                }
                .sortedByDescending { it.numberOfMembers }
                .toImmutableList()
            SetkaChannelDiscoveryPage(
                channels = channels,
                nextBatch = null,
            )
        }
    }
}

private fun encodeQuery(value: String): String = URLEncoder.encode(value, UTF_8.name())

private fun JSONObject.parsePublicRoomsArray(): ImmutableList<RoomDescription> {
    val array = optJSONArray("chunk") ?: JSONArray()
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
