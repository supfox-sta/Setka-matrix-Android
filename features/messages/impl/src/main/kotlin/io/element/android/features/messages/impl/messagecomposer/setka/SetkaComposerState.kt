/*
 * Copyright (c) 2026 Element Creations Ltd.
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.setka

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class SetkaComposerState(
    val isLoading: Boolean = false,
    val isStickerPickerVisible: Boolean = false,
    val isSubscriptionDialogVisible: Boolean = false,
    val subscription: SetkaPlusSubscription? = null,
    val plans: ImmutableList<SetkaPlusPlan> = persistentListOf(),
    val stickerPacks: ImmutableList<SetkaStickerPack> = persistentListOf(),
    val busyPlanId: String? = null,
    val uploadingPackId: String? = null,
    val deletingPackId: String? = null,
    val deletingStickerKey: String? = null,
    val packEditorState: SetkaPackEditorState? = null,
    val sharedPackPreview: SetkaSharedPackPreviewState? = null,
    val deleteConfirmation: SetkaDeleteConfirmation? = null,
    val errorMessage: String? = null,
) {
    val stickerPacksOnly: List<SetkaStickerPack>
        get() = stickerPacks.filter { it.kind == SetkaPackKind.STICKER }

    val emojiPacksOnly: List<SetkaStickerPack>
        get() = stickerPacks.filter { it.kind == SetkaPackKind.EMOJI }

    val isPlusActive: Boolean
        get() = subscription?.isActive == true
}
