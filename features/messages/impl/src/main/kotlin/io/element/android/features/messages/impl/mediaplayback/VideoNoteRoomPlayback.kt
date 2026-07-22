/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.messages.impl.mediaplayback

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.media.MediaSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@SingleIn(SessionScope::class)
class VideoNoteRoomPlayback @Inject constructor(
    @ApplicationContext private val context: Context,
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
) : VideoNotePlayback {
    private var player: ExoPlayer? = null
    private var progressJob: Job? = null

    private val _activeEventId = MutableStateFlow<EventId?>(null)
    override val activeEventId: StateFlow<EventId?> = _activeEventId.asStateFlow()

    private val _activeRoomId = MutableStateFlow<RoomId?>(null)
    override val activeRoomId: StateFlow<RoomId?> = _activeRoomId.asStateFlow()

    private val _isSourceVisible = MutableStateFlow(true)
    override val isSourceVisible: StateFlow<Boolean> = _isSourceVisible.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    override val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _thumbnailSource = MutableStateFlow<MediaSource?>(null)
    override val thumbnailSource: StateFlow<MediaSource?> = _thumbnailSource.asStateFlow()

    private val _blurHash = MutableStateFlow<String?>(null)
    override val blurHash: StateFlow<String?> = _blurHash.asStateFlow()

    override fun getPlayer(): ExoPlayer? = player

    override fun isActive(eventId: EventId): Boolean = _activeEventId.value == eventId

    fun isActivePlayback(): Boolean {
        val id = _activeEventId.value ?: return false
        return _isPlaying.value || _progress.value > 0f || (_isLoading.value && id != null)
    }

    override fun attachSource(
        eventId: EventId,
        roomId: RoomId,
        uri: Uri,
        thumbnailSource: MediaSource?,
        blurHash: String?,
    ) {
        if (_activeEventId.value != eventId || player == null) {
            releasePlayerInternal()
            player = ExoPlayer.Builder(context.applicationContext).build().apply {
                repeatMode = ExoPlayer.REPEAT_MODE_OFF
                volume = 1f
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                playWhenReady = false
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            sessionCoroutineScope.launch { handlePlaybackEnded() }
                        }
                    }
                })
            }
            _activeEventId.value = eventId
            _activeRoomId.value = roomId
            _progress.value = 0f
            _isLoading.value = true
            _isPlaying.value = false
        }
        _activeRoomId.value = roomId
        _thumbnailSource.value = thumbnailSource
        _blurHash.value = blurHash
        _isSourceVisible.value = true
        startProgressUpdates()
    }

    override fun play() {
        val p = player ?: return
        p.volume = 1f
        p.playWhenReady = true
        p.play()
        _isPlaying.value = true
        _isLoading.value = false
        startProgressUpdates()
    }

    override fun pause() {
        player?.let {
            it.playWhenReady = false
            it.pause()
        }
        _isPlaying.value = false
        _isLoading.value = false
    }

    override fun seekToProgress(fraction: Float) {
        val p = player ?: return
        val durationMs = p.duration
        if (durationMs <= 0) return
        p.seekTo((fraction.coerceIn(0f, 1f) * durationMs).toLong())
        _progress.value = (p.currentPosition.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        startProgressUpdates()
    }

    override fun setSourceVisible(visible: Boolean) {
        _isSourceVisible.value = visible
    }

    override fun stop() {
        val eventId = _activeEventId.value
        releasePlayerInternal()
        _activeEventId.value = null
        _activeRoomId.value = null
        _isSourceVisible.value = true
        _progress.value = 0f
        _isPlaying.value = false
        _isLoading.value = false
        _thumbnailSource.value = null
        _blurHash.value = null
    }

    private fun handlePlaybackEnded() {
        if (_activeEventId.value == null) return
        _isPlaying.value = false
        _isLoading.value = false
        _progress.value = 1f
        stop()
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = sessionCoroutineScope.launch {
            while (isActive) {
                val p = player
                val eventId = _activeEventId.value
                if (p == null || eventId == null) break
                val durationMs = p.duration
                if (durationMs > 0 && p.playbackState == Player.STATE_ENDED) {
                    handlePlaybackEnded()
                    break
                }
                val durationMsCoerced = durationMs.coerceAtLeast(1)
                val posMs = p.currentPosition.coerceAtLeast(0)
                _progress.value = (posMs.toFloat() / durationMsCoerced.toFloat()).coerceIn(0f, 1f)
                _isLoading.value = p.playbackState == Player.STATE_BUFFERING
                if (p.isPlaying) {
                    _isPlaying.value = true
                }
                if (!_isPlaying.value && p.playbackState != Player.STATE_BUFFERING) {
                    delay(50.milliseconds)
                    continue
                }
                delay(50.milliseconds)
            }
        }
    }

    private fun releasePlayerInternal() {
        progressJob?.cancel()
        progressJob = null
        player?.release()
        player = null
    }
}
