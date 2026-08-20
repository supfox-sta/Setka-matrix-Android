/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl

import io.element.android.features.home.impl.feed.SetkaFeedState
import io.element.android.features.home.impl.roomlist.RoomListState
import io.element.android.features.home.impl.spacefilters.SpaceFiltersState
import io.element.android.features.home.impl.spaces.HomeSpacesState
import io.element.android.features.home.impl.model.HomeContact
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.logout.api.direct.DirectLogoutState
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.libraries.matrix.api.setka.SetkaServerRecoverySyncState
import io.element.android.libraries.setkalite.api.SetkaLiteState
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.features.home.impl.channels.SetkaChannelDiscoveryLoader
import io.element.android.features.home.impl.channels.SetkaChannelDiscoveryPage
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class HomeState(
    /**
     * The current user of this session, in case of multiple accounts, will contains 3 items, with the
     * current user in the middle.
     */
    val currentUserAndNeighbors: ImmutableList<MatrixUser>,
    val showAvatarIndicator: Boolean,
    val hasNetworkConnection: Boolean,
    val currentHomeNavigationBarItem: HomeNavigationBarItem,
    val roomListState: RoomListState,
    val homeSpacesState: HomeSpacesState,
    val contacts: ImmutableList<HomeContact>,
    val snackbarMessage: SnackbarMessage?,
    val canReportBug: Boolean,
    val directLogoutState: DirectLogoutState,
    val setkaPlusState: HomeSetkaPlusState = HomeSetkaPlusState(),
    val statusEmojiState: HomeStatusEmojiState = HomeStatusEmojiState(),
    val statusEmojiByUserId: kotlinx.collections.immutable.ImmutableMap<String, String> =
        kotlinx.collections.immutable.persistentMapOf(),
    val setkaSyncState: SetkaServerRecoverySyncState? = null,
    val setkaLiteState: SetkaLiteState = SetkaLiteState(),
    val showTransferQueueSheet: Boolean = false,
    val canCreateSetkaChannel: Boolean = false,
    val showSetkaChannelsTab: Boolean = false,
    val setkaFeedState: SetkaFeedState? = null,
    val loadPublicChannelsForDiscovery: SetkaChannelDiscoveryLoader = SetkaChannelDiscoveryLoader { _, _ ->
        Result.success(SetkaChannelDiscoveryPage())
    },
    val recentRoomSummaries: ImmutableList<RoomListRoomSummary> = kotlinx.collections.immutable.persistentListOf(),
    val eventSink: (HomeEvent) -> Unit,
) {
    val isBackHandlerEnabled = currentHomeNavigationBarItem != HomeNavigationBarItem.Chats || roomListState.spaceFiltersState is SpaceFiltersState.Selected
    val displayActions = currentHomeNavigationBarItem == HomeNavigationBarItem.Chats
    val displayRoomListFilters = currentHomeNavigationBarItem == HomeNavigationBarItem.Chats && roomListState.displayFilters
    val showNavigationBar = true
}
