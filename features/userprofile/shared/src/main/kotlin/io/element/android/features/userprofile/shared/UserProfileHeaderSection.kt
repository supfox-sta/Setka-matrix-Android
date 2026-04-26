/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.userprofile.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.userprofile.api.UserProfileVerificationState
import io.element.android.libraries.designsystem.atomic.atoms.MatrixBadgeAtom
import io.element.android.libraries.designsystem.atomic.molecules.MatrixBadgeRowMolecule
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.modifiers.niceClickable
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.ButtonSize
import io.element.android.libraries.designsystem.theme.components.OutlinedButton
import io.element.android.libraries.designsystem.theme.components.Surface
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.parseSetkaColorOrNull
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.ui.media.MediaRequestData
import io.element.android.libraries.testtags.TestTags
import io.element.android.libraries.testtags.testTag
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.toImmutableList

@Composable
fun UserProfileHeaderSection(
    avatarUrl: String?,
    userId: UserId,
    userName: String?,
    bio: String?,
    profileColorHex: String?,
    badgeEmojiMxcUrl: String?,
    statusEmojiMxcUrl: String?,
    profileBackgroundMxcUrl: String?,
    lastSeenText: String?,
    verificationState: UserProfileVerificationState,
    openAvatarPreview: (url: String) -> Unit,
    onUserIdClick: () -> Unit,
    withdrawVerificationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val baseColor = parseSetkaColorOrNull(profileColorHex) ?: ElementTheme.colors.bgSubtlePrimary
    val gradient = Brush.verticalGradient(
        listOf(
            baseColor.copy(alpha = 0.44f),
            baseColor.copy(alpha = 0.24f),
            baseColor.copy(alpha = 0.16f),
        )
    )
    val useDarkText = baseColor.luminance() > 0.52f
    val headerTextColor = if (useDarkText) {
        Color.Black.copy(alpha = 0.9f)
    } else {
        Color.White.copy(alpha = 0.96f)
    }
    val secondaryHeaderTextColor = if (useDarkText) {
        Color.Black.copy(alpha = 0.68f)
    } else {
        Color.White.copy(alpha = 0.82f)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clipToBounds()
                .background(baseColor),
            contentAlignment = Alignment.Center,
        ) {
            if (!profileBackgroundMxcUrl.isNullOrBlank()) {
                AsyncImage(
                    model = MediaRequestData(MediaSource(profileBackgroundMxcUrl), MediaRequestData.Kind.Content),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.76f,
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(gradient)
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box {
                    Avatar(
                        avatarData = AvatarData(userId.value, userName, avatarUrl, AvatarSize.UserHeader),
                        avatarType = AvatarType.User,
                        contentDescription = stringResource(CommonStrings.a11y_user_avatar),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(
                                enabled = avatarUrl != null,
                                onClickLabel = stringResource(CommonStrings.action_view),
                            ) {
                                openAvatarPreview(avatarUrl!!)
                            }
                            .testTag(TestTags.memberDetailAvatar)
                    )
                    if (!statusEmojiMxcUrl.isNullOrBlank()) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 6.dp, end = 4.dp),
                            shape = CircleShape,
                            color = ElementTheme.colors.bgCanvasDefault.copy(alpha = 0.94f),
                        ) {
                            AsyncImage(
                                model = MediaRequestData(MediaSource(statusEmojiMxcUrl), MediaRequestData.Kind.Content),
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(18.dp),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (userName != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            modifier = Modifier
                                .clipToBounds()
                                .semantics { heading() },
                            text = userName,
                            style = ElementTheme.typography.fontHeadingLgBold,
                            textAlign = TextAlign.Center,
                            color = headerTextColor,
                        )
                        if (!badgeEmojiMxcUrl.isNullOrBlank()) {
                            Spacer(modifier = Modifier.padding(start = 8.dp))
                            AsyncImage(
                                model = MediaRequestData(MediaSource(badgeEmojiMxcUrl), MediaRequestData.Kind.Content),
                                contentDescription = null,
                                modifier = Modifier.height(18.dp),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }
                if (!lastSeenText.isNullOrBlank()) {
                    Text(
                        text = lastSeenText,
                        style = ElementTheme.typography.fontBodyMdRegular,
                        color = secondaryHeaderTextColor,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    modifier = Modifier.niceClickable { onUserIdClick() },
                    text = userId.value,
                    style = ElementTheme.typography.fontBodyLgRegular,
                    color = secondaryHeaderTextColor,
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (!bio.isNullOrBlank()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(18.dp),
                color = ElementTheme.colors.bgSubtleSecondary,
            ) {
                Text(
                    text = bio,
                    modifier = Modifier.padding(14.dp),
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textPrimary,
                )
            }
        } else {
            Spacer(modifier = Modifier.height(12.dp))
        }

        when (verificationState) {
            UserProfileVerificationState.UNKNOWN,
            UserProfileVerificationState.UNVERIFIED -> Unit

            UserProfileVerificationState.VERIFIED -> {
                MatrixBadgeRowMolecule(
                    data = listOf(
                        MatrixBadgeAtom.MatrixBadgeData(
                            text = stringResource(CommonStrings.common_verified),
                            icon = CompoundIcons.Verified(),
                            type = MatrixBadgeAtom.Type.Positive,
                        )
                    ).toImmutableList(),
                )
            }

            UserProfileVerificationState.VERIFICATION_VIOLATION -> {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(CommonStrings.crypto_identity_change_profile_pin_violation, userName ?: userId.value),
                    color = ElementTheme.colors.textCriticalPrimary,
                    style = ElementTheme.typography.fontBodyMdMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    size = ButtonSize.MediumLowPadding,
                    text = stringResource(CommonStrings.crypto_identity_change_withdraw_verification_action),
                    onClick = withdrawVerificationClick,
                )
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@PreviewsDayNight
@Composable
internal fun UserProfileHeaderSectionPreview() = ElementPreview {
    UserProfileHeaderSection(
        avatarUrl = null,
        userId = UserId("@alice:example.com"),
        userName = "Alice",
        bio = "О себе",
        profileColorHex = "#7B61FF",
        badgeEmojiMxcUrl = null,
        statusEmojiMxcUrl = null,
        profileBackgroundMxcUrl = null,
        lastSeenText = "В сети",
        verificationState = UserProfileVerificationState.VERIFIED,
        openAvatarPreview = {},
        onUserIdClick = {},
        withdrawVerificationClick = {},
    )
}

@PreviewsDayNight
@Composable
internal fun UserProfileHeaderSectionWithVerificationViolationPreview() = ElementPreview {
    UserProfileHeaderSection(
        avatarUrl = null,
        userId = UserId("@alice:example.com"),
        userName = "Alice",
        bio = "О себе",
        profileColorHex = "#7B61FF",
        badgeEmojiMxcUrl = null,
        statusEmojiMxcUrl = null,
        profileBackgroundMxcUrl = null,
        lastSeenText = "Был(-а) недавно",
        verificationState = UserProfileVerificationState.VERIFICATION_VIOLATION,
        openAvatarPreview = {},
        onUserIdClick = {},
        withdrawVerificationClick = {},
    )
}
