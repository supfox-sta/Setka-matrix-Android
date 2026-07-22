/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.roomdetails.impl.channel

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.roomdetails.impl.R
import io.element.android.features.roomdetails.impl.RoomDetailsAction
import io.element.android.features.roomdetails.impl.RoomTopicState
import io.element.android.libraries.designsystem.components.ClickableLinkText
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferenceCategory
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Surface
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.compound.tokens.generated.CompoundIcons

@Composable
internal fun SetkaChannelAboutSection(
    roomTopic: RoomTopicState,
    memberCount: Long,
    onActionClick: (RoomDetailsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferenceCategory(
        title = stringResource(R.string.screen_setka_channel_about_title),
        showTopDivider = false,
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            color = ElementTheme.colors.bgSubtleSecondary,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    text = pluralStringResource(
                        R.plurals.screen_setka_channel_about_subscribers,
                        memberCount.toInt(),
                        memberCount,
                    ),
                    style = ElementTheme.typography.fontBodySmMedium,
                    color = ElementTheme.colors.textSecondary,
                )
                when (roomTopic) {
                    is RoomTopicState.ExistingTopic -> {
                        Text(
                            text = stringResource(R.string.screen_setka_channel_about_description),
                            style = ElementTheme.typography.fontBodySmMedium,
                            color = ElementTheme.colors.textSecondary,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        ClickableLinkText(
                            text = roomTopic.topic,
                            modifier = Modifier.padding(top = 4.dp),
                            interactionSource = remember { MutableInteractionSource() },
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                    }
                    RoomTopicState.CanAddTopic -> {
                        Text(
                            text = stringResource(R.string.screen_setka_channel_about_empty),
                            style = ElementTheme.typography.fontBodyMdRegular,
                            color = ElementTheme.colors.textSecondary,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        ListItem(
                            headlineContent = {
                                Text(stringResource(R.string.screen_setka_channel_about_add_description))
                            },
                            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Plus())),
                            onClick = { onActionClick(RoomDetailsAction.AddTopic) },
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    RoomTopicState.Hidden -> Unit
                }
            }
        }
    }
}
