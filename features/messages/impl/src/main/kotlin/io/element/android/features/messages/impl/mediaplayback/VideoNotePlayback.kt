/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.messages.impl.mediaplayback

import android.net.Uri
import androidx.media3.exoplayer.ExoPlayer
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.media.MediaSource
import kotlinx.coroutines.flow.StateFlow

interface VideoNotePlayback {
    val activeEventId: StateFlow<EventId?>
    val activeRoomId: StateFlow<RoomId?>
    val isSourceVisible: StateFlow<Boolean>
    val progress: StateFlow<Float>
    val isPlaying: StateFlow<Boolean>
    val isLoading: StateFlow<Boolean>
    val thumbnailSource: StateFlow<MediaSource?>
    val blurHash: StateFlow<String?>

    fun getPlayer(): ExoPlayer?
    fun isActive(eventId: EventId): Boolean
    fun attachSource(
        eventId: EventId,
        roomId: RoomId,
        uri: Uri,
        thumbnailSource: MediaSource?,
        blurHash: String?,
    )
    fun play()
    fun pause()
    fun seekToProgress(fraction: Float)
    fun setSourceVisible(visible: Boolean)
    fun stop()
}

internal object NoOpVideoNotePlayback : VideoNotePlayback {
    private val emptyEventId = kotlinx.coroutines.flow.MutableStateFlow<EventId?>(null)
    private val emptyRoomId = kotlinx.coroutines.flow.MutableStateFlow<RoomId?>(null)
    private val emptySourceVisible = kotlinx.coroutines.flow.MutableStateFlow(true)
    private val emptyProgress = kotlinx.coroutines.flow.MutableStateFlow(0f)
    private val emptyPlaying = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val emptyLoading = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val emptyThumbnail = kotlinx.coroutines.flow.MutableStateFlow<MediaSource?>(null)
    private val emptyBlurHash = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    override val activeEventId = emptyEventId
    override val activeRoomId = emptyRoomId
    override val isSourceVisible = emptySourceVisible
    override val progress = emptyProgress
    override val isPlaying = emptyPlaying
    override val isLoading = emptyLoading
    override val thumbnailSource = emptyThumbnail
    override val blurHash = emptyBlurHash

    override fun getPlayer(): ExoPlayer? = null
    override fun isActive(eventId: EventId): Boolean = false
    override fun attachSource(
        eventId: EventId,
        roomId: RoomId,
        uri: Uri,
        thumbnailSource: MediaSource?,
        blurHash: String?,
    ) = Unit
    override fun play() = Unit
    override fun pause() = Unit
    override fun seekToProgress(fraction: Float) = Unit
    override fun setSourceVisible(visible: Boolean) = Unit
    override fun stop() = Unit
}
