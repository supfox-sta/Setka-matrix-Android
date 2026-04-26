/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.R
import io.element.android.libraries.androidutils.ui.hideKeyboard
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Surface
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.testtags.TestTags
import io.element.android.libraries.testtags.testTag

@Composable
internal fun AttachmentsBottomSheet(
    state: MessageComposerState,
    onSendLocationClick: () -> Unit,
    onCreatePollClick: () -> Unit,
    enableTextFormatting: Boolean,
    modifier: Modifier = Modifier,
) {
    val localView = LocalView.current
    var isVisible by rememberSaveable { mutableStateOf(state.showAttachmentSourcePicker) }

    BackHandler(enabled = isVisible) {
        isVisible = false
    }

    LaunchedEffect(state.showAttachmentSourcePicker) {
        isVisible = if (state.showAttachmentSourcePicker) {
            // We need to use this instead of `LocalFocusManager.clearFocus()` to hide the keyboard when focus is on an Android View
            localView.hideKeyboard()
            true
        } else {
            false
        }
    }
    // Send 'DismissAttachmentMenu' event when the bottomsheet was just hidden
    LaunchedEffect(isVisible) {
        if (!isVisible) {
            state.eventSink(MessageComposerEvent.DismissAttachmentMenu)
        }
    }

    if (isVisible) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(ElementTheme.colors.bgCanvasDefault.copy(alpha = 0.12f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { isVisible = false },
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .imePadding()
                    .navigationBarsPadding()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {},
                shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                color = ElementTheme.colors.bgCanvasDefault,
                border = BorderStroke(1.dp, ElementTheme.colors.borderInteractiveSecondary),
                shadowElevation = 10.dp,
            ) {
                AttachmentSourcePickerMenu(
                    state = state,
                    enableTextFormatting = enableTextFormatting,
                    onSendLocationClick = onSendLocationClick,
                    onCreatePollClick = onCreatePollClick,
                )
            }
        }
    }
}

@Composable
private fun AttachmentActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ElementTheme.colors.iconSecondary,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = ElementTheme.typography.fontBodyMdMedium,
            color = ElementTheme.colors.textPrimary,
        )
    }
}

@Composable
private fun AttachmentActionDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 14.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(ElementTheme.colors.borderDisabled),
    )
}

@Composable
internal fun AttachmentSourcePickerMenu(
    state: MessageComposerState,
    onSendLocationClick: () -> Unit,
    onCreatePollClick: () -> Unit,
    enableTextFormatting: Boolean,
) {
    Column(
        modifier = Modifier
            .width(248.dp)
            .padding(vertical = 8.dp)
    ) {
        AttachmentActionRow(
            icon = CompoundIcons.TakePhoto(),
            title = stringResource(R.string.screen_room_attachment_source_camera_photo),
            onClick = { state.eventSink(MessageComposerEvent.PickAttachmentSource.PhotoFromCamera) },
        )
        AttachmentActionRow(
            icon = CompoundIcons.VideoCall(),
            title = stringResource(R.string.screen_room_attachment_source_camera_video),
            onClick = { state.eventSink(MessageComposerEvent.PickAttachmentSource.VideoFromCamera) },
        )
        AttachmentActionRow(
            icon = CompoundIcons.Image(),
            title = stringResource(R.string.screen_room_attachment_source_gallery),
            onClick = { state.eventSink(MessageComposerEvent.PickAttachmentSource.FromGallery) },
        )
        AttachmentActionRow(
            icon = CompoundIcons.Attachment(),
            title = stringResource(R.string.screen_room_attachment_source_files),
            onClick = { state.eventSink(MessageComposerEvent.PickAttachmentSource.FromFiles) },
        )
        if (state.canShareLocation) {
            AttachmentActionDivider()
            AttachmentActionRow(
                modifier = Modifier.testTag(TestTags.attachmentSourceLocation),
                icon = CompoundIcons.LocationPin(),
                title = stringResource(R.string.screen_room_attachment_source_location),
                onClick = {
                    state.eventSink(MessageComposerEvent.PickAttachmentSource.Location)
                    onSendLocationClick()
                },
            )
        }
        AttachmentActionRow(
            modifier = Modifier.testTag(TestTags.attachmentSourcePoll),
            icon = CompoundIcons.Polls(),
            title = stringResource(R.string.screen_room_attachment_source_poll),
            onClick = {
                state.eventSink(MessageComposerEvent.PickAttachmentSource.Poll)
                onCreatePollClick()
            },
        )
        if (enableTextFormatting) {
            AttachmentActionDivider()
            AttachmentActionRow(
                icon = CompoundIcons.TextFormatting(),
                title = stringResource(R.string.screen_room_attachment_text_formatting),
                onClick = { state.eventSink(MessageComposerEvent.ToggleTextFormatting(enabled = true)) },
            )
        }
    }
}

@PreviewsDayNight
@Composable
internal fun AttachmentSourcePickerMenuPreview() = ElementPreview {
    AttachmentSourcePickerMenu(
        state = aMessageComposerState(
            canShareLocation = true,
        ),
        onSendLocationClick = {},
        onCreatePollClick = {},
        enableTextFormatting = true,
    )
}
