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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.designsystem.components.async.AsyncActionView
import io.element.android.libraries.designsystem.components.async.AsyncActionViewDefaults
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.components.dialogs.SaveChangesDialog
import io.element.android.libraries.designsystem.modifiers.clearFocusOnTap
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Surface
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.designsystem.theme.parseSetkaColorOrNull
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.ui.media.MediaRequestData
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
fun ProfileCustomizationView(
    state: ProfileCustomizationState,
    onSaveSuccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val emojiSheetState = rememberModalBottomSheetState()
    val colorSheetState = rememberModalBottomSheetState()
    var selectedEmojiPackId by rememberSaveable { mutableStateOf<String?>(null) }
    var showProfileColorPicker by rememberSaveable { mutableStateOf(false) }
    var emojiPickerTarget by rememberSaveable { mutableStateOf<EmojiPickerTarget?>(null) }

    fun onBackClick() {
        focusManager.clearFocus()
        state.eventSink(ProfileCustomizationEvent.Exit)
    }

    BackHandler(enabled = true, onBack = ::onBackClick)

    Scaffold(
        modifier = modifier.clearFocusOnTap(focusManager),
        topBar = {
            TopAppBar(
                titleStr = stringResource(R.string.screen_profile_customization_title),
                navigationIcon = { BackButton(::onBackClick) },
                actions = {
                    TextButton(
                        text = stringResource(CommonStrings.action_save),
                        enabled = state.saveButtonEnabled,
                        onClick = {
                            focusManager.clearFocus()
                            state.eventSink(ProfileCustomizationEvent.Save)
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
            ProfileAppearanceCard(
                state = state,
                onOpenColorPicker = { showProfileColorPicker = true },
            )
            ProfileImageSetting(
                title = stringResource(R.string.screen_profile_customization_background_title),
                subtitle = if (state.isSetkaPlusActive) {
                    stringResource(R.string.screen_profile_customization_background_subtitle)
                } else {
                    stringResource(R.string.screen_edit_profile_badge_plus_required)
                },
                imageUrl = state.profileBackgroundUrl,
                enabled = state.isSetkaPlusActive,
                onChoose = { state.eventSink(ProfileCustomizationEvent.PickProfileBackground) },
                onClear = { state.eventSink(ProfileCustomizationEvent.RemoveProfileBackground) },
            )
            EmojiSettingRow(
                title = stringResource(R.string.screen_profile_customization_badge_title),
                subtitle = when {
                    !state.isSetkaPlusActive -> stringResource(R.string.screen_edit_profile_badge_plus_required)
                    state.badgeEmojiPacks.isEmpty() -> stringResource(R.string.screen_profile_customization_no_packs)
                    else -> stringResource(R.string.screen_profile_customization_badge_subtitle)
                },
                emojiUrl = state.badgeEmojiMxcUrl,
                enabled = state.isSetkaPlusActive,
                onChoose = {
                    selectedEmojiPackId = selectedEmojiPackId ?: state.badgeEmojiPacks.firstOrNull()?.id
                    emojiPickerTarget = EmojiPickerTarget.Badge
                },
                onClear = { state.eventSink(ProfileCustomizationEvent.SetBadgeEmoji(null)) },
            )
            EmojiSettingRow(
                title = stringResource(R.string.screen_profile_customization_status_title),
                subtitle = when {
                    !state.isSetkaPlusActive -> stringResource(R.string.screen_edit_profile_badge_plus_required)
                    state.badgeEmojiPacks.isEmpty() -> stringResource(R.string.screen_profile_customization_no_packs)
                    else -> stringResource(R.string.screen_profile_customization_status_subtitle)
                },
                emojiUrl = state.statusEmojiMxcUrl,
                enabled = state.isSetkaPlusActive,
                onChoose = {
                    selectedEmojiPackId = selectedEmojiPackId ?: state.badgeEmojiPacks.firstOrNull()?.id
                    emojiPickerTarget = EmojiPickerTarget.Status
                },
                onClear = { state.eventSink(ProfileCustomizationEvent.SetStatusEmoji(null)) },
            )
        }

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
                            EmojiPickerTarget.Badge -> stringResource(R.string.screen_profile_customization_badge_picker)
                            EmojiPickerTarget.Status -> stringResource(R.string.screen_profile_customization_status_picker)
                            null -> ""
                        },
                        style = ElementTheme.typography.fontHeadingMdBold,
                    )
                    if (packs.isEmpty()) {
                        Text(
                            text = stringResource(R.string.screen_profile_customization_no_packs),
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
                                                        EmojiPickerTarget.Badge ->
                                                            state.eventSink(ProfileCustomizationEvent.SetBadgeEmoji(sticker.mxcUrl))
                                                        EmojiPickerTarget.Status ->
                                                            state.eventSink(ProfileCustomizationEvent.SetStatusEmoji(sticker.mxcUrl))
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
                            onSaveClick = { state.eventSink(ProfileCustomizationEvent.Save) },
                            onDiscardClick = { state.eventSink(ProfileCustomizationEvent.DiscardChanges) },
                            onDismiss = { state.eventSink(ProfileCustomizationEvent.CloseDialog) },
                        )
                    }
                }
            },
            onSuccess = { onSaveSuccess() },
            errorTitle = { stringResource(R.string.screen_edit_profile_error_title) },
            errorMessage = { stringResource(R.string.screen_edit_profile_error) },
            onErrorDismiss = { state.eventSink(ProfileCustomizationEvent.CloseDialog) },
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
                        text = stringResource(R.string.screen_profile_customization_color_picker_title),
                        style = ElementTheme.typography.fontHeadingMdBold,
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
                                        state.eventSink(ProfileCustomizationEvent.UpdateProfileColorHex(hex))
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
                            Text(text = stringResource(CommonStrings.action_close))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileAppearanceCard(
    state: ProfileCustomizationState,
    onOpenColorPicker: () -> Unit,
) {
    ProfileEditorCard(
        title = stringResource(R.string.screen_profile_customization_appearance_title),
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
                    onValueChange = { state.eventSink(ProfileCustomizationEvent.UpdateProfileColorHex(it)) },
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
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
                            .clickable { state.eventSink(ProfileCustomizationEvent.UpdateProfileColorHex(hex)) }
                    )
                }
                OutlinedButton(onClick = onOpenColorPicker) {
                    Text(text = stringResource(R.string.screen_profile_customization_custom_color))
                }
            }
        },
    )
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
                        text = stringResource(R.string.screen_profile_customization_background_empty),
                        color = ElementTheme.colors.textSecondary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onChoose, enabled = enabled) {
                    Text(text = stringResource(R.string.screen_profile_customization_choose))
                }
                OutlinedButton(onClick = onClear, enabled = imageUrl != null) {
                    Text(text = stringResource(R.string.screen_profile_customization_clear))
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
                Text(
                    text = subtitle,
                    style = ElementTheme.typography.fontBodySmRegular,
                    color = ElementTheme.colors.textSecondary,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = stringResource(R.string.screen_edit_profile_badge_choose),
                    enabled = enabled,
                    onClick = onChoose,
                )
                TextButton(
                    text = stringResource(R.string.screen_edit_profile_badge_clear),
                    enabled = emojiUrl != null,
                    onClick = onClear,
                )
            }
        },
    )
}
