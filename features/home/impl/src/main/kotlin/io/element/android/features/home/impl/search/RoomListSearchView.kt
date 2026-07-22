/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.home.impl.components.RecentRoomsSection
import io.element.android.features.home.impl.components.RoomSummaryRow
import io.element.android.features.home.impl.contentType
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.home.impl.roomlist.RoomListEvent
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.FilledTextField
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.designsystem.theme.components.Surface
import io.element.android.libraries.designsystem.theme.LocalSetkaCustomization
import io.element.android.libraries.designsystem.utils.OnVisibleRangeChangeEffect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.ui.model.getAvatarData
import io.element.android.libraries.matrix.ui.model.getBestName
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableList
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import io.element.android.features.home.impl.R

@Composable
internal fun RoomListSearchView(
    state: RoomListSearchState,
    hideInvitesAvatars: Boolean,
    eventSink: (RoomListEvent) -> Unit,
    onRoomClick: (RoomId) -> Unit,
    onUserClick: (UserId) -> Unit,
    recentRoomSummaries: ImmutableList<RoomListRoomSummary> = kotlinx.collections.immutable.persistentListOf(),
    backgroundColor: Color = ElementTheme.colors.bgCanvasDefault,
    transparentBackground: Boolean = false,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = state.isSearchActive) {
        state.eventSink(RoomListSearchEvent.ToggleSearchVisibility)
    }
    val customization = LocalSetkaCustomization.current
    val enableAnimations = customization.enableChatAnimations

    AnimatedVisibility(
        visible = state.isSearchActive,
        enter = if (enableAnimations) fadeIn() else EnterTransition.None,
        exit = if (enableAnimations) fadeOut() else ExitTransition.None,
    ) {
        Column(modifier = modifier) {
            RoomListSearchContent(
                state = state,
                hideInvitesAvatars = hideInvitesAvatars,
                onRoomClick = onRoomClick,
                onUserClick = onUserClick,
                eventSink = eventSink,
                recentRoomSummaries = recentRoomSummaries,
                backgroundColor = backgroundColor,
                transparentBackground = transparentBackground,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomListSearchContent(
    state: RoomListSearchState,
    hideInvitesAvatars: Boolean,
    eventSink: (RoomListEvent) -> Unit,
    onRoomClick: (RoomId) -> Unit,
    onUserClick: (UserId) -> Unit,
    recentRoomSummaries: ImmutableList<RoomListRoomSummary>,
    backgroundColor: Color,
    transparentBackground: Boolean,
) {
    fun onBackButtonClick() {
        state.eventSink(RoomListSearchEvent.ToggleSearchVisibility)
    }

    fun onRoomClick(room: RoomListRoomSummary) {
        onRoomClick(room.roomId)
    }
    Scaffold(
        containerColor = if (transparentBackground) Color.Transparent else backgroundColor,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (transparentBackground) Color.Transparent else backgroundColor,
                    scrolledContainerColor = if (transparentBackground) Color.Transparent else backgroundColor,
                ),
                navigationIcon = { BackButton(onClick = ::onBackButtonClick) },
                title = {
                    // The stateSaver will keep the selection state when returning to this UI
                    val focusRequester = remember { FocusRequester() }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = backgroundColor.copy(alpha = 0.94f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        FilledTextField(
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            state = state.query,
                            lineLimits = TextFieldLineLimits.SingleLine,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                errorIndicatorColor = Color.Transparent,
                            ),
                            trailingIcon = if (state.query.text.isNotEmpty()) {
                                {
                                    IconButton(onClick = { state.eventSink(RoomListSearchEvent.ClearQuery) }) {
                                        Icon(
                                            imageVector = CompoundIcons.Close(),
                                            contentDescription = stringResource(CommonStrings.action_cancel)
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                        )
                    }

                    LaunchedEffect(Unit) {
                        if (!focusRequester.restoreFocusedChild()) {
                            focusRequester.requestFocus()
                        }
                        focusRequester.saveFocusedChild()
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .let {
                    if (transparentBackground) it else it.background(backgroundColor)
                }
                .padding(padding)
                .consumeWindowInsets(padding)
        ) {
            RoomListSearchFilterRow(
                selected = state.filter,
                onSelect = { state.eventSink(RoomListSearchEvent.SetFilter(it)) },
            )
            val lazyListState = rememberLazyListState()
            OnVisibleRangeChangeEffect(lazyListState) { visibleRange ->
                state.eventSink(RoomListSearchEvent.UpdateVisibleRange(visibleRange))
            }
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.query.text.isEmpty() && recentRoomSummaries.isNotEmpty()) {
                    item(key = "recent-chats-section") {
                        RecentRoomsSection(
                            recentRoomSummaries = recentRoomSummaries,
                            onRoomClick = { room -> onRoomClick(room.roomId) },
                        )
                    }
                }
                items(
                    items = state.results,
                    key = { item ->
                        when (item) {
                            is RoomListSearchResultItem.Room -> "room-${item.summary.id}"
                            is RoomListSearchResultItem.User -> "user-${item.matrixUser.userId.value}"
                        }
                    },
                ) { item ->
                    when (item) {
                        is RoomListSearchResultItem.Room -> {
                            RoomSummaryRow(
                                room = item.summary,
                                hideInviteAvatars = hideInvitesAvatars,
                                isInviteSeen = false,
                                searchSubtitle = item.subtitle,
                                onClick = ::onRoomClick,
                                eventSink = eventSink,
                            )
                        }
                        is RoomListSearchResultItem.User -> {
                            SearchUserRow(
                                item = item,
                                onClick = { onUserClick(item.matrixUser.userId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchUserRow(
    item: RoomListSearchResultItem.User,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            avatarData = item.matrixUser.getAvatarData(io.element.android.libraries.designsystem.components.avatar.AvatarSize.RoomListItem),
            avatarType = AvatarType.User,
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = item.matrixUser.getBestName(),
                style = ElementTheme.typography.fontBodyLgMedium,
            )
            item.presenceText?.let { presence ->
                Text(
                    text = presence,
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textSecondary,
                )
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun RoomListSearchFilterRow(
    selected: RoomListSearchFilter,
    onSelect: (RoomListSearchFilter) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RoomListSearchFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                label = {
                    Text(
                        stringResource(
                            when (filter) {
                                RoomListSearchFilter.All -> R.string.screen_home_search_filter_all
                                RoomListSearchFilter.People -> R.string.screen_home_search_filter_people
                                RoomListSearchFilter.Channels -> R.string.screen_home_search_filter_channels
                                RoomListSearchFilter.Spaces -> R.string.screen_home_search_filter_spaces
                            }
                        )
                    )
                },
            )
        }
    }
}

@PreviewsDayNight
@Composable
internal fun RoomListSearchContentPreview(@PreviewParameter(RoomListSearchStateProvider::class) state: RoomListSearchState) = ElementPreview {
    RoomListSearchContent(
        state = state,
        hideInvitesAvatars = false,
        onRoomClick = {},
        onUserClick = {},
        eventSink = {},
        recentRoomSummaries = kotlinx.collections.immutable.persistentListOf(),
        backgroundColor = ElementTheme.colors.bgCanvasDefault,
        transparentBackground = false,
    )
}
