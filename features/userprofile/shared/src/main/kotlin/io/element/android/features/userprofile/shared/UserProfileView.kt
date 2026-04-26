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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.startchat.api.ConfirmingStartDmWithMatrixUser
import io.element.android.features.userprofile.api.UserProfileEvents
import io.element.android.features.userprofile.api.UserProfileState
import io.element.android.features.userprofile.api.UserProfileVerificationState
import io.element.android.features.userprofile.shared.blockuser.BlockUserDialogs
import io.element.android.features.userprofile.shared.blockuser.BlockUserSection
import io.element.android.libraries.designsystem.components.async.AsyncActionView
import io.element.android.libraries.designsystem.components.async.AsyncActionViewDefaults
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferenceCategory
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Surface
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarHost
import io.element.android.libraries.designsystem.utils.snackbar.rememberSnackbarHostState
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.ui.components.CreateDmConfirmationBottomSheet
import io.element.android.libraries.ui.strings.CommonStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileView(
    state: UserProfileState,
    onShareUser: () -> Unit,
    onOpenDm: (RoomId) -> Unit,
    onStartAudioCall: (RoomId) -> Unit,
    onStartVideoCall: (RoomId) -> Unit,
    goBack: () -> Unit,
    onEditProfile: () -> Unit,
    openAvatarPreview: (username: String, url: String) -> Unit,
    onVerifyClick: (UserId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = rememberSnackbarHostState(snackbarMessage = state.snackbarMessage)
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = { BackButton(onClick = goBack) },
                actions = {
                    if (state.isCurrentUser) {
                        IconButton(onClick = onEditProfile) {
                            Icon(
                                imageVector = CompoundIcons.Edit(),
                                contentDescription = stringResource(CommonStrings.common_editing),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState())
        ) {
            UserProfileHeaderSection(
                avatarUrl = state.avatarUrl,
                userId = state.userId,
                userName = state.userName,
                bio = state.bio,
                profileColorHex = state.profileColorHex,
                badgeEmojiMxcUrl = state.badgeEmojiMxcUrl,
                statusEmojiMxcUrl = state.statusEmojiMxcUrl,
                profileBackgroundMxcUrl = state.profileBackgroundMxcUrl,
                lastSeenText = state.lastSeenText,
                verificationState = state.verificationState,
                openAvatarPreview = { avatarUrl ->
                    openAvatarPreview(state.userName ?: state.userId.value, avatarUrl)
                },
                onUserIdClick = {
                    state.eventSink(UserProfileEvents.CopyToClipboard(state.userId.value))
                },
                withdrawVerificationClick = { state.eventSink(UserProfileEvents.WithdrawVerification) },
            )
            UserProfileMainActionsSection(
                isCurrentUser = state.isCurrentUser,
                canCall = state.canCall,
                onShareUser = onShareUser,
                onStartDM = { state.eventSink(UserProfileEvents.StartDM) },
                onAudioCall = { state.dmRoomId?.let { onStartAudioCall(it) } },
                onVideoCall = { state.dmRoomId?.let { onStartVideoCall(it) } }
            )
            ProfileInfoSection(state = state)
            Spacer(modifier = Modifier.height(26.dp))
            if (!state.isCurrentUser) {
                VerifyUserSection(state, onVerifyClick = { onVerifyClick(state.userId) })
                BlockUserSection(state)
                BlockUserDialogs(state)
            }
            AsyncActionView(
                async = state.startDmActionState,
                progressDialog = {
                    AsyncActionViewDefaults.ProgressDialog(
                        progressText = stringResource(CommonStrings.common_starting_chat),
                    )
                },
                onSuccess = onOpenDm,
                errorMessage = { stringResource(R.string.screen_start_chat_error_starting_chat) },
                onRetry = { state.eventSink(UserProfileEvents.StartDM) },
                onErrorDismiss = { state.eventSink(UserProfileEvents.ClearStartDMState) },
                confirmationDialog = { data ->
                    if (data is ConfirmingStartDmWithMatrixUser) {
                        CreateDmConfirmationBottomSheet(
                            matrixUser = data.matrixUser,
                            onSendInvite = {
                                state.eventSink(UserProfileEvents.StartDM)
                            },
                            onDismiss = {
                                state.eventSink(UserProfileEvents.ClearStartDMState)
                            },
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun ProfileInfoSection(state: UserProfileState) {
    PreferenceCategory(title = "Информация", showTopDivider = false) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            color = ElementTheme.colors.bgSubtleSecondary,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ProfileInfoRow(
                    label = "Имя пользователя",
                    value = state.userId.value,
                    onClick = { state.eventSink(UserProfileEvents.CopyToClipboard(state.userId.value)) },
                )
                state.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                    ProfileInfoDivider()
                    ProfileInfoRow(
                        label = "Телефон",
                        value = phone,
                        onClick = { state.eventSink(UserProfileEvents.CopyToClipboard(phone)) },
                    )
                }
                state.email?.takeIf { it.isNotBlank() }?.let { email ->
                    ProfileInfoDivider()
                    ProfileInfoRow(
                        label = "Почта",
                        value = email,
                        onClick = { state.eventSink(UserProfileEvents.CopyToClipboard(email)) },
                    )
                }
                state.lastSeenText?.takeIf { it.isNotBlank() }?.let { lastSeenText ->
                    ProfileInfoDivider()
                    ProfileInfoRow(
                        label = "Статус",
                        value = lastSeenText,
                        onClick = { state.eventSink(UserProfileEvents.CopyToClipboard(lastSeenText)) },
                    )
                }
                state.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                    ProfileInfoDivider()
                    ProfileInfoRow(
                        label = "О себе",
                        value = bio,
                        onClick = { state.eventSink(UserProfileEvents.CopyToClipboard(bio)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileInfoRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = value,
            style = ElementTheme.typography.fontBodyLgMedium,
            color = Color.White.copy(alpha = 0.96f),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun ProfileInfoDivider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ElementTheme.colors.borderDisabled)
    )
}

@Composable
private fun VerifyUserSection(
    state: UserProfileState,
    onVerifyClick: () -> Unit,
) {
    if (state.verificationState == UserProfileVerificationState.UNVERIFIED) {
        ListItem(
            headlineContent = { Text(stringResource(CommonStrings.common_verify_user)) },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Lock())),
            onClick = onVerifyClick,
        )
    }
}

@PreviewsDayNight
@Composable
internal fun UserProfileViewPreview(
    @PreviewParameter(UserProfileStateProvider::class) state: UserProfileState
) = ElementPreview {
    UserProfileView(
        state = state,
        onShareUser = {},
        goBack = {},
        onEditProfile = {},
        onOpenDm = {},
        onStartAudioCall = {},
        onStartVideoCall = {},
        openAvatarPreview = { _, _ -> },
        onVerifyClick = {},
    )
}
