/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.home.impl.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.appconfig.SetkaMatrixConfig
import io.element.android.features.home.impl.channels.SetkaPublicChannelsPresenter
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.timeline.item.event.EventOrTransactionId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import timber.log.Timber

@Inject
class SetkaFeedPresenter(
    private val matrixClient: MatrixClient,
    private val feedService: SetkaFeedService,
    private val publicChannelsPresenter: SetkaPublicChannelsPresenter,
) : Presenter<SetkaFeedState> {
    @Composable
    override fun present(): SetkaFeedState = presentFeedTab(feedTabSelected = false)

    @Composable
    fun presentFeedTab(feedTabSelected: Boolean): SetkaFeedState {
        val isSetkaSession = remember(matrixClient.sessionId) {
            SetkaMatrixConfig.isSetkaHomeserver(matrixClient.sessionId.value.substringAfter(':', ""))
        }
        if (!isSetkaSession) {
            return SetkaFeedState.hidden(eventSink = { })
        }

        var selectedTab by remember { mutableStateOf(SetkaFeedTab.Recommended) }
        var posts by remember { mutableStateOf<ImmutableList<SetkaFeedPost>>(persistentListOf()) }
        var isLoading by remember { mutableStateOf(true) }
        var isLoadingMore by remember { mutableStateOf(false) }
        var loadError by remember { mutableStateOf<String?>(null) }
        var nextBatch by remember { mutableStateOf<String?>(null) }
        var showSearchOverlay by remember { mutableStateOf(false) }
        var searchResults by remember { mutableStateOf<ImmutableList<SetkaFeedPost>>(persistentListOf()) }
        var isSearching by remember { mutableStateOf(false) }
        var searchError by remember { mutableStateOf<String?>(null) }
        val coroutineScope = rememberCoroutineScope()

        val channelsState = publicChannelsPresenter.presentChannelsTab(
            channelsTabSelected = feedTabSelected && selectedTab == SetkaFeedTab.Channels,
        )

        suspend fun enrichWithMembership(feedPosts: List<SetkaFeedPost>): ImmutableList<SetkaFeedPost> {
            return feedPosts.map { post ->
                post.copy(isMember = matrixClient.getJoinedRoom(post.roomId) != null)
            }.toImmutableList()
        }

        suspend fun fetchFeed(reset: Boolean) {
            if (selectedTab == SetkaFeedTab.Channels) return
            val mode = when (selectedTab) {
                SetkaFeedTab.Recommended -> SetkaFeedMode.Recommended
                SetkaFeedTab.Subscriptions -> SetkaFeedMode.Subscriptions
                SetkaFeedTab.Channels -> SetkaFeedMode.Recommended
            }
            if (reset) {
                isLoading = true
                loadError = null
                nextBatch = null
            } else {
                isLoadingMore = true
            }
            feedService.loadFeed(
                mode = mode,
                since = if (reset) null else nextBatch,
            ).onSuccess { page ->
                val enriched = enrichWithMembership(page.posts)
                posts = if (reset) {
                    enriched
                } else {
                    (posts + enriched).distinctBy { it.eventId }.toImmutableList()
                }
                nextBatch = page.nextBatch
                loadError = null
            }.onFailure { error ->
                Timber.w(error, "Failed to load Setka feed (tab=%s)", selectedTab)
                if (reset) {
                    posts = persistentListOf()
                    nextBatch = null
                    loadError = error.message ?: error.javaClass.simpleName
                }
            }
            isLoading = false
            isLoadingMore = false
        }

        LaunchedEffect(feedTabSelected, selectedTab) {
            if (feedTabSelected && selectedTab != SetkaFeedTab.Channels) {
                fetchFeed(reset = true)
            }
        }

        return SetkaFeedState(
            selectedTab = selectedTab,
            posts = posts,
            publicChannelsState = channelsState,
            isLoading = isLoading,
            isLoadingMore = isLoadingMore,
            loadError = loadError,
            nextBatch = nextBatch,
            showSearchOverlay = showSearchOverlay,
            searchResults = searchResults,
            isSearching = isSearching,
            searchError = searchError,
            eventSink = { event ->
                when (event) {
                    is SetkaFeedEvents.SelectTab -> {
                        selectedTab = event.tab
                        if (event.tab != SetkaFeedTab.Channels) {
                            coroutineScope.launch { fetchFeed(reset = true) }
                        }
                    }
                    SetkaFeedEvents.LoadMore -> {
                        if (!isLoadingMore && nextBatch != null) {
                            coroutineScope.launch { fetchFeed(reset = false) }
                        }
                    }
                    SetkaFeedEvents.Retry -> coroutineScope.launch { fetchFeed(reset = true) }
                    SetkaFeedEvents.OpenSearch -> showSearchOverlay = true
                    SetkaFeedEvents.CloseSearch -> {
                        showSearchOverlay = false
                        searchResults = persistentListOf()
                        searchError = null
                        isSearching = false
                    }
                    is SetkaFeedEvents.PerformSearch -> {
                        val query = event.query.trim()
                        if (query.length < 2) {
                            searchResults = persistentListOf()
                            searchError = null
                            isSearching = false
                            return@SetkaFeedState
                        }
                        coroutineScope.launch {
                            isSearching = true
                            searchError = null
                            feedService.searchPosts(query = query)
                                .onSuccess { page ->
                                    searchResults = enrichWithMembership(page.posts)
                                }
                                .onFailure { error ->
                                    Timber.w(error, "Setka feed search failed")
                                    searchError = error.message ?: error.javaClass.simpleName
                                }
                            isSearching = false
                        }
                    }
                    is SetkaFeedEvents.ToggleSubscribe -> {
                        coroutineScope.launch {
                            if (matrixClient.getJoinedRoom(event.roomId) != null) {
                                return@launch
                            }
                            matrixClient.joinRoom(event.roomId).onFailure { error ->
                                Timber.w(error, "Failed to join channel ${event.roomId}")
                                return@launch
                            }
                            feedService.subscribe(event.roomId).onSuccess {
                                fun updateList(list: ImmutableList<SetkaFeedPost>) =
                                    list.map { post ->
                                        if (post.roomId == event.roomId) {
                                            post.copy(isMember = true, subscribed = true)
                                        } else {
                                            post
                                        }
                                    }.toImmutableList()
                                posts = updateList(posts)
                                searchResults = updateList(searchResults)
                            }
                        }
                    }
                    is SetkaFeedEvents.ToggleLike -> {
                        coroutineScope.launch {
                            val post = posts.find { it.eventId == event.eventId }
                                ?: searchResults.find { it.eventId == event.eventId }
                            if (post == null) return@launch
                            feedService.toggleLike(event.eventId).onSuccess { result ->
                                fun updateList(list: ImmutableList<SetkaFeedPost>) =
                                    list.map { item ->
                                        if (item.eventId == event.eventId) {
                                            item.copy(
                                                liked = result.liked,
                                                likeCount = result.likeCount,
                                            )
                                        } else {
                                            item
                                        }
                                    }.toImmutableList()
                                posts = updateList(posts)
                                searchResults = updateList(searchResults)
                            }
                            if (matrixClient.getJoinedRoom(post.roomId) == null) {
                                matrixClient.joinRoom(post.roomId).onFailure { error ->
                                    Timber.w(error, "Failed to join channel ${post.roomId} for reaction")
                                    return@launch
                                }
                            }
                            matrixClient.getJoinedRoom(post.roomId)?.liveTimeline?.toggleReaction(
                                emoji = "❤️",
                                eventOrTransactionId = EventOrTransactionId.Event(event.eventId),
                            )?.onFailure { error ->
                                Timber.w(error, "Failed to toggle Matrix reaction for ${event.eventId}")
                            }
                        }
                    }
                    is SetkaFeedEvents.RecordView -> {
                        coroutineScope.launch {
                            feedService.recordView(event.eventId).onSuccess { viewCount ->
                                posts = posts.map { post ->
                                    if (post.eventId == event.eventId) post.copy(viewCount = viewCount) else post
                                }.toImmutableList()
                            }
                        }
                    }
                    is SetkaFeedEvents.OpenPostComments,
                    is SetkaFeedEvents.SharePost,
                    is SetkaFeedEvents.OpenChannel -> Unit
                }
            },
        )
    }

    fun publicChannelsPresenter(): SetkaPublicChannelsPresenter = publicChannelsPresenter
}
