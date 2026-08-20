/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.home.impl.feed

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.home.impl.R
import io.element.android.features.home.impl.channels.SetkaPublicChannelsContentView
import io.element.android.features.home.impl.channels.SetkaPublicChannelsState
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.FilledTextField
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Surface
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.ui.media.MediaRequestData
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun SetkaFeedView(
    state: SetkaFeedState,
    channelsState: SetkaPublicChannelsState?,
    contentPadding: PaddingValues,
    onOpenPostComments: (RoomId, EventId) -> Unit,
    onSharePost: (RoomId, EventId) -> Unit,
    onOpenChannel: (RoomId) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.showSearchOverlay) {
        SetkaFeedSearchOverlay(
            results = state.searchResults,
            isSearching = state.isSearching,
            searchError = state.searchError,
            onSearch = { state.eventSink(SetkaFeedEvents.PerformSearch(it)) },
            onDismiss = { state.eventSink(SetkaFeedEvents.CloseSearch) },
            onOpenPostComments = onOpenPostComments,
            onSharePost = onSharePost,
            onLike = { state.eventSink(SetkaFeedEvents.ToggleLike(it)) },
            onSubscribe = { roomId, subscribed ->
                state.eventSink(SetkaFeedEvents.ToggleSubscribe(roomId, subscribed))
            },
            onOpenChannel = onOpenChannel,
            modifier = modifier.padding(contentPadding),
        )
        return
    }

    val pagerState = rememberPagerState(
        initialPage = state.selectedTab.ordinal,
        pageCount = { SetkaFeedTab.entries.size },
    )

    LaunchedEffect(state.selectedTab) {
        if (pagerState.currentPage != state.selectedTab.ordinal) {
            pagerState.animateScrollToPage(state.selectedTab.ordinal)
        }
    }

    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) {
            val tab = SetkaFeedTab.entries[pagerState.currentPage]
            if (tab != state.selectedTab) {
                state.eventSink(SetkaFeedEvents.SelectTab(tab))
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScrollableTabRow(
                    selectedTabIndex = state.selectedTab.ordinal,
                    edgePadding = 0.dp,
                    modifier = Modifier.weight(1f),
                    containerColor = ElementTheme.colors.bgCanvasDefault,
                    contentColor = ElementTheme.colors.textPrimary,
                ) {
                    SetkaFeedTab.entries.forEach { tab ->
                        Tab(
                            selected = state.selectedTab == tab,
                            onClick = { state.eventSink(SetkaFeedEvents.SelectTab(tab)) },
                            text = {
                                Text(
                                    text = when (tab) {
                                        SetkaFeedTab.Recommended -> stringResource(R.string.screen_home_feed_tab_recommended)
                                        SetkaFeedTab.Subscriptions -> stringResource(R.string.screen_home_feed_tab_subscriptions)
                                        SetkaFeedTab.Channels -> stringResource(R.string.screen_home_feed_tab_channels)
                                    },
                                )
                            },
                        )
                    }
                }
                IconButton(onClick = { state.eventSink(SetkaFeedEvents.OpenSearch) }) {
                    Icon(
                        imageVector = CompoundIcons.Search(),
                        contentDescription = stringResource(R.string.screen_home_feed_search),
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                beyondViewportPageCount = 1,
            ) { page ->
                when (SetkaFeedTab.entries[page]) {
                    SetkaFeedTab.Channels -> {
                        if (channelsState != null) {
                            SetkaPublicChannelsContentView(
                                state = channelsState,
                                onChannelClick = onOpenChannel,
                                contentPadding = PaddingValues(top = 8.dp),
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    SetkaFeedTab.Recommended -> {
                        SetkaFeedPostsPage(
                            tab = SetkaFeedTab.Recommended,
                            posts = state.posts,
                            isLoading = state.isLoading,
                            loadError = state.loadError,
                            nextBatch = state.nextBatch,
                            eventSink = state.eventSink,
                            onOpenPostComments = onOpenPostComments,
                            onSharePost = onSharePost,
                            onOpenChannel = onOpenChannel,
                        )
                    }
                    SetkaFeedTab.Subscriptions -> {
                        SetkaFeedPostsPage(
                            tab = SetkaFeedTab.Subscriptions,
                            posts = state.posts,
                            isLoading = state.isLoading,
                            loadError = state.loadError,
                            nextBatch = state.nextBatch,
                            eventSink = state.eventSink,
                            onOpenPostComments = onOpenPostComments,
                            onSharePost = onSharePost,
                            onOpenChannel = onOpenChannel,
                        )
                    }
                    else -> Unit
                }
            }
        }

    }
}

@Composable
private fun SetkaFeedPostsPage(
    tab: SetkaFeedTab,
    posts: List<SetkaFeedPost>,
    isLoading: Boolean,
    loadError: String?,
    nextBatch: String?,
    eventSink: (SetkaFeedEvents) -> Unit,
    onOpenPostComments: (RoomId, EventId) -> Unit,
    onSharePost: (RoomId, EventId) -> Unit,
    onOpenChannel: (RoomId) -> Unit,
) {
    when {
        isLoading && posts.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        loadError != null && posts.isEmpty() -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.screen_home_feed_load_error, loadError),
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                TextButton(
                    text = stringResource(CommonStrings.action_retry),
                    onClick = { eventSink(SetkaFeedEvents.Retry) },
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        posts.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(
                        when (tab) {
                            SetkaFeedTab.Subscriptions -> R.string.screen_home_feed_subscriptions_empty
                            else -> R.string.screen_home_feed_empty
                        }
                    ),
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
        }
        else -> {
            val lazyListState = rememberLazyListState()
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(posts, key = { it.eventId.value }) { post ->
                    LaunchedEffect(post.eventId) {
                        eventSink(SetkaFeedEvents.RecordView(post.eventId))
                    }
                    SetkaFeedPostCard(
                        post = post,
                        onChannelClick = { onOpenChannel(post.roomId) },
                        onSubscribeClick = {
                            if (!post.isMember) {
                                eventSink(SetkaFeedEvents.ToggleSubscribe(post.roomId, post.subscribed))
                            }
                        },
                        onLikeClick = { eventSink(SetkaFeedEvents.ToggleLike(post.eventId)) },
                        onCommentClick = { onOpenPostComments(post.roomId, post.eventId) },
                        onShareClick = { onSharePost(post.roomId, post.eventId) },
                        onOpenPost = { onOpenPostComments(post.roomId, post.eventId) },
                    )
                }
                if (nextBatch != null) {
                    item(key = "load_more") {
                        TextButton(
                            text = stringResource(R.string.screen_home_feed_load_more),
                            onClick = { eventSink(SetkaFeedEvents.LoadMore) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetkaFeedSearchOverlay(
    results: List<SetkaFeedPost>,
    isSearching: Boolean,
    searchError: String?,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    onOpenPostComments: (RoomId, EventId) -> Unit,
    onSharePost: (RoomId, EventId) -> Unit,
    onLike: (EventId) -> Unit,
    onSubscribe: (RoomId, Boolean) -> Unit,
    onOpenChannel: (RoomId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val queryState = rememberTextFieldState()
    var debouncedQuery by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(queryState) {
        snapshotFlow { queryState.text.toString() }
            .debounce(350)
            .distinctUntilChanged()
            .collectLatest { debouncedQuery = it.trim() }
    }

    LaunchedEffect(debouncedQuery) {
        onSearch(debouncedQuery)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ElementTheme.colors.bgCanvasDefault,
                ),
                navigationIcon = { BackButton(onClick = onDismiss) },
                title = {
                    val focusRequester = remember { FocusRequester() }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = ElementTheme.colors.bgSubtleSecondary,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        FilledTextField(
                            state = queryState,
                            placeholder = { Text(stringResource(R.string.screen_home_feed_search_hint)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            lineLimits = TextFieldLineLimits.SingleLine,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = ElementTheme.colors.bgSubtleSecondary,
                                unfocusedContainerColor = ElementTheme.colors.bgSubtleSecondary,
                            ),
                        )
                    }
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (isSearching) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            when {
                searchError != null && results.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.screen_home_feed_load_error, searchError),
                                style = ElementTheme.typography.fontBodyMdRegular,
                                color = ElementTheme.colors.textSecondary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                debouncedQuery.length >= 2 && results.isEmpty() && !isSearching -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.screen_home_feed_search_empty),
                            style = ElementTheme.typography.fontBodyMdRegular,
                            color = ElementTheme.colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                debouncedQuery.length < 2 -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.screen_home_feed_search_hint),
                            style = ElementTheme.typography.fontBodyMdRegular,
                            color = ElementTheme.colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(results, key = { it.eventId.value }) { post ->
                            SetkaFeedPostCard(
                                post = post,
                                onChannelClick = { onOpenChannel(post.roomId) },
                                onSubscribeClick = {
                                    if (!post.isMember) {
                                        onSubscribe(post.roomId, post.subscribed)
                                    }
                                },
                                onLikeClick = { onLike(post.eventId) },
                                onCommentClick = { onOpenPostComments(post.roomId, post.eventId) },
                                onShareClick = { onSharePost(post.roomId, post.eventId) },
                                onOpenPost = { onOpenPostComments(post.roomId, post.eventId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SetkaFeedPostCard(
    post: SetkaFeedPost,
    onChannelClick: () -> Unit,
    onSubscribeClick: () -> Unit,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit,
    onOpenPost: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ElementTheme.colors.bgSubtleSecondary)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(
                avatarData = AvatarData(
                    id = post.roomId.value,
                    name = post.channelName,
                    url = post.channelAvatar,
                    size = AvatarSize.RoomListItem,
                ),
                avatarType = AvatarType.Room(),
                modifier = Modifier.clickable(onClick = onChannelClick),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
                    .clickable(onClick = onChannelClick),
            ) {
                Text(
                    text = post.channelName ?: post.roomId.value,
                    style = ElementTheme.typography.fontBodyMdMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                post.categoryLabel?.let { label ->
                    Text(
                        text = label,
                        style = ElementTheme.typography.fontBodySmRegular,
                        color = ElementTheme.colors.textSecondary,
                    )
                }
            }
            TextButton(
                text = stringResource(
                    if (post.isMember) {
                        R.string.screen_home_feed_in_channel
                    } else {
                        R.string.screen_home_feed_join
                    }
                ),
                onClick = onSubscribeClick,
                enabled = !post.isMember,
            )
        }

        val showMedia = post.mediaMxc != null &&
            (post.msgtype == "m.image" || post.msgtype == "m.video")
        if (showMedia && post.mediaMxc != null) {
            Spacer(modifier = Modifier.height(8.dp))
            SetkaFeedPostMedia(
                mediaMxc = post.mediaMxc,
                onOpenPost = onOpenPost,
                onDoubleTapLike = onLikeClick,
            )
        }

        if (post.body.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = post.body,
                style = ElementTheme.typography.fontBodyMdRegular,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(onClick = onOpenPost),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .clickable(onClick = onLikeClick)
                    .padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (post.liked) CompoundIcons.FavouriteSolid() else CompoundIcons.Favourite(),
                    contentDescription = stringResource(R.string.screen_home_feed_like),
                    tint = if (post.liked) ElementTheme.colors.textCriticalPrimary else ElementTheme.colors.iconSecondary,
                    modifier = Modifier.size(24.dp),
                )
                if (post.likeCount > 0) {
                    Text(
                        text = post.likeCount.toString(),
                        modifier = Modifier.padding(start = 4.dp),
                        style = ElementTheme.typography.fontBodySmRegular,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .clickable(onClick = onCommentClick)
                    .padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = CompoundIcons.Chat(),
                    contentDescription = stringResource(R.string.screen_home_feed_comment),
                    modifier = Modifier.size(24.dp),
                )
                if (post.commentCount > 0) {
                    Text(
                        text = post.commentCount.toString(),
                        modifier = Modifier.padding(start = 4.dp),
                        style = ElementTheme.typography.fontBodySmRegular,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .clickable(onClick = onShareClick)
                    .padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = CompoundIcons.ShareAndroid(),
                    contentDescription = stringResource(R.string.screen_home_feed_share),
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = CompoundIcons.VisibilityOn(),
                    contentDescription = stringResource(R.string.screen_home_feed_views),
                    modifier = Modifier.size(24.dp),
                    tint = ElementTheme.colors.iconSecondary,
                )
                Text(
                    text = post.viewCount.toString(),
                    modifier = Modifier.padding(start = 4.dp),
                    style = ElementTheme.typography.fontBodySmRegular,
                    color = ElementTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun SetkaFeedPostMedia(
    mediaMxc: String,
    onOpenPost: () -> Unit,
    onDoubleTapLike: () -> Unit,
) {
    var showHeart by remember { mutableStateOf(false) }
    val heartAlpha by animateFloatAsState(
        targetValue = if (showHeart) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "feedHeartAlpha",
    )

    LaunchedEffect(showHeart) {
        if (showHeart) {
            delay(700)
            showHeart = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(onOpenPost, onDoubleTapLike) {
                detectTapGestures(
                    onTap = { onOpenPost() },
                    onDoubleTap = {
                        showHeart = true
                        onDoubleTapLike()
                    },
                )
            },
    ) {
        AsyncImage(
            model = MediaRequestData(
                source = MediaSource(mediaMxc),
                kind = MediaRequestData.Kind.Content,
            ),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth,
        )
        if (heartAlpha > 0f) {
            Icon(
                imageVector = CompoundIcons.FavouriteSolid(),
                contentDescription = null,
                tint = Color.Red.copy(alpha = 0.45f + 0.55f * heartAlpha),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .graphicsLayer { alpha = heartAlpha },
            )
        }
    }
}
