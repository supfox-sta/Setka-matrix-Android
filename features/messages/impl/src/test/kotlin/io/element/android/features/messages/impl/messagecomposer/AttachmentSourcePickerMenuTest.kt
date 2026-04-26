/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.element.android.libraries.testtags.TestTags
import io.element.android.tests.testutils.EnsureNeverCalled
import io.element.android.tests.testutils.EventsRecorder
import io.element.android.tests.testutils.ensureCalledOnce
import io.element.android.tests.testutils.setSafeContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "h1024dp")
class AttachmentSourcePickerMenuTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `clicking on send location invokes callback and emits event`() {
        val eventsRecorder = EventsRecorder<MessageComposerEvent>()
        ensureCalledOnce { callback ->
            rule.setSafeContent {
                AttachmentSourcePickerMenu(
                    state = aMessageComposerState(
                        canShareLocation = true,
                        eventSink = eventsRecorder,
                    ),
                    onSendLocationClick = callback,
                    onCreatePollClick = EnsureNeverCalled(),
                    enableTextFormatting = false,
                )
            }
            rule.onNodeWithTag(TestTags.attachmentSourceLocation.value, useUnmergedTree = true).performClick()
        }
        eventsRecorder.assertSingle(MessageComposerEvent.PickAttachmentSource.Location)
    }

    @Test
    fun `clicking on create poll invokes callback and emits event`() {
        val eventsRecorder = EventsRecorder<MessageComposerEvent>()
        ensureCalledOnce { callback ->
            rule.setSafeContent {
                AttachmentSourcePickerMenu(
                    state = aMessageComposerState(
                        canShareLocation = true,
                        eventSink = eventsRecorder,
                    ),
                    onSendLocationClick = EnsureNeverCalled(),
                    onCreatePollClick = callback,
                    enableTextFormatting = false,
                )
            }
            rule.onNodeWithTag(TestTags.attachmentSourcePoll.value, useUnmergedTree = true).performClick()
        }
        eventsRecorder.assertSingle(MessageComposerEvent.PickAttachmentSource.Poll)
    }
}
