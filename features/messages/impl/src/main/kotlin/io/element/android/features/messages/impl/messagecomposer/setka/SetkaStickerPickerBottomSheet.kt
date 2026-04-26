/*
 * Copyright (c) 2026 Element Creations Ltd.
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.setka

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.R
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.ui.media.MediaRequestData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetkaStickerPickerBottomSheet(
    state: SetkaComposerState,
    onDismiss: () -> Unit,
    onSendSticker: (SetkaSticker) -> Unit,
    onOpenPackEditor: (SetkaPackKind, String?) -> Unit,
    onUploadToPack: (String, SetkaPackKind) -> Unit,
    onDeletePack: (String, String) -> Unit,
    onDeleteSticker: (String, String) -> Unit,
    onOpenSubscription: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedKind by rememberSaveable { mutableStateOf(SetkaPackKind.STICKER) }
    val availableKinds = buildList {
        if (state.stickerPacksOnly.isNotEmpty() || state.emojiPacksOnly.isEmpty()) add(SetkaPackKind.STICKER)
        if (state.emojiPacksOnly.isNotEmpty()) add(SetkaPackKind.EMOJI)
    }
    val packs = when (selectedKind) {
        SetkaPackKind.STICKER -> state.stickerPacksOnly
        SetkaPackKind.EMOJI -> state.emojiPacksOnly
    }
    var selectedPackId by rememberSaveable(selectedKind) { mutableStateOf<String?>(null) }

    LaunchedEffect(packs) {
        if (packs.none { it.id == selectedPackId }) {
            selectedPackId = packs.firstOrNull()?.id
        }
    }

    val selectedPack = packs.firstOrNull { it.id == selectedPackId } ?: packs.firstOrNull()
    val canManagePacks = state.isPlusActive

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.screen_room_setka_picker_title),
                style = ElementTheme.typography.fontHeadingMdBold,
            )

            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textCriticalPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(ElementTheme.colors.bgCriticalSubtle)
                        .padding(12.dp),
                )
            }

            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (!state.isPlusActive) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(ElementTheme.colors.bgSubtleSecondary)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.screen_room_setka_plus_inactive_title),
                        style = ElementTheme.typography.fontBodyLgMedium,
                    )
                    Text(
                        text = stringResource(R.string.screen_room_setka_plus_inactive_message),
                        style = ElementTheme.typography.fontBodyMdRegular,
                        color = ElementTheme.colors.textSecondary,
                    )
                    FilledTonalButton(onClick = onOpenSubscription) {
                        Text(stringResource(R.string.screen_room_setka_open_subscription))
                    }
                }
            }

            if (availableKinds.size > 1) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    availableKinds.forEach { kind ->
                        FilterChip(
                            selected = selectedKind == kind,
                            onClick = { selectedKind = kind },
                            label = {
                                Text(
                                    text = stringResource(
                                        if (kind == SetkaPackKind.STICKER) {
                                            R.string.screen_room_setka_stickers_tab
                                        } else {
                                            R.string.screen_room_setka_emoji_tab
                                        }
                                    )
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (kind == SetkaPackKind.STICKER) {
                                        CompoundIcons.Sticker()
                                    } else {
                                        CompoundIcons.ReactionAdd()
                                    },
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = { onOpenPackEditor(selectedKind, null) },
                    enabled = canManagePacks,
                ) {
                    Text(stringResource(R.string.screen_room_setka_new_pack))
                }
                if (selectedPack != null) {
                    OutlinedButton(
                        onClick = { onOpenPackEditor(selectedKind, selectedPack.id) },
                        enabled = canManagePacks,
                    ) {
                        Text(stringResource(R.string.screen_room_setka_edit_pack))
                    }
                    FilledTonalButton(
                        onClick = { onUploadToPack(selectedPack.id, selectedKind) },
                        enabled = canManagePacks && state.uploadingPackId == null,
                    ) {
                        Text(stringResource(R.string.screen_room_setka_upload_media))
                    }
                    OutlinedButton(
                        onClick = { onDeletePack(selectedPack.id, selectedPack.name) },
                        enabled = canManagePacks && state.deletingPackId == null,
                    ) {
                        Text(stringResource(R.string.screen_room_setka_delete_pack))
                    }
                }
            }

            if (selectedKind == SetkaPackKind.EMOJI) {
                Text(
                    text = stringResource(R.string.screen_room_setka_emoji_hint),
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textSecondary,
                )
            }

            if (packs.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    packs.forEach { pack ->
                        FilterChip(
                            selected = selectedPack?.id == pack.id,
                            onClick = { selectedPackId = pack.id },
                            label = { Text(pack.name) },
                        )
                    }
                }
            }

            if (selectedPack == null) {
                Text(
                    text = stringResource(
                        if (selectedKind == SetkaPackKind.STICKER) {
                            R.string.screen_room_setka_empty_stickers
                        } else {
                            R.string.screen_room_setka_empty_emoji
                        }
                    ),
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                )
            } else {
                LazyVerticalGrid(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    columns = GridCells.Adaptive(minSize = 80.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(selectedPack.stickers, key = { it.id }) { sticker ->
                        SetkaStickerCard(
                            sticker = sticker,
                            isBusy = state.deletingStickerKey == "${selectedPack.id}:${sticker.id}",
                            onClick = {
                                if (selectedKind == SetkaPackKind.STICKER) {
                                    onSendSticker(sticker)
                                }
                            },
                            onDelete = { onDeleteSticker(selectedPack.id, sticker.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SetkaStickerCard(
    sticker: SetkaSticker,
    isBusy: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(ElementTheme.colors.bgSubtleSecondary)
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        AsyncImage(
            model = MediaRequestData(MediaSource(sticker.mxcUrl), MediaRequestData.Kind.Content),
            contentDescription = sticker.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentScale = ContentScale.Crop,
        )
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(28.dp)
                .background(
                    color = if (isBusy) ElementTheme.colors.bgSubtlePrimary else Color.Black.copy(alpha = 0.48f),
                    shape = MaterialTheme.shapes.medium,
                )
        ) {
            Icon(
                imageVector = CompoundIcons.Close(),
                contentDescription = null,
                tint = Color.White,
            )
        }
    }
}
