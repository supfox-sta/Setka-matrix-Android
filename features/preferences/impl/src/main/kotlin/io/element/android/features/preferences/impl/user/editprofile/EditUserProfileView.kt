/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.user.editprofile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.designsystem.components.async.AsyncActionView
import io.element.android.libraries.designsystem.components.async.AsyncActionViewDefaults
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.components.dialogs.SaveChangesDialog
import io.element.android.libraries.designsystem.components.preferences.PreferenceCategory
import io.element.android.libraries.designsystem.modifiers.clearFocusOnTap
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.OutlinedButton
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Surface
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.designsystem.theme.parseSetkaColorOrNull
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.ui.components.AvatarActionBottomSheet
import io.element.android.libraries.matrix.ui.components.AvatarPickerState
import io.element.android.libraries.matrix.ui.components.AvatarPickerView
import io.element.android.libraries.matrix.ui.media.MediaRequestData
import io.element.android.libraries.permissions.api.PermissionsView
import io.element.android.libraries.ui.strings.CommonStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditUserProfileView(
    state: EditUserProfileState,
    onEditProfileSuccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val isAvatarActionsSheetVisible = remember { mutableStateOf(false) }

    fun onBackClick() {
        focusManager.clearFocus()
        state.eventSink(EditUserProfileEvent.Exit)
    }

    BackHandler(enabled = true, onBack = ::onBackClick)

    Scaffold(
        modifier = modifier.clearFocusOnTap(focusManager),
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = { BackButton(::onBackClick) },
                actions = {
                    TextButton(
                        text = stringResource(R.string.screen_edit_profile_done),
                        enabled = state.saveButtonEnabled,
                        onClick = {
                            focusManager.clearFocus()
                            state.eventSink(EditUserProfileEvent.Save)
                        },
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            EditProfileHeaderSection(
                state = state,
                onAvatarClick = {
                    focusManager.clearFocus()
                    isAvatarActionsSheetVisible.value = true
                },
            )

            Spacer(modifier = Modifier.height(12.dp))

            PreferenceCategory(title = "Информация", showTopDivider = false) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = ElementTheme.colors.bgSubtleSecondary,
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        EditProfileInfoRow(
                            label = stringResource(R.string.screen_edit_profile_email_label),
                            value = state.emailAddress ?: stringResource(R.string.screen_edit_profile_email_empty),
                        )
                        EditProfileInfoDivider()
                        OutlinedButton(
                            text = stringResource(
                                if (state.hasVerifiedEmail) {
                                    R.string.screen_edit_profile_change_email
                                } else {
                                    R.string.screen_edit_profile_add_email
                                }
                            ),
                            onClick = { state.eventSink(EditUserProfileEvent.OpenChangeEmail) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        EditProfileInfoDivider()
                        EditProfileInfoRow(
                            label = stringResource(R.string.screen_edit_profile_phone_label),
                            value = state.phoneNumber ?: stringResource(R.string.screen_edit_profile_phone_empty),
                        )
                        EditProfileInfoDivider()
                        OutlinedButton(
                            text = stringResource(R.string.screen_edit_profile_change_phone),
                            onClick = { state.eventSink(EditUserProfileEvent.OpenChangePhone) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        if (!state.hasVerifiedEmail) {
                            Text(
                                text = stringResource(R.string.screen_edit_profile_email_required_for_phone),
                                style = ElementTheme.typography.fontBodySmRegular,
                                color = ElementTheme.colors.textSecondary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        EditProfileInfoDivider()
                        EditProfileInfoRow(
                            label = stringResource(R.string.screen_edit_profile_username_label),
                            value = state.userId.value,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    text = stringResource(R.string.screen_edit_profile_customization_button),
                    onClick = { state.eventSink(EditUserProfileEvent.OpenProfileCustomization) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    text = stringResource(CommonStrings.action_signout),
                    onClick = { state.eventSink(EditUserProfileEvent.SignOut) },
                    destructive = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        AvatarActionBottomSheet(
            actions = state.avatarActions,
            isVisible = isAvatarActionsSheetVisible.value,
            onDismiss = { isAvatarActionsSheetVisible.value = false },
            onSelectAction = { state.eventSink(EditUserProfileEvent.HandleAvatarAction(it)) },
        )

        AsyncActionView(
            async = state.saveAction,
            progressDialog = {
                AsyncActionViewDefaults.ProgressDialog(
                    progressText = stringResource(R.string.screen_edit_profile_updating_details),
                )
            },
            confirmationDialog = { confirming ->
                when (confirming) {
                    is AsyncAction.ConfirmingCancellation -> {
                        SaveChangesDialog(
                            onSaveClick = { state.eventSink(EditUserProfileEvent.Save) },
                            onDiscardClick = { state.eventSink(EditUserProfileEvent.DiscardChanges) },
                            onDismiss = { state.eventSink(EditUserProfileEvent.CloseDialog) },
                        )
                    }
                }
            },
            onSuccess = { onEditProfileSuccess() },
            errorTitle = { stringResource(R.string.screen_edit_profile_error_title) },
            errorMessage = { stringResource(R.string.screen_edit_profile_error) },
            onErrorDismiss = { state.eventSink(EditUserProfileEvent.CloseDialog) },
        )
    }

    PermissionsView(state = state.cameraPermissionState)
}

@Composable
private fun EditProfileHeaderSection(
    state: EditUserProfileState,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val baseColor = parseSetkaColorOrNull(state.profileColorHex) ?: ElementTheme.colors.bgSubtlePrimary
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

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clipToBounds()
                .background(baseColor),
            contentAlignment = Alignment.Center,
        ) {
            val backgroundModel = state.profileBackgroundUrl?.takeIf { it.isNotBlank() }
            if (backgroundModel != null) {
                val imageModel = if (backgroundModel.startsWith("mxc://")) {
                    MediaRequestData(MediaSource(backgroundModel), MediaRequestData.Kind.Content)
                } else {
                    backgroundModel
                }
                AsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.76f,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(gradient)
            )
            if (state.isSetkaPlusActive) {
                IconButton(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    onClick = { state.eventSink(EditUserProfileEvent.PickProfileBackground) },
                ) {
                    Icon(
                        imageVector = CompoundIcons.Image(),
                        contentDescription = stringResource(R.string.screen_profile_customization_background_title),
                        tint = headerTextColor,
                    )
                }
                if (!state.profileBackgroundUrl.isNullOrBlank()) {
                    IconButton(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        onClick = { state.eventSink(EditUserProfileEvent.RemoveProfileBackground) },
                    ) {
                        Icon(
                            imageVector = CompoundIcons.Delete(),
                            contentDescription = stringResource(CommonStrings.action_remove),
                            tint = headerTextColor,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                AvatarPickerView(
                    state = remember(state.userAvatarUrl, state.displayName, state.userId) {
                        AvatarPickerState.Selected(
                            avatarData = AvatarData(
                                id = state.userId.value,
                                name = state.displayName,
                                size = AvatarSize.UserHeader,
                                url = state.userAvatarUrl,
                            ),
                            type = AvatarType.User,
                        )
                    },
                    onClick = onAvatarClick,
                )
                Spacer(modifier = Modifier.height(12.dp))
                BasicTextField(
                    value = state.displayName,
                    onValueChange = { state.eventSink(EditUserProfileEvent.UpdateDisplayName(it)) },
                    singleLine = true,
                    textStyle = ElementTheme.typography.fontHeadingLgBold.copy(
                        color = headerTextColor,
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (state.displayName.isBlank()) {
                                Text(
                                    text = stringResource(R.string.screen_edit_profile_display_name_placeholder),
                                    style = ElementTheme.typography.fontHeadingLgBold,
                                    color = headerTextColor.copy(alpha = 0.55f),
                                    textAlign = TextAlign.Center,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                Text(
                    text = state.userId.value,
                    style = ElementTheme.typography.fontBodyLgRegular,
                    color = if (useDarkText) Color.Black.copy(alpha = 0.68f) else Color.White.copy(alpha = 0.82f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            shape = RoundedCornerShape(18.dp),
            color = ElementTheme.colors.bgSubtleSecondary,
        ) {
            TextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                label = stringResource(R.string.screen_edit_profile_bio_label),
                value = state.bio,
                placeholder = stringResource(R.string.screen_edit_profile_bio_placeholder),
                singleLine = false,
                maxLines = 4,
                onValueChange = { state.eventSink(EditUserProfileEvent.UpdateBio(it)) },
            )
        }
    }
}

@Composable
private fun EditProfileInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = value,
            style = ElementTheme.typography.fontBodyLgMedium,
            color = ElementTheme.colors.textPrimary,
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
private fun EditProfileInfoDivider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ElementTheme.colors.borderDisabled)
    )
}

@PreviewsDayNight
@Composable
internal fun EditUserProfileViewPreview(@PreviewParameter(EditUserProfileStateProvider::class) state: EditUserProfileState) =
    ElementPreview {
        EditUserProfileView(
            onEditProfileSuccess = {},
            state = state,
        )
    }
