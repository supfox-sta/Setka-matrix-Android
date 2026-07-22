/*

 * Copyright (c) 2026 Setka Matrix

 *

 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.

 */



package io.element.android.features.messages.impl.channel



import androidx.compose.foundation.background

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.BoxWithConstraints

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.size

import androidx.compose.foundation.layout.width

import androidx.compose.foundation.layout.widthIn

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.runtime.Composable

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.ui.res.pluralStringResource

import androidx.compose.ui.res.stringResource

import androidx.compose.ui.unit.dp

import io.element.android.compound.theme.ElementTheme

import io.element.android.compound.tokens.generated.CompoundIcons

import io.element.android.features.messages.impl.R

import io.element.android.features.messages.impl.timeline.components.MessageEventBubbleDefaults

import io.element.android.libraries.designsystem.modifiers.niceClickable

import io.element.android.libraries.designsystem.theme.LocalSetkaCustomization

import io.element.android.libraries.designsystem.theme.components.Icon

import io.element.android.libraries.designsystem.theme.components.Text

import io.element.android.libraries.ui.strings.CommonPlurals



@Composable

internal fun SetkaChannelCommentsRow(

    replyCount: Long,

    isOutgoing: Boolean,

    onClick: () -> Unit,

    modifier: Modifier = Modifier,

) {

    val bubbleWidthRatio = LocalSetkaCustomization.current.bubbleWidthPercent / 100f

    BoxWithConstraints(modifier = modifier) {

        Row(

            modifier = Modifier

                .then(if (!isOutgoing) Modifier.padding(start = 16.dp) else Modifier)

                .graphicsLayer {

                    shape = RoundedCornerShape(8.dp)

                    clip = true

                }

                .background(MessageEventBubbleDefaults.backgroundBubbleColor(isOutgoing))

                .niceClickable(onClick)

                .padding(horizontal = 12.dp, vertical = 10.dp)

                .widthIn(max = (maxWidth - 24.dp) * bubbleWidthRatio),

            verticalAlignment = Alignment.CenterVertically,

        ) {

            Icon(

                modifier = Modifier.size(20.dp),

                imageVector = CompoundIcons.ThreadsSolid(),

                contentDescription = null,

                tint = ElementTheme.colors.iconSecondary,

            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(

                text = if (replyCount > 0) {

                    pluralStringResource(CommonPlurals.common_replies, replyCount.toInt(), replyCount)

                } else {

                    stringResource(R.string.screen_setka_channel_comments)

                },

                style = ElementTheme.typography.fontBodySmMedium,

                color = ElementTheme.colors.textSecondary,

            )

        }

    }

}



@Composable

internal fun SetkaChannelVideoNoteDiscussionBadge(

    onClick: () -> Unit,

    modifier: Modifier = Modifier,

) {

    Box(

        modifier = modifier

            .size(36.dp)

            .clip(CircleShape)

            .background(ElementTheme.colors.bgAccentRest)

            .niceClickable(onClick),

        contentAlignment = Alignment.Center,

    ) {

        Icon(

            imageVector = CompoundIcons.ThreadsSolid(),

            contentDescription = stringResource(R.string.screen_setka_channel_comments),

            tint = ElementTheme.colors.iconOnSolidPrimary,

            modifier = Modifier.size(18.dp),

        )

    }

}

