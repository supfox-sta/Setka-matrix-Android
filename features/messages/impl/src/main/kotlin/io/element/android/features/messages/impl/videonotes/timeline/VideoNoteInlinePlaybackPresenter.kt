/*
 * Copyright (c) 2026 Element Creations Ltd.
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.videonotes.timeline

import android.net.Uri
import androidx.core.net.toUri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import io.element.android.features.messages.impl.timeline.di.TimelineItemEventContentKey
import io.element.android.features.messages.impl.timeline.di.TimelineItemPresenterFactory
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemVideoContent
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.core.extensions.mapCatchingExceptions
import io.element.android.libraries.di.CacheDirectory
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.matrix.api.media.MatrixMediaLoader
import io.element.android.libraries.matrix.api.mxc.MxcTools
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

@BindingContainer
@ContributesTo(RoomScope::class)
interface VideoNoteInlinePlaybackPresenterModule {
    @Binds
    @IntoMap
    @TimelineItemEventContentKey(TimelineItemVideoContent::class)
    fun bindVideoNoteInlinePlaybackPresenterFactory(factory: VideoNoteInlinePlaybackPresenter.Factory): TimelineItemPresenterFactory<*, *>
}

data class VideoNoteInlinePlaybackState(
    val localUri: Uri?,
    val isPreparing: Boolean,
    val error: Throwable?,
    val eventSink: (VideoNoteInlinePlaybackEvent) -> Unit,
) {
    companion object {
        fun empty(): VideoNoteInlinePlaybackState = VideoNoteInlinePlaybackState(
            localUri = null,
            isPreparing = false,
            error = null,
            eventSink = {},
        )
    }
}

sealed interface VideoNoteInlinePlaybackEvent {
    data object Prepare : VideoNoteInlinePlaybackEvent
}

/**
 * Downloads (and caches) the video-note media locally so it can be played inline.
 *
 * Telegram-like UX expects a direct, fast, in-place playback; `MediaSource.url` is often `mxc://...`
 * which is not playable by ExoPlayer directly.
 */
@AssistedInject
class VideoNoteInlinePlaybackPresenter(
    @CacheDirectory private val cacheDir: File,
    private val mxcTools: MxcTools,
    private val matrixMediaLoader: MatrixMediaLoader,
    @Assisted private val content: TimelineItemVideoContent,
) : Presenter<VideoNoteInlinePlaybackState> {
    @AssistedFactory
    fun interface Factory : TimelineItemPresenterFactory<TimelineItemVideoContent, VideoNoteInlinePlaybackState> {
        override fun create(content: TimelineItemVideoContent): VideoNoteInlinePlaybackPresenter
    }

    @Composable
    override fun present(): VideoNoteInlinePlaybackState {
        if (!content.isVideoNote) return VideoNoteInlinePlaybackState.empty()

        val coroutineScope = rememberCoroutineScope()

        val mxcKey = remember(content.mediaSource.url) {
            mxcTools.mxcUri2FilePath(content.mediaSource.url)
        }
        val cachedFile = remember(mxcKey, content.fileExtension) {
            mxcKey?.let { key ->
                val ext = content.fileExtension
                    .trim()
                    .trimStart('.')
                    .takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) }
                    ?: "mp4"
                File("${cacheDir.path}/temp/video-notes/$key.$ext")
            }
        }

        var localUri by remember { mutableStateOf<Uri?>(null) }
        var isPreparing by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<Throwable?>(null) }

        LaunchedEffect(cachedFile) {
            if (cachedFile != null && cachedFile.exists()) {
                localUri = cachedFile.toUri()
            }
        }

        fun prepare() {
            if (isPreparing) return
            if (localUri != null) return
            if (cachedFile == null) {
                error = IllegalStateException("Invalid MXC url for video note.")
                return
            }
            error = null
            isPreparing = true
            coroutineScope.launch {
                val result = matrixMediaLoader.downloadMediaFile(
                    source = content.mediaSource,
                    mimeType = content.mimeType,
                    filename = content.filename,
                ).mapCatchingExceptions { mediaFile ->
                    mediaFile.use {
                        val dest = cachedFile.apply { parentFile?.mkdirs() }
                        if (it.persist(dest.path)) {
                            dest
                        } else {
                            error("Failed to persist video note to cache.")
                        }
                    }
                }
                isPreparing = false
                result.onSuccess { file ->
                    localUri = file.toUri()
                }.onFailure { t ->
                    Timber.e(t, "Failed to prepare inline video note")
                    error = t
                }
            }
        }

        val eventSink: (VideoNoteInlinePlaybackEvent) -> Unit = { event ->
            when (event) {
                VideoNoteInlinePlaybackEvent.Prepare -> prepare()
            }
        }

        return VideoNoteInlinePlaybackState(
            localUri = localUri,
            isPreparing = isPreparing,
            error = error,
            eventSink = eventSink,
        )
    }
}
