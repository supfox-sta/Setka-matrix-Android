/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2022-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer

import android.net.Uri
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.messages.impl.messagecomposer.legacygallery.LegacyGalleryFilter
import io.element.android.features.messages.impl.messagecomposer.legacygallery.LegacyGalleryItem
import io.element.android.features.messages.impl.messagecomposer.legacygallery.LegacyGalleryPickerState
import io.element.android.features.messages.impl.messagecomposer.setka.SetkaComposerState
import io.element.android.libraries.textcomposer.mentions.ResolvedSuggestion
import io.element.android.libraries.textcomposer.model.MessageComposerMode
import io.element.android.libraries.textcomposer.model.TextEditorState
import io.element.android.libraries.textcomposer.model.aTextEditorStateRich
import io.element.android.libraries.permissions.api.aPermissionsState
import io.element.android.wysiwyg.display.TextDisplay
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

open class MessageComposerStateProvider : PreviewParameterProvider<MessageComposerState> {
    override val values: Sequence<MessageComposerState>
        get() = sequenceOf(
            aMessageComposerState(),
            aMessageComposerState(
                legacyGalleryPickerState = LegacyGalleryPickerState(
                    permissionState = aPermissionsState(showDialog = false, permissionGranted = true),
                    isLoading = false,
                    selectedFilter = LegacyGalleryFilter.All,
                    mediaItems = persistentListOf(
                        LegacyGalleryItem(
                            id = 1L,
                            uri = Uri.EMPTY,
                            mimeType = "image/jpeg",
                            isVideo = false,
                            durationMillis = null,
                        ),
                        LegacyGalleryItem(
                            id = 2L,
                            uri = Uri.EMPTY,
                            mimeType = "video/mp4",
                            isVideo = true,
                            durationMillis = 12_000L,
                        ),
                    ),
                    selectedMediaIds = persistentSetOf(1L),
                    maxSelectionCount = Int.MAX_VALUE,
                    eventSink = {},
                )
            ),
        )
}

fun aMessageComposerState(
    textEditorState: TextEditorState = aTextEditorStateRich(),
    isFullScreen: Boolean = false,
    mode: MessageComposerMode = MessageComposerMode.Normal,
    showTextFormatting: Boolean = false,
    showAttachmentSourcePicker: Boolean = false,
    canShareLocation: Boolean = true,
    suggestions: ImmutableList<ResolvedSuggestion> = persistentListOf(),
    legacyGalleryPickerState: LegacyGalleryPickerState? = null,
    setkaState: SetkaComposerState = SetkaComposerState(),
    eventSink: (MessageComposerEvent) -> Unit = {},
) = MessageComposerState(
    textEditorState = textEditorState,
    isFullScreen = isFullScreen,
    mode = mode,
    showTextFormatting = showTextFormatting,
    showAttachmentSourcePicker = showAttachmentSourcePicker,
    canShareLocation = canShareLocation,
    suggestions = suggestions,
    legacyGalleryPickerState = legacyGalleryPickerState,
    setkaState = setkaState,
    resolveMentionDisplay = { _, _ -> TextDisplay.Plain },
    resolveAtRoomMentionDisplay = { TextDisplay.Plain },
    eventSink = eventSink,
)
