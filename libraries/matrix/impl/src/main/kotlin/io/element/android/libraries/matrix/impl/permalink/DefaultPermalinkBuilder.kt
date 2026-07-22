/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.permalink

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.appconfig.SetkaMatrixConfig
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.core.MatrixPatterns
import io.element.android.libraries.matrix.api.core.RoomAlias
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.permalink.PermalinkBuilder
import io.element.android.libraries.matrix.api.permalink.PermalinkBuilderError
import org.matrix.rustcomponents.sdk.matrixToRoomAliasPermalink
import org.matrix.rustcomponents.sdk.matrixToUserPermalink

@ContributesBinding(AppScope::class)
class DefaultPermalinkBuilder : PermalinkBuilder {
    override fun permalinkForUser(userId: UserId): Result<String> {
        if (!MatrixPatterns.isUserId(userId.value)) {
            return Result.failure(PermalinkBuilderError.InvalidData)
        }
        return runCatchingExceptions {
            matrixToUserPermalink(userId.value).toSetkaSharePermalink()
        }
    }

    override fun permalinkForRoomAlias(roomAlias: RoomAlias): Result<String> {
        if (!MatrixPatterns.isRoomAlias(roomAlias.value)) {
            return Result.failure(PermalinkBuilderError.InvalidData)
        }
        return runCatchingExceptions {
            matrixToRoomAliasPermalink(roomAlias.value).toSetkaSharePermalink()
        }
    }

    private fun String.toSetkaSharePermalink(): String {
        return replace("https://matrix.to", SetkaMatrixConfig.SHARE_BASE_URL)
            .replace("https://www.matrix.to", SetkaMatrixConfig.SHARE_BASE_URL)
    }
}
