/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.messages.impl.mediaplayback

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.mediaplayer.api.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class RoomMediaPlaybackKind {
    Voice,
    VideoNote,
}

interface RoomMediaPlaybackController {
    fun togglePlayPause()
    fun seekToProgress(progress: Float)
    fun stop()
}

data class RoomMediaPlaybackSession(
    val kind: RoomMediaPlaybackKind,
    val eventId: EventId?,
    val title: String,
    val progress: Float,
    val isPlaying: Boolean,
    val isLoading: Boolean,
    val controller: RoomMediaPlaybackController,
)

@SingleIn(SessionScope::class)
class RoomMediaPlaybackCoordinator @Inject constructor(
    val videoNotePlayback: VideoNotePlayback,
) {
    private val _voiceMediaPlayer = MutableStateFlow<MediaPlayer?>(null)
    val voiceMediaPlayer: StateFlow<MediaPlayer?> = _voiceMediaPlayer.asStateFlow()

    fun registerVoiceMediaPlayer(mediaPlayer: MediaPlayer) {
        _voiceMediaPlayer.value = mediaPlayer
    }

    fun unregisterVoiceMediaPlayer() {
        _voiceMediaPlayer.value = null
    }

    fun pauseVoicePlayback() {
        _voiceMediaPlayer.value?.pause()
        _voiceMediaPlayer.value?.seekTo(0)
    }

    fun pinVoiceBar() {
        _voiceBarPinned.value = true
    }

    fun unpinVoiceBar() {
        _voiceBarPinned.value = false
    }

    fun pinVideoBar() {
        _videoBarPinned.value = true
    }

    fun unpinVideoBar() {
        _videoBarPinned.value = false
    }

    private val _session = MutableStateFlow<RoomMediaPlaybackSession?>(null)
    val session: StateFlow<RoomMediaPlaybackSession?> = _session.asStateFlow()

    private val _voiceBarPinned = MutableStateFlow(false)
    val voiceBarPinned: StateFlow<Boolean> = _voiceBarPinned.asStateFlow()

    private val _videoBarPinned = MutableStateFlow(false)
    val videoBarPinned: StateFlow<Boolean> = _videoBarPinned.asStateFlow()

    fun showVoiceSession(
        eventId: EventId?,
        title: String,
        progress: Float,
        isPlaying: Boolean,
        isLoading: Boolean,
        controller: RoomMediaPlaybackController,
    ) {
        _session.value = RoomMediaPlaybackSession(
            kind = RoomMediaPlaybackKind.Voice,
            eventId = eventId,
            title = title,
            progress = progress,
            isPlaying = isPlaying,
            isLoading = isLoading,
            controller = controller,
        )
    }

    fun showVideoNoteSession(
        eventId: EventId,
        title: String,
        progress: Float,
        isPlaying: Boolean,
        isLoading: Boolean,
        controller: RoomMediaPlaybackController,
    ) {
        pauseVoicePlayback()
        _session.value = RoomMediaPlaybackSession(
            kind = RoomMediaPlaybackKind.VideoNote,
            eventId = eventId,
            title = title,
            progress = progress,
            isPlaying = isPlaying,
            isLoading = isLoading,
            controller = controller,
        )
    }

    fun updateSession(
        progress: Float? = null,
        isPlaying: Boolean? = null,
        isLoading: Boolean? = null,
    ) {
        _session.update { current ->
            current?.copy(
                progress = progress ?: current.progress,
                isPlaying = isPlaying ?: current.isPlaying,
                isLoading = isLoading ?: current.isLoading,
            )
        }
    }

    fun clearSession(eventId: EventId? = null) {
        if (eventId == null || _session.value?.eventId == eventId) {
            _session.value = null
        }
    }

    fun stopVoice(eventId: EventId? = null) {
        pauseVoicePlayback()
        unpinVoiceBar()
        clearSession(eventId)
    }

    fun stopVideoNote(eventId: EventId? = null) {
        if (eventId == null || videoNotePlayback.activeEventId.value == eventId) {
            videoNotePlayback.stop()
        }
        unpinVideoBar()
        clearSession(eventId)
    }
}
