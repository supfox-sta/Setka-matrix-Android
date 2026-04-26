/*
 * Copyright (c) 2026 Element Creations Ltd.
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.setka

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.impl.R
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Surface
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.ui.media.MediaRequestData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetkaSharedPackPreviewSheet(
    state: SetkaSharedPackPreviewState,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val pack = state.pack
            val isInstalled = state.installedPackId != null
            val packTitle = pack?.name ?: stringResource(R.string.screen_room_setka_shared_pack_title)
            val packKindText = stringResource(
                if (pack?.kind == SetkaPackKind.EMOJI) {
                    R.string.screen_room_setka_shared_pack_kind_emoji
                } else {
                    R.string.screen_room_setka_shared_pack_kind_sticker
                }
            )

            Text(
                text = "Просмотр пака",
                style = ElementTheme.typography.fontHeadingMdBold,
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = ElementTheme.colors.bgSubtleSecondary,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(ElementTheme.colors.bgCanvasDefault),
                        contentAlignment = Alignment.Center,
                    ) {
                        val iconSticker = pack?.stickers?.firstOrNull()
                        if (iconSticker != null) {
                            AsyncImage(
                                model = MediaRequestData(MediaSource(iconSticker.mxcUrl), MediaRequestData.Kind.Content),
                                contentDescription = iconSticker.name,
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Text(
                                text = "S",
                                style = ElementTheme.typography.fontHeadingMdBold,
                            )
                        }
                    }
                    Text(
                        text = packTitle,
                        style = ElementTheme.typography.fontHeadingSmMedium,
                    )
                    Text(
                        text = if (isInstalled) {
                            "$packKindText • уже в коллекции"
                        } else {
                            packKindText
                        },
                        style = ElementTheme.typography.fontBodySmRegular,
                        color = ElementTheme.colors.textSecondary,
                    )
                    when {
                        state.isLoading && pack == null -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 180.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        pack != null -> {
                            LazyVerticalGrid(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 180.dp, max = 320.dp),
                                columns = GridCells.Adaptive(minSize = 70.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(pack.stickers, key = { it.id }) { sticker ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f),
                                        shape = RoundedCornerShape(18.dp),
                                        color = ElementTheme.colors.bgCanvasDefault,
                                    ) {
                                        AsyncImage(
                                            model = MediaRequestData(MediaSource(sticker.mxcUrl), MediaRequestData.Kind.Content),
                                            contentDescription = sticker.name,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(1f)
                                                .padding(10.dp),
                                            contentScale = ContentScale.Fit,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            FilledTonalButton(
                onClick = onApply,
                enabled = pack != null && !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (isInstalled) {
                        "Удалить пак"
                    } else {
                        stringResource(R.string.screen_room_setka_shared_pack_add)
                    }
                )
            }
        }
    }
}
