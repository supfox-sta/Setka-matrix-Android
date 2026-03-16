/*
 * Copyright (c) 2026 Element Creations Ltd.
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.api.timeline.videonotes.composer

import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.timeline.Timeline

fun interface VideoNoteComposerPresenter : Presenter<VideoNoteComposerState> {
    interface Factory {
        fun create(timelineMode: Timeline.Mode): VideoNoteComposerPresenter
    }
}

