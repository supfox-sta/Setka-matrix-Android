/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.home.impl.channels

import io.element.android.features.roomdirectory.api.RoomDescription
import kotlinx.collections.immutable.ImmutableList

data class SetkaPublicChannelsState(
    val isLoading: Boolean,
    val channels: ImmutableList<RoomDescription>,
    val displayLoadMoreIndicator: Boolean,
    val eventSink: (SetkaPublicChannelsEvents) -> Unit,
) {
    companion object {
        fun hidden(eventSink: (SetkaPublicChannelsEvents) -> Unit) = SetkaPublicChannelsState(
            isLoading = false,
            channels = kotlinx.collections.immutable.persistentListOf(),
            displayLoadMoreIndicator = false,
            eventSink = eventSink,
        )
    }
}

sealed interface SetkaPublicChannelsEvents {
    data object LoadMore : SetkaPublicChannelsEvents
}
