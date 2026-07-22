/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.messages.impl.mediaplayback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.mediaplayer.api.MediaPlayer

private val EmptyMediaPlayerState = MediaPlayer.State(
    isReady = false,
    isPlaying = false,
    isEnded = false,
    mediaId = null,
    currentPosition = 0L,
    duration = null,
)

@Inject
class RoomMediaPlaybackBarPresenter(
    private val coordinator: RoomMediaPlaybackCoordinator,
) : Presenter<RoomMediaPlaybackBarState> {
    @Composable
    override fun present(): RoomMediaPlaybackBarState {
        val voiceMediaPlayer by coordinator.voiceMediaPlayer.collectAsState()
        val playerState by produceState(initialValue = EmptyMediaPlayerState, voiceMediaPlayer) {
            val player = voiceMediaPlayer
            if (player == null) {
                value = EmptyMediaPlayerState
            } else {
                player.state.collect { value = it }
            }
        }
        val videoPlayback = coordinator.videoNotePlayback
        val videoEventId by videoPlayback.activeEventId.collectAsState()
        val videoProgress by videoPlayback.progress.collectAsState()
        val videoIsPlaying by videoPlayback.isPlaying.collectAsState()
        val videoIsLoading by videoPlayback.isLoading.collectAsState()
        val voiceBarPinned by coordinator.voiceBarPinned.collectAsState()
        val videoBarPinned by coordinator.videoBarPinned.collectAsState()

        LaunchedEffect(
            videoEventId,
            playerState.mediaId,
            playerState.isReady,
            playerState.isPlaying,
            playerState.currentPosition,
            playerState.isEnded,
        ) {
            if (videoEventId != null) return@LaunchedEffect
            val hasVoiceMedia = playerState.mediaId != null && playerState.isReady
            val hasStartedPlayback = playerState.isPlaying ||
                playerState.currentPosition > 0L ||
                playerState.isEnded
            if (hasVoiceMedia && hasStartedPlayback) {
                coordinator.pinVoiceBar()
            }
        }

        LaunchedEffect(videoEventId) {
            if (videoEventId != null) {
                coordinator.pinVideoBar()
            } else {
                coordinator.unpinVideoBar()
            }
        }

        val voiceProgress = playerState.duration?.takeIf { it > 0L }?.let { duration ->
            (playerState.currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        } ?: 0f

        val voiceVisible = videoEventId == null &&
            playerState.mediaId != null &&
            playerState.isReady &&
            voiceBarPinned

        val activeSession = when {
            videoEventId != null && videoBarPinned -> {
                val eventId = videoEventId!!
                RoomMediaPlaybackSession(
                    kind = RoomMediaPlaybackKind.VideoNote,
                    eventId = eventId,
                    title = "",
                    progress = videoProgress,
                    isPlaying = videoIsPlaying,
                    isLoading = videoIsLoading,
                    controller = object : RoomMediaPlaybackController {
                        override fun togglePlayPause() {
                            if (videoIsPlaying) {
                                videoPlayback.pause()
                            } else {
                                videoPlayback.play()
                            }
                        }

                        override fun seekToProgress(progress: Float) {
                            videoPlayback.seekToProgress(progress)
                        }

                        override fun stop() {
                            coordinator.stopVideoNote(eventId)
                        }
                    },
                )
            }
            voiceVisible -> {
                val eventId = playerState.mediaId?.let(::EventId)
                val mediaPlayer = voiceMediaPlayer
                RoomMediaPlaybackSession(
                    kind = RoomMediaPlaybackKind.Voice,
                    eventId = eventId,
                    title = "",
                    progress = voiceProgress,
                    isPlaying = playerState.isPlaying,
                    isLoading = !playerState.isReady,
                    controller = object : RoomMediaPlaybackController {
                        override fun togglePlayPause() {
                            if (mediaPlayer == null) return
                            if (playerState.isPlaying) mediaPlayer.pause() else mediaPlayer.play()
                        }

                        override fun seekToProgress(progress: Float) {
                            val duration = playerState.duration ?: return
                            mediaPlayer?.seekTo((progress.coerceIn(0f, 1f) * duration).toLong())
                        }

                        override fun stop() {
                            coordinator.stopVoice(eventId)
                        }
                    },
                )
            }
            else -> null
        }

        val session = activeSession ?: return RoomMediaPlaybackBarState.Hidden

        fun handleEvent(event: RoomMediaPlaybackBarEvent) {
            when (event) {
                RoomMediaPlaybackBarEvent.TogglePlayPause -> session.controller.togglePlayPause()
                is RoomMediaPlaybackBarEvent.Seek -> session.controller.seekToProgress(event.progress)
                RoomMediaPlaybackBarEvent.Stop -> session.controller.stop()
            }
        }

        return RoomMediaPlaybackBarState.Visible(
            kind = session.kind,
            title = session.title,
            progress = session.progress,
            isPlaying = session.isPlaying,
            isLoading = session.isLoading,
            eventSink = ::handleEvent,
        )
    }
}
