/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.deeplink.impl.usecase

import android.app.Activity
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.androidutils.system.openUrlInExternalApp
import io.element.android.libraries.deeplink.api.usecase.InviteFriendsUseCase
import io.element.android.libraries.di.SessionScope
import io.element.android.services.toolbox.api.strings.StringProvider
import io.element.android.libraries.androidutils.R as AndroidUtilsR

@ContributesBinding(SessionScope::class)
class DefaultInviteFriendsUseCase(
    private val stringProvider: StringProvider,
) : InviteFriendsUseCase {
    override fun execute(activity: Activity) {
        activity.openUrlInExternalApp(
            url = "https://setka-matrix.ru",
            errorMessage = stringProvider.getString(AndroidUtilsR.string.error_no_compatible_app_found)
        )
    }
}
