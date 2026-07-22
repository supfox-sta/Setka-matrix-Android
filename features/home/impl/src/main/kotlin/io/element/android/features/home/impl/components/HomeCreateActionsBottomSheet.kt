/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.home.impl.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.home.impl.R
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.IconSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeCreateActionsBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onNewChatClick: () -> Unit,
    onCreateChannelClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.screen_home_create_actions_title),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = ElementTheme.typography.fontBodyLgMedium,
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.screen_home_create_chat)) },
                leadingContent = ListItemContent.Icon(iconSource = IconSource.Vector(CompoundIcons.Chat())),
                onClick = {
                    onDismiss()
                    onNewChatClick()
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.screen_home_create_channel)) },
                leadingContent = ListItemContent.Icon(iconSource = IconSource.Vector(CompoundIcons.Room())),
                onClick = {
                    onDismiss()
                    onCreateChannelClick()
                },
            )
        }
    }
}
