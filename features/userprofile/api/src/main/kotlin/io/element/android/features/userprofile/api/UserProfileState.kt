/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.userprofile.api

import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId

data class UserProfileState(
    val userId: UserId,
    val userName: String?,
    val avatarUrl: String?,
    /** Setka profile "about" text (server-backed). */
    val bio: String?,
    /** Setka profile color (hex string like "#RRGGBB" or "#AARRGGBB"). */
    val profileColorHex: String?,
    /** Optional badge emoji image (mxc://) to show next to the display name. */
    val badgeEmojiMxcUrl: String?,
    /** Optional status emoji image (mxc://) shown on top of the avatar. */
    val statusEmojiMxcUrl: String?,
    /** Optional profile background image (mxc://) shown behind the gradient header. */
    val profileBackgroundMxcUrl: String?,
    val email: String?,
    val phone: String?,
    val lastSeenText: String?,
    val verificationState: UserProfileVerificationState,
    val isBlocked: AsyncData<Boolean>,
    val startDmActionState: AsyncAction<RoomId>,
    val displayConfirmationDialog: ConfirmationDialog?,
    val isCurrentUser: Boolean,
    val dmRoomId: RoomId?,
    val canCall: Boolean,
    val snackbarMessage: SnackbarMessage?,
    val eventSink: (UserProfileEvents) -> Unit
) {
    enum class ConfirmationDialog {
        Block,
        Unblock
    }
}

enum class UserProfileVerificationState {
    UNKNOWN,
    VERIFIED,
    UNVERIFIED,
    VERIFICATION_VIOLATION,
}
