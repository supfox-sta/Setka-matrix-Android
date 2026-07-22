/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.deeplink.impl.usecase

import android.app.Activity
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.androidutils.system.startSharePlainTextIntent
import io.element.android.libraries.deeplink.api.usecase.InviteFriendsUseCase
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.permalink.PermalinkBuilder
import io.element.android.services.toolbox.api.strings.StringProvider
import io.element.android.libraries.androidutils.R as AndroidUtilsR

@ContributesBinding(SessionScope::class)
class DefaultInviteFriendsUseCase(
    private val matrixClient: MatrixClient,
    private val permalinkBuilder: PermalinkBuilder,
    private val stringProvider: StringProvider,
) : InviteFriendsUseCase {
    override fun execute(activity: Activity) {
        val userId = UserId(matrixClient.sessionId.value)
        val permalink = permalinkBuilder.permalinkForUser(userId).getOrNull()
        val shareText = if (permalink != null) {
            "Присоединяйся ко мне в Сетке Matrix:\n$permalink"
        } else {
            "Присоединяйся к Сетке Matrix"
        }
        activity.startSharePlainTextIntent(
            activityResultLauncher = null,
            chooserTitle = "Пригласить в Сетку",
            text = shareText,
            subject = "Приглашение в Сетку Matrix",
            noActivityFoundMessage = stringProvider.getString(AndroidUtilsR.string.error_no_compatible_app_found),
        )
    }
}
