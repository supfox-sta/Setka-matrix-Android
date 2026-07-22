/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.messages.impl.channel

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.features.messages.impl.R
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.ButtonSize

@Composable
internal fun ChannelSubscriberBottomBar(
    isJoined: Boolean,
    notificationsMuted: Boolean,
    enabled: Boolean,
    onJoin: () -> Unit,
    onToggleNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = if (!isJoined) {
        stringResource(R.string.screen_setka_channel_join)
    } else if (notificationsMuted) {
        stringResource(R.string.screen_setka_channel_enable_notifications)
    } else {
        stringResource(R.string.screen_setka_channel_disable_notifications)
    }
    Button(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        text = label,
        size = ButtonSize.Large,
        enabled = enabled,
        onClick = if (!isJoined) onJoin else onToggleNotifications,
    )
}
