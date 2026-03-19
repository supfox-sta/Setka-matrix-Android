/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.userprofile.shared

import android.content.Context
import io.element.android.libraries.androidutils.R
import io.element.android.libraries.androidutils.system.openUrlInExternalApp
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.permalink.PermalinkBuilder

class UserProfileNodeHelper(
    private val userId: UserId,
) {
    interface Callback : NodeInputs {
        fun navigateToAvatarPreview(username: String, avatarUrl: String)
        fun navigateToRoom(roomId: RoomId)
        fun startAudioCall(dmRoomId: RoomId)
        fun startVideoCall(dmRoomId: RoomId)
        fun startVerifyUserFlow(userId: UserId)
    }

    fun onShareUser(
        context: Context,
        _permalinkBuilder: PermalinkBuilder,
    ) {
        context.openUrlInExternalApp(
            url = "https://setka-matrix.ru",
            errorMessage = context.getString(R.string.error_no_compatible_app_found),
        )
    }
}
