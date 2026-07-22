/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.messages.impl.mediaplayback

sealed interface RoomMediaPlaybackBarState {
    data object Hidden : RoomMediaPlaybackBarState

    data class Visible(
        val kind: RoomMediaPlaybackKind,
        val title: String,
        val progress: Float,
        val isPlaying: Boolean,
        val isLoading: Boolean,
        val eventSink: (RoomMediaPlaybackBarEvent) -> Unit,
    ) : RoomMediaPlaybackBarState
}

sealed interface RoomMediaPlaybackBarEvent {
    data object TogglePlayPause : RoomMediaPlaybackBarEvent
    data class Seek(val progress: Float) : RoomMediaPlaybackBarEvent
    data object Stop : RoomMediaPlaybackBarEvent
}
