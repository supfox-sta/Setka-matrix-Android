/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.customreaction

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.emojibasebindings.Emoji
import io.element.android.features.messages.impl.R
import io.element.android.features.messages.impl.messagecomposer.setka.SetkaStickerPack
import io.element.android.features.messages.impl.timeline.components.customreaction.picker.EmojiPicker
import io.element.android.features.messages.impl.timeline.components.customreaction.picker.EmojiPickerPresenter
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.hide
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.api.timeline.item.event.EventOrTransactionId
import io.element.android.libraries.matrix.ui.media.MediaRequestData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomReactionBottomSheet(
    state: CustomReactionState,
    customEmojiPacks: List<SetkaStickerPack>,
    onSelectEmoji: (EventOrTransactionId, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()
    val coroutineScope = rememberCoroutineScope()
    val target = state.target as? CustomReactionState.Target.Success

    fun onDismiss() {
        state.eventSink(CustomReactionEvent.DismissCustomReactionSheet)
    }

    fun onEmojiSelectedDismiss(emoji: Emoji) {
        if (target?.event == null) return
        sheetState.hide(coroutineScope) {
            state.eventSink(CustomReactionEvent.DismissCustomReactionSheet)
            onSelectEmoji(target.event.eventOrTransactionId, emoji.unicode)
        }
    }

    fun onCustomEmojiSelectedDismiss(mxcUrl: String) {
        if (target?.event == null) return
        sheetState.hide(coroutineScope) {
            state.eventSink(CustomReactionEvent.DismissCustomReactionSheet)
            onSelectEmoji(target.event.eventOrTransactionId, mxcUrl)
        }
    }

    if (target?.emojibaseStore != null && target.event.eventId != null) {
        ModalBottomSheet(
            onDismissRequest = ::onDismiss,
            sheetState = sheetState,
            modifier = modifier,
        ) {
            val presenter = remember {
                EmojiPickerPresenter(
                    emojibaseStore = target.emojibaseStore,
                    recentEmojis = state.recentEmojis,
                    coroutineDispatchers = CoroutineDispatchers.Default,
                )
            }
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CustomEmojiSection(
                    packs = customEmojiPacks,
                    selectedKeys = state.selectedEmoji,
                    onSelectCustomEmoji = ::onCustomEmojiSelectedDismiss,
                )
                EmojiPicker(
                    onSelectEmoji = ::onEmojiSelectedDismiss,
                    state = presenter.present(),
                    selectedEmojis = state.selectedEmoji,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun CustomEmojiSection(
    packs: List<SetkaStickerPack>,
    selectedKeys: Set<String>,
    onSelectCustomEmoji: (String) -> Unit,
) {
    if (packs.isEmpty()) return

    var selectedPackId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(packs) {
        if (packs.none { it.id == selectedPackId }) {
            selectedPackId = packs.firstOrNull()?.id
        }
    }
    val selectedPack = packs.firstOrNull { it.id == selectedPackId } ?: packs.firstOrNull() ?: return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.screen_room_setka_emoji_section_title),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            packs.forEach { pack ->
                FilterChip(
                    selected = selectedPack.id == pack.id,
                    onClick = { selectedPackId = pack.id },
                    label = { Text(pack.name) },
                )
            }
        }
        LazyVerticalGrid(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 168.dp)
                .padding(horizontal = 16.dp),
            columns = GridCells.Adaptive(minSize = 52.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(selectedPack.stickers, key = { it.id }) { sticker ->
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .background(
                            if (selectedKeys.contains(sticker.mxcUrl)) {
                                ElementTheme.colors.bgSubtlePrimary
                            } else {
                                ElementTheme.colors.bgSubtleSecondary
                            }
                        )
                        .clickable { onSelectCustomEmoji(sticker.mxcUrl) }
                        .padding(6.dp)
                ) {
                    AsyncImage(
                        model = MediaRequestData(MediaSource(sticker.mxcUrl), MediaRequestData.Kind.Content),
                        contentDescription = sticker.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(MaterialTheme.shapes.medium),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
}
