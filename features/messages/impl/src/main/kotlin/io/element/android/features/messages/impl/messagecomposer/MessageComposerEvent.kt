/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer

import android.net.Uri
import io.element.android.features.messages.impl.messagecomposer.setka.SetkaPackKind
import io.element.android.features.messages.impl.messagecomposer.setka.SetkaPlusPlan
import io.element.android.features.messages.impl.messagecomposer.setka.SetkaSticker
import io.element.android.libraries.textcomposer.mentions.ResolvedSuggestion
import io.element.android.libraries.textcomposer.model.MessageComposerMode
import io.element.android.libraries.textcomposer.model.Suggestion

sealed interface MessageComposerEvent {
    data object ToggleFullScreenState : MessageComposerEvent
    data object SendMessage : MessageComposerEvent
    data class SendUri(val uri: Uri) : MessageComposerEvent
    data object CloseSpecialMode : MessageComposerEvent
    data class SetMode(val composerMode: MessageComposerMode) : MessageComposerEvent
    data object AddAttachment : MessageComposerEvent
    data object DismissAttachmentMenu : MessageComposerEvent
    sealed interface PickAttachmentSource : MessageComposerEvent {
        data object FromGallery : PickAttachmentSource
        data object FromFiles : PickAttachmentSource
        data object PhotoFromCamera : PickAttachmentSource
        data object VideoFromCamera : PickAttachmentSource
        data object SetkaStickers : PickAttachmentSource
        data object SetkaPlus : PickAttachmentSource
        data object Location : PickAttachmentSource
        data object Poll : PickAttachmentSource
    }

    data class ToggleTextFormatting(val enabled: Boolean) : MessageComposerEvent
    data class Error(val error: Throwable) : MessageComposerEvent
    data class TypingNotice(val isTyping: Boolean) : MessageComposerEvent
    data class SuggestionReceived(val suggestion: Suggestion?) : MessageComposerEvent
    data class InsertSuggestion(val resolvedSuggestion: ResolvedSuggestion) : MessageComposerEvent
    data object RefreshSetka : MessageComposerEvent
    data object ShowSetkaStickerPicker : MessageComposerEvent
    data object HideSetkaStickerPicker : MessageComposerEvent
    data class InsertInlineText(val text: String) : MessageComposerEvent
    data object ShowSetkaPlusDialog : MessageComposerEvent
    data object HideSetkaPlusDialog : MessageComposerEvent
    data class SendSetkaSticker(val sticker: SetkaSticker) : MessageComposerEvent
    data class BuySetkaPlan(val plan: SetkaPlusPlan) : MessageComposerEvent
    data class OpenSetkaPackEditor(val kind: SetkaPackKind, val packId: String? = null) : MessageComposerEvent
    data object CloseSetkaPackEditor : MessageComposerEvent
    data class SaveSetkaPack(val kind: SetkaPackKind, val packId: String?, val name: String) : MessageComposerEvent
    data class ConfirmDeleteSetkaPack(val packId: String, val packName: String) : MessageComposerEvent
    data object DismissDeleteSetkaPack : MessageComposerEvent
    data class DeleteSetkaPack(val packId: String) : MessageComposerEvent
    data class ShareSetkaPack(val packId: String) : MessageComposerEvent
    data class PreviewSetkaSharedPack(val token: String) : MessageComposerEvent
    data object DismissSetkaSharedPackPreview : MessageComposerEvent
    data object ApplySetkaSharedPackPreview : MessageComposerEvent
    data class ImportSetkaSharedPack(val token: String) : MessageComposerEvent
    data class DeleteSetkaSticker(val packId: String, val stickerId: String) : MessageComposerEvent
    data class UploadSetkaMedia(val packId: String, val kind: SetkaPackKind) : MessageComposerEvent
    data object ClearSetkaError : MessageComposerEvent
    data object SaveDraft : MessageComposerEvent
}
