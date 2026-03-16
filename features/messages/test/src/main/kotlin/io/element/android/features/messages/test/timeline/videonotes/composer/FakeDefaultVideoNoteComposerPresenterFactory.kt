/*
 * Copyright (c) 2026 Element Creations Ltd.
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.test.timeline.videonotes.composer

import io.element.android.features.messages.impl.videonotes.composer.DefaultVideoNoteComposerPresenter
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.mediaupload.api.MediaSender
import io.element.android.libraries.mediaupload.impl.DefaultMediaSender
import io.element.android.libraries.mediaupload.test.FakeMediaOptimizationConfigProvider
import io.element.android.libraries.mediaupload.test.FakeMediaPreProcessor
import io.element.android.libraries.permissions.test.FakePermissionsPresenterFactory
import io.element.android.services.analytics.test.FakeAnalyticsService
import kotlinx.coroutines.CoroutineScope

class FakeDefaultVideoNoteComposerPresenterFactory(
    private val sessionCoroutineScope: CoroutineScope,
    private val mediaSender: MediaSender = DefaultMediaSender(
        preProcessor = FakeMediaPreProcessor(),
        room = FakeJoinedRoom(),
        timelineMode = Timeline.Mode.Live,
        mediaOptimizationConfigProvider = FakeMediaOptimizationConfigProvider(),
    ),
) : DefaultVideoNoteComposerPresenter.Factory {
    override fun create(timelineMode: Timeline.Mode): DefaultVideoNoteComposerPresenter {
        return DefaultVideoNoteComposerPresenter(
            sessionCoroutineScope = sessionCoroutineScope,
            timelineMode = timelineMode,
            analyticsService = FakeAnalyticsService(),
            mediaOptimizationConfigProvider = FakeMediaOptimizationConfigProvider(),
            mediaSenderFactory = { mediaSender },
            permissionsPresenterFactory = FakePermissionsPresenterFactory(),
        )
    }
}

