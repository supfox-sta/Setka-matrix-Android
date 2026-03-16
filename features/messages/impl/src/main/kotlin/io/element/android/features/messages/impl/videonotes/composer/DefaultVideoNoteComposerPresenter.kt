/*
 * Copyright (c) 2026 Element Creations Ltd.
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.videonotes.composer

import android.Manifest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.messages.api.timeline.videonotes.composer.VideoNoteComposerEvent
import io.element.android.features.messages.api.timeline.videonotes.composer.VideoNoteComposerPresenter
import io.element.android.features.messages.api.timeline.videonotes.composer.VideoNoteComposerState
import io.element.android.libraries.androidutils.file.safeDelete
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.mediaupload.api.MediaOptimizationConfigProvider
import io.element.android.libraries.mediaupload.api.MediaSenderFactory
import io.element.android.libraries.permissions.api.PermissionsEvent
import io.element.android.libraries.permissions.api.PermissionsPresenter
import io.element.android.libraries.textcomposer.model.VideoNoteRecorderEvent
import io.element.android.libraries.textcomposer.model.VideoNoteState
import io.element.android.services.analytics.api.AnalyticsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

@AssistedInject
class DefaultVideoNoteComposerPresenter(
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
    @Assisted private val timelineMode: Timeline.Mode,
    private val analyticsService: AnalyticsService,
    private val mediaOptimizationConfigProvider: MediaOptimizationConfigProvider,
    mediaSenderFactory: MediaSenderFactory,
    permissionsPresenterFactory: PermissionsPresenter.Factory,
) : VideoNoteComposerPresenter {
    @ContributesBinding(RoomScope::class)
    @AssistedFactory
    interface Factory : VideoNoteComposerPresenter.Factory {
        override fun create(timelineMode: Timeline.Mode): DefaultVideoNoteComposerPresenter
    }

    private val cameraPermissionsPresenter = permissionsPresenterFactory.create(Manifest.permission.CAMERA)
    private val audioPermissionsPresenter = permissionsPresenterFactory.create(Manifest.permission.RECORD_AUDIO)
    private val mediaSender = mediaSenderFactory.create(timelineMode)

    private var pendingStart: Boolean = false

    @Composable
    override fun present(): VideoNoteComposerState {
        val localCoroutineScope = rememberCoroutineScope()
        val cameraPermissionState by rememberUpdatedState(cameraPermissionsPresenter.present())
        val audioPermissionState by rememberUpdatedState(audioPermissionsPresenter.present())

        var state by remember { mutableStateOf<VideoNoteState>(VideoNoteState.Idle) }
        var showSendFailureDialog by remember { mutableStateOf(false) }

        val permissionGranted = cameraPermissionState.permissionGranted && audioPermissionState.permissionGranted
        val keepScreenOn = state is VideoNoteState.Recording

        LaunchedEffect(permissionGranted) {
            if (permissionGranted && pendingStart) {
                pendingStart = false
                state = VideoNoteState.Recording()
            }
        }

        fun resetToIdle(deleteFile: Boolean) {
            val preview = state as? VideoNoteState.Preview
            if (deleteFile) {
                preview?.file?.safeDelete()
            }
            state = VideoNoteState.Idle
        }

        fun handleRecorderEvent(event: VideoNoteRecorderEvent) {
            when (event) {
                VideoNoteRecorderEvent.Start -> {
                    if (state !is VideoNoteState.Idle) return
                    if (!permissionGranted) {
                        pendingStart = true
                        if (!cameraPermissionState.permissionGranted) {
                            cameraPermissionState.eventSink(PermissionsEvent.RequestPermissions)
                        }
                        if (!audioPermissionState.permissionGranted) {
                            audioPermissionState.eventSink(PermissionsEvent.RequestPermissions)
                        }
                        return
                    }
                    state = VideoNoteState.Recording()
                }
                VideoNoteRecorderEvent.Stop -> {
                    val recording = state as? VideoNoteState.Recording ?: return
                    state = recording.copy(stopRequestId = recording.stopRequestId + 1)
                }
                VideoNoteRecorderEvent.Cancel -> resetToIdle(deleteFile = true)
            }
        }

        fun sendVideoNote() {
            val preview = state as? VideoNoteState.Preview ?: return
            if (preview.isSending) return
            state = preview.copy(isSending = true)
            sessionCoroutineScope.launch {
                val mimeType = "video/mp4"
                val result = mediaSender.sendMedia(
                    uri = preview.file.toUri(),
                    mimeType = mimeType,
                    caption = null,
                    formattedCaption = null,
                    inReplyToEventId = null,
                    mediaOptimizationConfig = mediaOptimizationConfigProvider.get(),
                )
                if (result.isFailure) {
                    Timber.e(result.exceptionOrNull(), "Failed to send video note")
                    analyticsService.trackError(result.exceptionOrNull() ?: IllegalStateException("Failed to send video note"))
                    showSendFailureDialog = true
                    state = preview.copy(isSending = false)
                } else {
                    resetToIdle(deleteFile = true)
                }
            }
        }

        fun handleEvent(event: VideoNoteComposerEvent) {
            when (event) {
                is VideoNoteComposerEvent.RecorderEvent -> handleRecorderEvent(event.recorderEvent)
                is VideoNoteComposerEvent.RecordingFinished -> {
                    val duration = event.durationMillis.milliseconds
                    state = VideoNoteState.Preview(
                        file = event.file,
                        duration = duration,
                        isSending = false,
                    )
                }
                VideoNoteComposerEvent.SendVideoNote -> localCoroutineScope.launch { sendVideoNote() }
                VideoNoteComposerEvent.DeleteVideoNote -> resetToIdle(deleteFile = true)
                VideoNoteComposerEvent.AcceptPermissionRationale -> {
                    // We may have 2 dialogs; close both by sending the event to each presenter.
                    cameraPermissionState.eventSink(PermissionsEvent.OpenSystemSettingAndCloseDialog)
                    audioPermissionState.eventSink(PermissionsEvent.OpenSystemSettingAndCloseDialog)
                }
                VideoNoteComposerEvent.DismissPermissionsRationale -> {
                    cameraPermissionState.eventSink(PermissionsEvent.CloseDialog)
                    audioPermissionState.eventSink(PermissionsEvent.CloseDialog)
                }
                is VideoNoteComposerEvent.LifecycleEvent -> Unit
                VideoNoteComposerEvent.DismissSendFailureDialog -> showSendFailureDialog = false
            }
        }

        val showPermissionRationaleDialog = cameraPermissionState.showDialog || audioPermissionState.showDialog

        return VideoNoteComposerState(
            videoNoteState = state,
            showPermissionRationaleDialog = showPermissionRationaleDialog,
            showSendFailureDialog = showSendFailureDialog,
            keepScreenOn = keepScreenOn,
            eventSink = ::handleEvent,
        )
    }
}
