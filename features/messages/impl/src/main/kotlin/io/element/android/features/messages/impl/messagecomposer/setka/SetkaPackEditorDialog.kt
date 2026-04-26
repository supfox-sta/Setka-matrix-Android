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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
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
import io.element.android.libraries.designsystem.theme.components.FilledTextField
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Surface
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.ui.media.MediaRequestData
import io.element.android.libraries.ui.strings.CommonStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetkaPackEditorDialog(
    state: SetkaComposerState,
    editorState: SetkaPackEditorState,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onUploadToPack: (String, SetkaPackKind) -> Unit,
    onSharePack: (String) -> Unit,
    onDeletePack: (String, String) -> Unit,
    onDeleteSticker: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentPack = state.stickerPacks.firstOrNull { it.id == editorState.packId }
    val isBusy = state.uploadingPackId != null || state.deletingPackId != null || state.deletingStickerKey != null
    var packName by rememberSaveable(editorState.packId, editorState.initialName) {
        mutableStateOf(editorState.initialName)
    }
    var didAttemptSave by rememberSaveable(editorState.packId) { mutableStateOf(false) }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = ElementTheme.colors.bgCanvasDefault,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            if (currentPack == null) {
                                R.string.screen_room_setka_create_pack_title
                            } else {
                                R.string.screen_room_setka_edit_pack_title
                            }
                        ),
                        style = ElementTheme.typography.fontHeadingMdBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = CompoundIcons.Close(),
                            contentDescription = stringResource(CommonStrings.action_close),
                        )
                    }
                }

                FilledTextField(
                    value = packName,
                    onValueChange = { packName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = {
                        Text(text = stringResource(R.string.screen_room_setka_pack_name_placeholder))
                    },
                    isError = didAttemptSave && packName.isBlank(),
                )

                state.errorMessage?.let { errorMessage ->
                    Text(
                        text = errorMessage,
                        style = ElementTheme.typography.fontBodyMdRegular,
                        color = ElementTheme.colors.textCriticalPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(ElementTheme.colors.bgCriticalSubtle)
                            .padding(12.dp),
                    )
                }

                if (currentPack == null) {
                    EmptyPackEditorState(
                        text = stringResource(R.string.screen_room_setka_pack_editor_empty_new),
                    )
                } else if (currentPack.stickers.isEmpty()) {
                    EmptyPackEditorState(
                        text = stringResource(R.string.screen_room_setka_pack_editor_empty_existing),
                    )
                }

                if (currentPack != null) {
                    LazyVerticalGrid(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        columns = GridCells.Adaptive(minSize = 74.dp),
                        contentPadding = PaddingValues(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(currentPack.stickers, key = { it.id }) { sticker ->
                            EditablePackItem(
                                sticker = sticker,
                                isDeleting = state.deletingStickerKey == "${currentPack.id}:${sticker.id}",
                                onDelete = { onDeleteSticker(currentPack.id, sticker.id) },
                            )
                        }
                        item(key = "add-pack-media") {
                            AddPackMediaTile(
                                enabled = !isBusy,
                                onClick = { onUploadToPack(currentPack.id, editorState.kind) },
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (currentPack != null) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = { onDeletePack(currentPack.id, currentPack.name) },
                            enabled = !isBusy,
                        ) {
                            Text(stringResource(R.string.screen_room_setka_delete_pack))
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = { onSharePack(currentPack.id) },
                            enabled = !isBusy,
                        ) {
                            Text(stringResource(CommonStrings.action_share))
                        }
                    }
                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            didAttemptSave = true
                            if (packName.isNotBlank()) onSave(packName)
                        },
                        enabled = !isBusy && packName.isNotBlank(),
                    ) {
                        Text(
                            text = stringResource(
                                if (currentPack == null) {
                                    R.string.screen_room_setka_create_pack_button
                                } else {
                                    R.string.screen_room_setka_done_button
                                }
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyPackEditorState(
    text: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(ElementTheme.colors.bgSubtleSecondary)
            .padding(horizontal = 16.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EditablePackItem(
    sticker: SetkaSticker,
    isDeleting: Boolean,
    onDelete: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(ElementTheme.colors.bgSubtleSecondary)
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
                    color = if (isDeleting) ElementTheme.colors.bgSubtlePrimary else Color.Black.copy(alpha = 0.48f),
                    shape = MaterialTheme.shapes.medium,
                )
        ) {
            Icon(
                imageVector = CompoundIcons.Close(),
                contentDescription = stringResource(R.string.screen_room_setka_delete_pack),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun AddPackMediaTile(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(ElementTheme.colors.bgSubtleSecondary.copy(alpha = 0.56f))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.medium)
                .background(ElementTheme.colors.bgCanvasDefault.copy(alpha = 0.42f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = CompoundIcons.Plus(),
                contentDescription = null,
            )
        }
    }
}
