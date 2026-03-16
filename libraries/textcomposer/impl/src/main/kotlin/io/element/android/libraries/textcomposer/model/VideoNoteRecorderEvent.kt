/*
 * Copyright (c) 2026 Element Creations Ltd.
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.textcomposer.model

import androidx.compose.runtime.Immutable

@Immutable
sealed interface VideoNoteRecorderEvent {
    data object Start : VideoNoteRecorderEvent
    data object Stop : VideoNoteRecorderEvent
    data object Cancel : VideoNoteRecorderEvent
}

