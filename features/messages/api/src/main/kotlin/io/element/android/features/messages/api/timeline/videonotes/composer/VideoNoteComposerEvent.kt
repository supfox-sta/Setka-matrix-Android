/*
 * Copyright (c) 2026 Element Creations Ltd.
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.api.timeline.videonotes.composer

import androidx.lifecycle.Lifecycle
import io.element.android.libraries.textcomposer.model.VideoNoteRecorderEvent
import java.io.File

sealed interface VideoNoteComposerEvent {
    data class RecorderEvent(
        val recorderEvent: VideoNoteRecorderEvent,
    ) : VideoNoteComposerEvent

    /**
     * Emitted by the recording UI when a local recording is finalized.
     */
    data class RecordingFinished(
        val file: File,
        val mimeType: String,
        val durationMillis: Long,
    ) : VideoNoteComposerEvent

    data object SendVideoNote : VideoNoteComposerEvent
    data object DeleteVideoNote : VideoNoteComposerEvent

    data object AcceptPermissionRationale : VideoNoteComposerEvent
    data object DismissPermissionsRationale : VideoNoteComposerEvent

    data class LifecycleEvent(val event: Lifecycle.Event) : VideoNoteComposerEvent
    data object DismissSendFailureDialog : VideoNoteComposerEvent
}

