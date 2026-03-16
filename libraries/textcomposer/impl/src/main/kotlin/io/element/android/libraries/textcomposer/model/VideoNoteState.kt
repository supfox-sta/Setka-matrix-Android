/*
 * Copyright (c) 2026 Element Creations Ltd.
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.textcomposer.model

import androidx.compose.runtime.Immutable
import java.io.File
import kotlin.time.Duration

@Immutable
sealed interface VideoNoteState {
    data object Idle : VideoNoteState

    data class Recording(
        /**
         * Increments each time the UI should request the active recording to stop.
         */
        val stopRequestId: Long = 0,
    ) : VideoNoteState

    data class Preview(
        val file: File,
        val duration: Duration,
        val isSending: Boolean,
    ) : VideoNoteState
}
