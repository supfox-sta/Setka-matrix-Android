/*
 * Copyright (c) 2026 Element Creations Ltd.
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.setka

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.R
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Surface
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.ui.media.MediaRequestData
import io.element.android.libraries.ui.strings.CommonStrings

private val stockEmojiList = listOf(
    "\uD83D\uDE02", "\uD83D\uDE2D", "\uD83E\uDD23", "\uD83D\uDE0D",
    "\uD83D\uDE0A", "\uD83E\uDD14", "\uD83D\uDE21", "\uD83D\uDE31",
    "\uD83D\uDC4D", "\uD83D\uDC4E", "\uD83D\uDC4F", "\uD83D\uDE4F",
    "\uD83D\uDD25", "\u2764\uFE0F", "\uD83D\uDCAF", "\u2728",
    "\uD83C\uDF89", "\uD83E\uDD1D", "\uD83D\uDC40", "\uD83D\uDE4C",
    "\uD83D\uDC4C", "\uD83E\uDD2F", "\uD83D\uDE34", "\uD83E\uDD73",
    "\uD83D\uDE07", "\uD83E\uDD16", "\uD83D\uDC31", "\uD83D\uDC36",
    "\uD83C\uDF08", "\u2B50", "\uD83C\uDF55", "\u2615",
)

private const val STOCK_EMOJI_PACK_ID = "__setka_stock_emoji__"

@Composable
fun SetkaInlinePickerPanel(
    state: SetkaComposerState,
    onDismiss: () -> Unit,
    onSendSticker: (SetkaSticker) -> Unit,
    onInsertEmoji: (String) -> Unit,
    onOpenPackEditor: (SetkaPackKind, String?) -> Unit,
    onOpenSubscription: () -> Unit,
    onSharePack: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedKind by rememberSaveable { mutableStateOf(SetkaPackKind.STICKER) }
    val availableKinds = listOf(SetkaPackKind.STICKER, SetkaPackKind.EMOJI)
    val selectedKindIndex = availableKinds.indexOf(selectedKind).coerceAtLeast(0)
    var contentSwipePx by remember { mutableFloatStateOf(0f) }
    val contentSwipeThresholdPx = with(LocalDensity.current) { 64.dp.toPx() }
    val packs = when (selectedKind) {
        SetkaPackKind.STICKER -> state.stickerPacksOnly
        SetkaPackKind.EMOJI -> state.emojiPacksOnly
    }
    val stockEmojiPack = SetkaStickerPack(
        id = STOCK_EMOJI_PACK_ID,
        name = stringResource(R.string.screen_room_setka_stock_emoji_section_title),
        kind = SetkaPackKind.EMOJI,
        stickers = emptyList(),
        createdAt = null,
        updatedAt = null,
    )
    val headerPacks = when (selectedKind) {
        SetkaPackKind.STICKER -> packs
        SetkaPackKind.EMOJI -> listOf(stockEmojiPack) + packs
    }
    var selectedPackId by rememberSaveable(selectedKind) { mutableStateOf<String?>(null) }
    var heldStickerPreview by remember { mutableStateOf<HeldStickerPreview?>(null) }
    val contentScrollState = rememberScrollState()
    val compactIcons = contentScrollState.value > 18
    val packIconSize by animateDpAsState(
        targetValue = if (compactIcons) 36.dp else 54.dp,
        animationSpec = tween(durationMillis = 160),
        label = "setkaPackIconSize",
    )
    val headerPadding by animateDpAsState(
        targetValue = if (compactIcons) 4.dp else 8.dp,
        animationSpec = tween(durationMillis = 160),
        label = "setkaHeaderPadding",
    )

    LaunchedEffect(headerPacks) {
        if (headerPacks.none { it.id == selectedPackId }) {
            selectedPackId = headerPacks.firstOrNull()?.id
        }
    }

    val selectedPack = headerPacks.firstOrNull { it.id == selectedPackId } ?: headerPacks.firstOrNull()
    val isStockEmojiPackSelected = selectedPack?.id == STOCK_EMOJI_PACK_ID

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = ElementTheme.colors.bgCanvasDefault,
            shadowElevation = 10.dp,
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage,
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = headerPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    headerPacks.forEach { pack ->
                        PackHeaderIcon(
                            pack = pack,
                            selected = selectedPack?.id == pack.id,
                            size = packIconSize,
                            onClick = { selectedPackId = pack.id },
                        )
                    }
                }
                IconButton(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(ElementTheme.colors.bgSubtlePrimary),
                    onClick = { onOpenPackEditor(selectedKind, null) },
                    enabled = selectedKind == SetkaPackKind.STICKER,
                ) {
                    Icon(
                        imageVector = CompoundIcons.Plus(),
                        contentDescription = stringResource(R.string.screen_room_setka_new_pack),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth()
                    .pointerInput(selectedKind, availableKinds) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmount ->
                                contentSwipePx += dragAmount
                                change.consume()
                            },
                            onDragEnd = {
                                when {
                                    contentSwipePx > contentSwipeThresholdPx -> {
                                        val prev = (selectedKindIndex - 1).coerceAtLeast(0)
                                        selectedKind = availableKinds[prev]
                                    }
                                    contentSwipePx < -contentSwipeThresholdPx -> {
                                        val next = (selectedKindIndex + 1).coerceAtMost(availableKinds.lastIndex)
                                        selectedKind = availableKinds[next]
                                    }
                                }
                                contentSwipePx = 0f
                            },
                            onDragCancel = { contentSwipePx = 0f },
                        )
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(contentScrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (selectedPack != null && !isStockEmojiPackSelected && selectedKind == SetkaPackKind.STICKER) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = selectedPack.name,
                                style = ElementTheme.typography.fontBodyLgMedium,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedButton(
                                onClick = { onOpenPackEditor(selectedKind, selectedPack.id) },
                            ) {
                                Text(stringResource(CommonStrings.action_edit))
                            }
                        }
                    } else if (selectedPack != null && !isStockEmojiPackSelected && selectedKind == SetkaPackKind.EMOJI) {
                        Text(
                            text = selectedPack.name,
                            style = ElementTheme.typography.fontBodyLgMedium,
                        )
                    }

                    if (!state.isPlusActive && selectedKind == SetkaPackKind.EMOJI) {
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

                    if (selectedKind == SetkaPackKind.EMOJI && isStockEmojiPackSelected) {
                        Text(
                            text = stringResource(R.string.screen_room_setka_stock_emoji_section_title),
                            style = ElementTheme.typography.fontBodyMdMedium,
                        )
                        EmojiUnicodeGrid(
                            emojis = stockEmojiList,
                            onSelect = { emoji -> onInsertEmoji("$emoji ") },
                        )
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
                    } else if (!isStockEmojiPackSelected) {
                        if (selectedKind == SetkaPackKind.EMOJI) {
                            Text(
                                text = stringResource(R.string.screen_room_setka_emoji_section_title),
                                style = ElementTheme.typography.fontBodyMdMedium,
                            )
                            HorizontalDivider()
                        }
                        FlowStickerGrid(
                            modifier = Modifier.fillMaxWidth(),
                            stickers = selectedPack.stickers,
                            enableStickerLongPress = selectedKind == SetkaPackKind.STICKER,
                            onStickerClick = { sticker ->
                                if (selectedKind == SetkaPackKind.STICKER) {
                                    if (state.sendingStickerId == null) {
                                        onSendSticker(sticker)
                                    }
                                } else {
                                    if (!state.isPlusActive) {
                                        onOpenSubscription()
                                    } else {
                                        onInsertEmoji("${sticker.inlineEmojiToken()} ")
                                    }
                                }
                            },
                            onStickerLongPressChanged = { sticker, showPreview, showMenu ->
                                heldStickerPreview = if (sticker == null) {
                                    null
                                } else {
                                    HeldStickerPreview(
                                        sticker = sticker,
                                        packId = selectedPack.id,
                                        showPreview = showPreview,
                                        showMenu = showMenu,
                                    )
                                }
                            },
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }

            SetkaKindBottomSwitcher(
                kinds = availableKinds,
                selectedKind = selectedKind,
                onSelect = { selectedKind = it },
            )
        }
        }

        heldStickerPreview?.let { preview ->
            if (preview.showPreview) {
                StickerHoldOverlay(
                    preview = preview,
                    onDismiss = { heldStickerPreview = null },
                    onSend = {
                        if (state.sendingStickerId == null) {
                            onSendSticker(preview.sticker)
                        }
                        heldStickerPreview = null
                    },
                    onSharePack = {
                        onSharePack(preview.packId)
                        heldStickerPreview = null
                    },
                )
            }
        }
    }
}

private data class HeldStickerPreview(
    val sticker: SetkaSticker,
    val packId: String,
    val showPreview: Boolean,
    val showMenu: Boolean,
)

@Composable
private fun StickerHoldOverlay(
    preview: HeldStickerPreview,
    onDismiss: () -> Unit,
    onSend: () -> Unit,
    onSharePack: () -> Unit,
) {
    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AsyncImage(
                    model = MediaRequestData(MediaSource(preview.sticker.mxcUrl), MediaRequestData.Kind.Content),
                    contentDescription = preview.sticker.name,
                    modifier = Modifier
                        .size(180.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    contentScale = ContentScale.Fit,
                )
                if (preview.showMenu) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ElementTheme.colors.bgCanvasDefault,
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                text = stringResource(R.string.screen_room_setka_sticker_hold_send),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onSend)
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                style = ElementTheme.typography.fontBodyLgMedium,
                                textAlign = TextAlign.Center,
                            )
                            HorizontalDivider()
                            Text(
                                text = stringResource(R.string.screen_room_setka_sticker_hold_share_pack),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onSharePack)
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                style = ElementTheme.typography.fontBodyLgMedium,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PackHeaderIcon(
    pack: SetkaStickerPack,
    selected: Boolean,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(if (selected) 18.dp else 16.dp)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(
                if (selected) ElementTheme.colors.bgSubtlePrimary else ElementTheme.colors.bgSubtleSecondary
            )
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) ElementTheme.colors.borderFocused else Color.Transparent,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        PackPreview(
            pack = pack,
            size = size - 12.dp,
        )
    }
}

@Composable
private fun PackPreview(
    pack: SetkaStickerPack,
    size: androidx.compose.ui.unit.Dp = 42.dp,
) {
    val previewSticker = pack.stickers.firstOrNull()
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape((size.value / 3f).dp))
            .background(ElementTheme.colors.bgCanvasDefault),
        contentAlignment = Alignment.Center,
    ) {
        if (previewSticker != null) {
            AsyncImage(
                model = MediaRequestData(MediaSource(previewSticker.mxcUrl), MediaRequestData.Kind.Content),
                contentDescription = pack.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = if (pack.kind == SetkaPackKind.STICKER) {
                    CompoundIcons.Sticker()
                } else {
                    CompoundIcons.ReactionAdd()
                },
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun EmojiUnicodeGrid(
    emojis: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        emojis.forEach { emoji ->
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ElementTheme.colors.bgSubtleSecondary)
                    .clickable { onSelect(emoji) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = emoji,
                    style = ElementTheme.typography.fontHeadingMdBold,
                )
            }
        }
    }
}

@Composable
private fun FlowStickerGrid(
    stickers: List<SetkaSticker>,
    onStickerClick: (SetkaSticker) -> Unit,
    enableStickerLongPress: Boolean = false,
    onStickerLongPressChanged: (
        sticker: SetkaSticker?,
        showPreview: Boolean,
        showMenu: Boolean,
    ) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        stickers.forEach { sticker ->
            StickerOrEmojiTile(
                sticker = sticker,
                modifier = Modifier.width(74.dp),
                enableLongPress = enableStickerLongPress,
                onClick = { onStickerClick(sticker) },
                onLongPressChanged = { showPreview, showMenu ->
                    if (!showPreview && !showMenu) {
                        onStickerLongPressChanged(null, false, false)
                    } else {
                        onStickerLongPressChanged(sticker, showPreview, showMenu)
                    }
                },
            )
        }
    }
}

@Composable
private fun StickerOrEmojiTile(
    sticker: SetkaSticker,
    onClick: () -> Unit,
    enableLongPress: Boolean = false,
    onLongPressChanged: (showPreview: Boolean, showMenu: Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(ElementTheme.colors.bgSubtleSecondary)
            .then(
                if (enableLongPress) {
                    Modifier.pointerInput(sticker.id) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            val start = System.currentTimeMillis()
                            var previewShown = false
                            var menuShown = false
                            var isTap = true
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.none { it.pressed }) {
                                    if (isTap && System.currentTimeMillis() - start < 400L) {
                                        onClick()
                                    }
                                    onLongPressChanged(false, false)
                                    break
                                }
                                val elapsed = System.currentTimeMillis() - start
                                if (elapsed >= 400L) {
                                    isTap = false
                                }
                                if (!previewShown && elapsed >= 4_000L) {
                                    previewShown = true
                                    onLongPressChanged(true, false)
                                }
                                if (!menuShown && elapsed >= 6_000L) {
                                    menuShown = true
                                    onLongPressChanged(true, true)
                                }
                            }
                        }
                    }
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            )
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
    }
}

@Composable
private fun SetkaKindBottomSwitcher(
    kinds: List<SetkaPackKind>,
    selectedKind: SetkaPackKind,
    onSelect: (SetkaPackKind) -> Unit,
) {
    val switchWidth = 180.dp
    val knobWidth = (switchWidth - 8.dp) / kinds.size
    val selectedIndex = kinds.indexOf(selectedKind).coerceAtLeast(0)
    var dragPx by remember { mutableFloatStateOf(0f) }
    val thresholdPx = with(LocalDensity.current) { 42.dp.toPx() }
    val knobOffset by animateDpAsState(
        targetValue = knobWidth * selectedIndex,
        animationSpec = tween(durationMillis = 180),
        label = "setkaKindKnobOffset",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(switchWidth)
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(ElementTheme.colors.bgSubtleSecondary)
                .padding(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .offset(x = knobOffset)
                    .requiredWidth(knobWidth)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(ElementTheme.colors.bgCanvasDefault),
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(kinds, selectedKind) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmount ->
                                dragPx += dragAmount
                                change.consume()
                            },
                            onDragEnd = {
                                when {
                                    dragPx > thresholdPx -> {
                                        val prev = (selectedIndex - 1).coerceAtLeast(0)
                                        onSelect(kinds[prev])
                                    }
                                    dragPx < -thresholdPx -> {
                                        val next = (selectedIndex + 1).coerceAtMost(kinds.lastIndex)
                                        onSelect(kinds[next])
                                    }
                                }
                                dragPx = 0f
                            },
                            onDragCancel = { dragPx = 0f },
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                kinds.forEach { kind ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .clickable { onSelect(kind) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(
                                if (kind == SetkaPackKind.STICKER) {
                                    R.string.screen_room_setka_stickers_tab
                                } else {
                                    R.string.screen_room_setka_emoji_tab
                                }
                            ),
                            style = ElementTheme.typography.fontBodyMdMedium,
                            color = if (selectedKind == kind) {
                                ElementTheme.colors.textPrimary
                            } else {
                                ElementTheme.colors.textSecondary
                            },
                        )
                    }
                }
            }
        }
    }
}
