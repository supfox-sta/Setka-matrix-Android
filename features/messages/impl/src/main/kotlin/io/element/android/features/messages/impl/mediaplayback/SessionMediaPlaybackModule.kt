/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.messages.impl.mediaplayback

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.di.SessionScope

@ContributesTo(SessionScope::class)
@BindingContainer
interface SessionMediaPlaybackModule {
    @Binds
    fun bindVideoNotePlayback(playback: VideoNoteRoomPlayback): VideoNotePlayback

    @Binds
    fun bindRoomMediaPlaybackBarPresenter(presenter: RoomMediaPlaybackBarPresenter): Presenter<RoomMediaPlaybackBarState>
}
