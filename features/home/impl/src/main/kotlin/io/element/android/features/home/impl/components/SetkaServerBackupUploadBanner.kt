/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.home.impl.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.element.android.features.home.impl.R
import io.element.android.libraries.designsystem.components.Announcement
import io.element.android.libraries.designsystem.components.AnnouncementType
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight

@Composable
internal fun SetkaServerBackupUploadBanner(
    onContinueClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Announcement(
        modifier = modifier.roomListBannerPadding(),
        title = stringResource(R.string.banner_setka_server_backup_title),
        description = stringResource(R.string.banner_setka_server_backup_content),
        type = AnnouncementType.Actionable(
            actionText = stringResource(R.string.banner_setka_server_backup_submit),
            onActionClick = onContinueClick,
            onDismissClick = onDismissClick,
        ),
    )
}

@PreviewsDayNight
@Composable
internal fun SetkaServerBackupUploadBannerPreview() = ElementPreview {
    SetkaServerBackupUploadBanner(
        onContinueClick = {},
        onDismissClick = {},
    )
}
