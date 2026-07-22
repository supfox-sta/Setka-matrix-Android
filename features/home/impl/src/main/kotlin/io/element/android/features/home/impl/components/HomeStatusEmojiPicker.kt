/*

 * Copyright (c) 2026 Setka Matrix

 *

 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.

 */



package io.element.android.features.home.impl.components



import androidx.compose.foundation.background

import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.FlowRow

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.size

import androidx.compose.foundation.layout.width

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.verticalScroll

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.runtime.Composable

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip

import androidx.compose.ui.layout.ContentScale

import androidx.compose.ui.unit.dp

import coil3.compose.AsyncImage

import io.element.android.compound.theme.ElementTheme

import io.element.android.compound.tokens.generated.CompoundIcons

import io.element.android.features.home.impl.HomeEmojiPack

import io.element.android.features.home.impl.HomeStatusEmojiState

import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator

import io.element.android.libraries.designsystem.theme.components.DropdownMenu

import io.element.android.libraries.designsystem.theme.components.Icon

import io.element.android.libraries.designsystem.theme.components.IconButton

import io.element.android.libraries.designsystem.theme.components.Text

import io.element.android.libraries.matrix.api.media.MediaSource

import io.element.android.libraries.matrix.ui.media.MediaRequestData



@Composable

internal fun HomeStatusEmojiButton(

    state: HomeStatusEmojiState,

    onOpenPicker: () -> Unit,

    onDismissPicker: () -> Unit,

    onSelectEmoji: (String?) -> Unit,

    modifier: Modifier = Modifier,

) {

    Box(modifier = modifier) {

        IconButton(onClick = onOpenPicker) {

            if (!state.selectedMxcUrl.isNullOrBlank()) {

                AsyncImage(

                    modifier = Modifier

                        .size(24.dp)

                        .clip(RoundedCornerShape(6.dp)),

                    model = MediaRequestData(

                        source = MediaSource(state.selectedMxcUrl),

                        kind = MediaRequestData.Kind.Content,

                    ),

                    contentDescription = null,

                    contentScale = ContentScale.Crop,

                )

            } else {

                Icon(

                    imageVector = CompoundIcons.ReactionAdd(),

                    contentDescription = null,

                )

            }

        }

        HomeStatusEmojiPickerMenu(

            state = state,

            onDismissPicker = onDismissPicker,

            onSelectEmoji = onSelectEmoji,

        )

    }

}



@Composable

internal fun HomeStatusEmojiPickerMenu(

    state: HomeStatusEmojiState,

    onDismissPicker: () -> Unit,

    onSelectEmoji: (String?) -> Unit,

) {

    DropdownMenu(

        expanded = state.showPicker,

        onDismissRequest = onDismissPicker,

        modifier = Modifier.width(280.dp),

    ) {

        Column(

            modifier = Modifier

                .width(280.dp)

                .heightIn(max = 320.dp)

                .verticalScroll(rememberScrollState())

                .padding(horizontal = 8.dp, vertical = 4.dp),

        ) {

            Text(

                text = "Эмодзи у имени",

                style = ElementTheme.typography.fontBodyMdMedium,

                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),

            )

            if (state.isLoadingPacks) {

                Box(

                    modifier = Modifier

                        .fillMaxWidth()

                        .padding(24.dp),

                    contentAlignment = Alignment.Center,

                ) {

                    CircularProgressIndicator(modifier = Modifier.size(24.dp))

                }

            } else if (state.packs.isEmpty()) {

                Text(

                    text = "Нет доступных эмодзи-паков",

                    style = ElementTheme.typography.fontBodySmRegular,

                    color = ElementTheme.colors.textSecondary,

                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),

                )

            } else {

                state.packs.forEach { pack ->

                    EmojiPackSection(

                        pack = pack,

                        onSelectEmoji = onSelectEmoji,

                    )

                }

            }

            if (state.selectedMxcUrl != null) {

                Text(

                    text = "Убрать эмодзи",

                    style = ElementTheme.typography.fontBodyMdRegular,

                    color = ElementTheme.colors.textCriticalPrimary,

                    modifier = Modifier

                        .fillMaxWidth()

                        .clip(RoundedCornerShape(8.dp))

                        .clickable {

                            onSelectEmoji(null)

                            onDismissPicker()

                        }

                        .padding(horizontal = 12.dp, vertical = 10.dp),

                )

            }

        }

    }

}



@Composable

private fun EmojiPackSection(

    pack: HomeEmojiPack,

    onSelectEmoji: (String?) -> Unit,

) {

    Text(

        text = pack.name,

        style = ElementTheme.typography.fontBodySmMedium,

        color = ElementTheme.colors.textSecondary,

        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),

    )

    FlowRow(

        modifier = Modifier

            .fillMaxWidth()

            .padding(horizontal = 4.dp, vertical = 4.dp),

        horizontalArrangement = Arrangement.spacedBy(6.dp),

        verticalArrangement = Arrangement.spacedBy(6.dp),

        maxItemsInEachRow = 5,

    ) {

        pack.stickers.forEach { sticker ->

            AsyncImage(

                modifier = Modifier

                    .size(44.dp)

                    .clip(RoundedCornerShape(8.dp))

                    .background(ElementTheme.colors.bgSubtlePrimary)

                    .clickable {

                        onSelectEmoji(sticker.mxcUrl)

                    },

                model = MediaRequestData(

                    source = MediaSource(sticker.mxcUrl),

                    kind = MediaRequestData.Kind.Content,

                ),

                contentDescription = sticker.name,

                contentScale = ContentScale.Crop,

            )

        }

    }

}

