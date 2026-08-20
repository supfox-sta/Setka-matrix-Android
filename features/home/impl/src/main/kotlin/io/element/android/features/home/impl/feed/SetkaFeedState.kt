/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.home.impl.feed

import io.element.android.features.home.impl.channels.SetkaPublicChannelsState
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

enum class SetkaFeedTab {
    Recommended,
    Subscriptions,
    Channels,
}

data class SetkaFeedState(
    val selectedTab: SetkaFeedTab = SetkaFeedTab.Recommended,
    val posts: ImmutableList<SetkaFeedPost> = persistentListOf(),
    val publicChannelsState: SetkaPublicChannelsState? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val loadError: String? = null,
    val nextBatch: String? = null,
    val showSearchOverlay: Boolean = false,
    val searchResults: ImmutableList<SetkaFeedPost> = persistentListOf(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val eventSink: (SetkaFeedEvents) -> Unit,
) {
    companion object {
        fun hidden(eventSink: (SetkaFeedEvents) -> Unit) = SetkaFeedState(eventSink = eventSink)
    }
}

sealed interface SetkaFeedEvents {
    data class SelectTab(val tab: SetkaFeedTab) : SetkaFeedEvents
    data object LoadMore : SetkaFeedEvents
    data object Retry : SetkaFeedEvents
    data object OpenSearch : SetkaFeedEvents
    data object CloseSearch : SetkaFeedEvents
    data class PerformSearch(val query: String) : SetkaFeedEvents
    data class ToggleSubscribe(val roomId: RoomId, val subscribed: Boolean) : SetkaFeedEvents
    data class ToggleLike(val eventId: EventId) : SetkaFeedEvents
    data class RecordView(val eventId: EventId) : SetkaFeedEvents
    data class OpenPostComments(val roomId: RoomId, val eventId: EventId) : SetkaFeedEvents
    data class SharePost(val roomId: RoomId, val eventId: EventId) : SetkaFeedEvents
    data class OpenChannel(val roomId: RoomId) : SetkaFeedEvents
}
