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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton as MaterialTextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.designsystem.components.async.AsyncActionView
import io.element.android.libraries.designsystem.components.async.AsyncActionViewDefaults
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.components.dialogs.SaveChangesDialog
import io.element.android.libraries.designsystem.modifiers.clearFocusOnTap
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
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
import kotlinx.coroutines.launch

private val setkaProfileColorPresets = listOf(
    "#7B61FF",
    "#5A8CFF",
    "#0F9D8A",
    "#F26B5B",
    "#F3B53F",
    "#F08AC3",
    "#5B6475",
    "#111827",
)

private enum class EmojiPickerTarget {
    Badge,
    Status,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditUserProfileView(
    state: EditUserProfileState,
    onEditProfileSuccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val isAvatarActionsSheetVisible = remember { mutableStateOf(false) }
    val emojiSheetState = rememberModalBottomSheetState()
    val colorSheetState = rememberModalBottomSheetState()
    var selectedEmojiPackId by rememberSaveable { mutableStateOf<String?>(null) }
    var showProfileColorPicker by rememberSaveable { mutableStateOf(false) }
    var emojiPickerTarget by rememberSaveable { mutableStateOf<EmojiPickerTarget?>(null) }

    fun onBackClick() {
        focusManager.clearFocus()
        state.eventSink(EditUserProfileEvent.Exit)
    }

    BackHandler(enabled = true, onBack = ::onBackClick)

    Scaffold(
        modifier = modifier.clearFocusOnTap(focusManager),
        topBar = {
            TopAppBar(
                titleStr = stringResource(R.string.screen_edit_profile_title),
                navigationIcon = { BackButton(::onBackClick) },
                actions = {
                    TextButton(
                        text = stringResource(CommonStrings.action_save),
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
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProfilePreviewCard(
                state = state,
                onAvatarClick = {
                    focusManager.clearFocus()
                    isAvatarActionsSheetVisible.value = true
                },
            )
            ProfileInfoPreviewCard(state = state)
            ProfileEditorCard(
                title = "Основное",
                content = {
                    TextField(
                        label = stringResource(R.string.screen_edit_profile_display_name),
                        value = state.displayName,
                        placeholder = stringResource(R.string.screen_edit_profile_display_name_placeholder),
                        singleLine = true,
                        onValueChange = { state.eventSink(EditUserProfileEvent.UpdateDisplayName(it)) },
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(
                        label = stringResource(R.string.screen_edit_profile_bio_label),
                        value = state.bio,
                        placeholder = stringResource(R.string.screen_edit_profile_bio_placeholder),
                        singleLine = false,
                        maxLines = 4,
                        onValueChange = { state.eventSink(EditUserProfileEvent.UpdateBio(it)) },
                    )
                },
            )
            ProfileAppearanceCard(
                state = state,
                onOpenColorPicker = { showProfileColorPicker = true },
            )
            ProfileImageSetting(
                title = "Фон профиля",
                subtitle = if (state.isSetkaPlusActive) {
                    "Показывается в шапке профиля"
                } else {
                    "Доступно только с Setka Plus"
                },
                imageUrl = state.profileBackgroundUrl,
                enabled = state.isSetkaPlusActive,
                onChoose = { state.eventSink(EditUserProfileEvent.PickProfileBackground) },
                onClear = { state.eventSink(EditUserProfileEvent.RemoveProfileBackground) },
            )
            EmojiSettingRow(
                title = "Эмодзи рядом с именем",
                subtitle = when {
                    !state.isSetkaPlusActive -> stringResource(R.string.screen_edit_profile_badge_plus_required)
                    state.badgeEmojiPacks.isEmpty() -> "Нет доступных паков эмодзи"
                    else -> "Показывается справа от имени"
                },
                emojiUrl = state.badgeEmojiMxcUrl,
                enabled = state.isSetkaPlusActive,
                onChoose = {
                    selectedEmojiPackId = selectedEmojiPackId ?: state.badgeEmojiPacks.firstOrNull()?.id
                    emojiPickerTarget = EmojiPickerTarget.Badge
                },
                onClear = { state.eventSink(EditUserProfileEvent.SetBadgeEmoji(null)) },
            )
            EmojiSettingRow(
                title = "Статус на аватаре",
                subtitle = when {
                    !state.isSetkaPlusActive -> "Доступно только с Setka Plus"
                    state.badgeEmojiPacks.isEmpty() -> "Нет доступных паков эмодзи"
                    else -> "Показывается в углу аватара"
                },
                emojiUrl = state.statusEmojiMxcUrl,
                enabled = state.isSetkaPlusActive,
                onChoose = {
                    selectedEmojiPackId = selectedEmojiPackId ?: state.badgeEmojiPacks.firstOrNull()?.id
                    emojiPickerTarget = EmojiPickerTarget.Status
                },
                onClear = { state.eventSink(EditUserProfileEvent.SetStatusEmoji(null)) },
            )
        }

        AvatarActionBottomSheet(
            actions = state.avatarActions,
            isVisible = isAvatarActionsSheetVisible.value,
            onDismiss = { isAvatarActionsSheetVisible.value = false },
            onSelectAction = { state.eventSink(EditUserProfileEvent.HandleAvatarAction(it)) },
        )

        if (emojiPickerTarget != null) {
            val packs = state.badgeEmojiPacks
            val selectedPack = packs.firstOrNull { it.id == selectedEmojiPackId } ?: packs.firstOrNull()
            ModalBottomSheet(
                onDismissRequest = { emojiPickerTarget = null },
                sheetState = emojiSheetState,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = when (emojiPickerTarget) {
                            EmojiPickerTarget.Badge -> "Выберите эмодзи рядом с именем"
                            EmojiPickerTarget.Status -> "Выберите статус на аватаре"
                            null -> ""
                        },
                        style = ElementTheme.typography.fontHeadingMdBold,
                    )
                    if (packs.isEmpty()) {
                        Text(
                            text = "Нет доступных паков эмодзи. Сначала добавьте или импортируйте набор.",
                            style = ElementTheme.typography.fontBodyMdRegular,
                            color = ElementTheme.colors.textSecondary,
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            packs.forEach { pack ->
                                FilterChip(
                                    selected = selectedPack?.id == pack.id,
                                    onClick = { selectedEmojiPackId = pack.id },
                                    label = { Text(pack.name) },
                                )
                            }
                        }
                        selectedPack?.let { pack ->
                            LazyVerticalGrid(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp),
                                columns = GridCells.Adaptive(minSize = 56.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(pack.stickers, key = { it.id }) { sticker ->
                                    Surface(
                                        modifier = Modifier
                                            .clip(MaterialTheme.shapes.large)
                                            .clickable {
                                                coroutineScope.launch {
                                                    emojiSheetState.hide()
                                                    when (emojiPickerTarget) {
                                                        EmojiPickerTarget.Badge -> state.eventSink(EditUserProfileEvent.SetBadgeEmoji(sticker.mxcUrl))
                                                        EmojiPickerTarget.Status -> state.eventSink(EditUserProfileEvent.SetStatusEmoji(sticker.mxcUrl))
                                                        null -> Unit
                                                    }
                                                    emojiPickerTarget = null
                                                }
                                            },
                                        color = ElementTheme.colors.bgSubtleSecondary,
                                        shape = MaterialTheme.shapes.large,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(1f)
                                                .padding(10.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            AsyncImage(
                                                model = MediaRequestData(MediaSource(sticker.mxcUrl), MediaRequestData.Kind.Content),
                                                contentDescription = sticker.name,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Fit,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

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

        if (showProfileColorPicker) {
            ModalBottomSheet(
                onDismissRequest = { showProfileColorPicker = false },
                sheetState = colorSheetState,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Палитра профиля",
                        style = ElementTheme.typography.fontHeadingMdBold,
                    )
                    Text(
                        text = "Можно выбрать готовый цвет ниже или ввести свой HEX вручную в поле цвета.",
                        style = ElementTheme.typography.fontBodySmRegular,
                        color = ElementTheme.colors.textSecondary,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        setkaProfileColorPresets.forEach { hex ->
                            val swatchColor = parseSetkaColorOrNull(hex) ?: return@forEach
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(swatchColor)
                                    .clickable {
                                        state.eventSink(EditUserProfileEvent.UpdateProfileColorHex(hex))
                                        showProfileColorPicker = false
                                    }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        MaterialTextButton(onClick = { showProfileColorPicker = false }) {
                            Text(text = "Закрыть")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    PermissionsView(state = state.cameraPermissionState)
}

@Composable
private fun ProfilePreviewCard(
    state: EditUserProfileState,
    onAvatarClick: () -> Unit,
) {
    val previewColor = parseSetkaColorOrNull(state.profileColorHex) ?: ElementTheme.colors.bgSubtlePrimary
    val headerTextColor = if (previewColor.luminance() > 0.52f) {
        Color.Black.copy(alpha = 0.88f)
    } else {
        Color.White.copy(alpha = 0.96f)
    }
    val secondaryHeaderTextColor = headerTextColor.copy(
        alpha = if (previewColor.luminance() > 0.52f) 0.68f else 0.82f
    )
    val headerBrush = Brush.verticalGradient(
        colors = listOf(
            previewColor.copy(alpha = 0.42f),
            previewColor.copy(alpha = 0.22f),
            previewColor.copy(alpha = 0.14f),
        )
    )
    val avatarPickerState = remember(state.userAvatarUrl, state.displayName, state.userId) {
        AvatarPickerState.Selected(
            avatarData = AvatarData(
                id = state.userId.value,
                name = state.displayName,
                size = AvatarSize.EditProfileDetails,
                url = state.userAvatarUrl,
            ),
            type = AvatarType.User,
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = ElementTheme.colors.bgSubtleSecondary,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(previewColor),
        ) {
            if (!state.profileBackgroundUrl.isNullOrBlank()) {
                AsyncImage(
                    model = MediaRequestData(MediaSource(state.profileBackgroundUrl), MediaRequestData.Kind.Content),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.72f,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(headerBrush)
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box {
                    AvatarPickerView(
                        state = avatarPickerState,
                        onClick = onAvatarClick,
                    )
                    if (!state.statusEmojiMxcUrl.isNullOrBlank()) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 2.dp, end = 2.dp),
                            shape = CircleShape,
                            color = ElementTheme.colors.bgCanvasDefault.copy(alpha = 0.94f),
                        ) {
                            AsyncImage(
                                model = MediaRequestData(MediaSource(state.statusEmojiMxcUrl), MediaRequestData.Kind.Content),
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(20.dp),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.displayName.ifBlank { state.userId.value },
                        style = ElementTheme.typography.fontHeadingLgBold,
                        textAlign = TextAlign.Center,
                        color = headerTextColor,
                    )
                    if (!state.badgeEmojiMxcUrl.isNullOrBlank()) {
                        Spacer(modifier = Modifier.size(8.dp))
                        AsyncImage(
                            model = MediaRequestData(MediaSource(state.badgeEmojiMxcUrl), MediaRequestData.Kind.Content),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = state.userId.value,
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = secondaryHeaderTextColor,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (!state.statusEmojiMxcUrl.isNullOrBlank()) {
                        "Статус установлен"
                    } else {
                        "Профиль можно настроить ниже"
                    },
                    style = ElementTheme.typography.fontBodySmRegular,
                    color = secondaryHeaderTextColor,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ProfileInfoPreviewCard(state: EditUserProfileState) {
    val primaryTextColor = if (ElementTheme.isLightTheme) {
        Color.Black.copy(alpha = 0.9f)
    } else {
        Color.White.copy(alpha = 0.96f)
    }
    val secondaryTextColor = if (ElementTheme.isLightTheme) {
        Color.Black.copy(alpha = 0.62f)
    } else {
        Color.White.copy(alpha = 0.72f)
    }
    ProfileEditorCard(
        title = "Информация",
        content = {
            ProfileInfoPreviewRow(
                label = "Имя пользователя",
                value = state.userId.value,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
            )
            Spacer(modifier = Modifier.height(12.dp))
            ProfileInfoPreviewRow(
                label = "О себе",
                value = state.bio.ifBlank { "Описание пока не заполнено" },
            )
            Spacer(modifier = Modifier.height(12.dp))
            ProfileInfoPreviewRow(
                label = "Фон профиля",
                value = if (state.profileBackgroundUrl.isNullOrBlank()) "Не установлен" else "Установлен",
            )
        },
    )
}

@Composable
private fun ProfileInfoPreviewRow(
    label: String,
    value: String,
    primaryTextColor: Color = if (ElementTheme.isLightTheme) Color.Black.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.96f),
    secondaryTextColor: Color = if (ElementTheme.isLightTheme) Color.Black.copy(alpha = 0.62f) else Color.White.copy(alpha = 0.72f),
) {
    Column {
        Text(
            text = value,
            style = ElementTheme.typography.fontBodyLgMedium,
            color = primaryTextColor,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = ElementTheme.typography.fontBodySmRegular,
            color = secondaryTextColor,
        )
    }
}

@Composable
private fun ProfileEditorCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = ElementTheme.colors.bgSubtleSecondary,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                        text = title,
                        style = ElementTheme.typography.fontHeadingSmMedium,
            )
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileAppearanceCard(
    state: EditUserProfileState,
    onOpenColorPicker: () -> Unit,
) {
    ProfileEditorCard(
        title = "Оформление профиля",
        content = {
            val selectedColor = parseSetkaColorOrNull(state.profileColorHex)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(selectedColor ?: ElementTheme.colors.bgCanvasDefault)
                )
                Spacer(modifier = Modifier.size(12.dp))
                TextField(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.screen_edit_profile_profile_color_label),
                    value = state.profileColorHex,
                    placeholder = stringResource(R.string.screen_edit_profile_profile_color_placeholder),
                    singleLine = true,
                    onValueChange = { state.eventSink(EditUserProfileEvent.UpdateProfileColorHex(it)) },
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Готовые цвета",
                style = ElementTheme.typography.fontBodyMdMedium,
                color = ElementTheme.colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                setkaProfileColorPresets.forEach { hex ->
                    val swatchColor = parseSetkaColorOrNull(hex) ?: return@forEach
                    val isSelected = selectedColor == swatchColor
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 38.dp else 34.dp)
                            .clip(CircleShape)
                            .background(swatchColor)
                            .clickable { state.eventSink(EditUserProfileEvent.UpdateProfileColorHex(hex)) }
                    )
                }
                OutlinedButton(onClick = onOpenColorPicker) {
                    Text(text = "Свой")
                }
            }
        },
    )
}

@Composable
private fun ProfileImageSetting(
    title: String,
    subtitle: String,
    imageUrl: String?,
    enabled: Boolean,
    onChoose: () -> Unit,
    onClear: () -> Unit,
) {
    ProfileEditorCard(
        title = title,
        content = {
            Text(
                text = subtitle,
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(128.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(ElementTheme.colors.bgCanvasDefault),
                contentAlignment = Alignment.Center,
            ) {
                if (!imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = MediaRequestData(MediaSource(imageUrl), MediaRequestData.Kind.Content),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        text = "Фон не выбран",
                        color = ElementTheme.colors.textSecondary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onChoose, enabled = enabled) {
                    Text(text = "Выбрать")
                }
                OutlinedButton(onClick = onClear, enabled = imageUrl != null) {
                    Text(text = "Убрать")
                }
            }
        },
    )
}

@Composable
private fun EmojiSettingRow(
    title: String,
    subtitle: String,
    emojiUrl: String?,
    enabled: Boolean,
    onChoose: () -> Unit,
    onClear: () -> Unit,
) {
    ProfileEditorCard(
        title = title,
        content = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!emojiUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = MediaRequestData(MediaSource(emojiUrl), MediaRequestData.Kind.Content),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(ElementTheme.colors.bgCanvasDefault),
                    )
                }
                Spacer(modifier = Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subtitle,
                        style = ElementTheme.typography.fontBodySmRegular,
                        color = ElementTheme.colors.textSecondary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = "Выбрать",
                    enabled = enabled,
                    onClick = onChoose,
                )
                TextButton(
                    text = "Убрать",
                    enabled = emojiUrl != null,
                    onClick = onClear,
                )
            }
        },
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
