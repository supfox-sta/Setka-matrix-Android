/*
 * Copyright (c) 2026 Element Creations Ltd.
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.api.timeline.videonotes.composer

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.libraries.textcomposer.model.VideoNoteState

open class VideoNoteComposerStateProvider : PreviewParameterProvider<VideoNoteComposerState> {
    override val values: Sequence<VideoNoteComposerState>
        get() = sequenceOf(
            aVideoNoteComposerState(videoNoteState = VideoNoteState.Recording()),
        )
}

fun aVideoNoteComposerState(
    videoNoteState: VideoNoteState = VideoNoteState.Idle,
    keepScreenOn: Boolean = false,
    showPermissionRationaleDialog: Boolean = false,
    showSendFailureDialog: Boolean = false,
) = VideoNoteComposerState(
    videoNoteState = videoNoteState,
    showPermissionRationaleDialog = showPermissionRationaleDialog,
    showSendFailureDialog = showSendFailureDialog,
    keepScreenOn = keepScreenOn,
    eventSink = {},
)

