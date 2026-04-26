/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.legacygallery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.R
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.permissions.api.PermissionsView
import io.element.android.libraries.ui.strings.CommonStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegacyGalleryPickerView(
    state: LegacyGalleryPickerState,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = { state.eventSink(LegacyGalleryPickerEvent.Dismiss) })

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ElementTheme.colors.bgCanvasDefault,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                titleStr = stringResource(R.string.screen_room_legacy_gallery_picker_title),
                navigationIcon = {
                    BackButton(onClick = { state.eventSink(LegacyGalleryPickerEvent.Dismiss) })
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LegacyGalleryFilterRow(
                selectedFilter = state.selectedFilter,
                onSelect = { state.eventSink(LegacyGalleryPickerEvent.SelectFilter(it)) },
            )

            when {
                !state.permissionState.permissionGranted -> {
                    PermissionRequiredContent(
                        onRequestPermissions = {
                            state.eventSink(LegacyGalleryPickerEvent.RequestPermissions)
                        },
                    )
                }
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.mediaItems.isEmpty() -> {
                    EmptyContent()
                }
                else -> {
                    val selectedMediaIds = state.selectedMediaIds.toList()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding(),
                    ) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 112.dp),
                            contentPadding = PaddingValues(
                                start = 12.dp,
                                end = 12.dp,
                                top = 8.dp,
                                bottom = if (state.selectedCount > 0) 100.dp else 24.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(
                                items = state.mediaItems,
                                key = { item -> item.id },
                            ) { item ->
                                LegacyGalleryItemView(
                                    item = item,
                                    isSelected = selectedMediaIds.contains(item.id),
                                    selectionIndex = selectedMediaIds.indexOf(item.id).takeIf { it >= 0 }?.plus(1),
                                    onClick = { state.eventSink(LegacyGalleryPickerEvent.ToggleMediaSelection(item)) },
                                )
                            }
                        }

                        if (state.selectedCount > 0) {
                            SelectionBar(
                                state = state,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    PermissionsView(
        state = state.permissionState,
        content = stringResource(R.string.screen_room_legacy_gallery_picker_permission_message),
    )
}

@Composable
private fun SelectionBar(
    state: LegacyGalleryPickerState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(ElementTheme.colors.bgCanvasDefault)
            .border(
                border = BorderStroke(1.dp, ElementTheme.colors.borderInteractiveSecondary),
                shape = RoundedCornerShape(24.dp),
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (state.allowMultiSelection) {
                        stringResource(
                            R.string.screen_room_legacy_gallery_picker_selected_count,
                            state.selectedCount,
                        )
                    } else {
                        stringResource(R.string.screen_room_legacy_gallery_picker_one_selected)
                    },
                    style = ElementTheme.typography.fontBodyMdMedium,
                )
                Text(
                    text = stringResource(R.string.screen_room_legacy_gallery_picker_continue_hint),
                    style = ElementTheme.typography.fontBodySmRegular,
                    color = ElementTheme.colors.textSecondary,
                )
            }
            Button(
                text = stringResource(CommonStrings.action_continue),
                onClick = { state.eventSink(LegacyGalleryPickerEvent.ConfirmSelection) },
            )
        }
    }
}

@Composable
private fun LegacyGalleryFilterRow(
    selectedFilter: LegacyGalleryFilter,
    onSelect: (LegacyGalleryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LegacyGalleryFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selectedFilter,
                onClick = { onSelect(filter) },
                label = {
                    Text(
                        when (filter) {
                            LegacyGalleryFilter.All -> stringResource(R.string.screen_room_legacy_gallery_picker_filter_all)
                            LegacyGalleryFilter.Photos -> stringResource(R.string.screen_room_legacy_gallery_picker_filter_photos)
                            LegacyGalleryFilter.Videos -> stringResource(R.string.screen_room_legacy_gallery_picker_filter_videos)
                        }
                    )
                },
            )
        }
    }
}

@Composable
private fun PermissionRequiredContent(
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.screen_room_legacy_gallery_picker_permission_message),
            textAlign = TextAlign.Center,
            color = ElementTheme.colors.textSecondary,
        )
        Button(
            text = stringResource(R.string.screen_room_legacy_gallery_picker_permission_action),
            onClick = onRequestPermissions,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun EmptyContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.screen_room_legacy_gallery_picker_empty),
            color = ElementTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

@Composable
private fun LegacyGalleryItemView(
    item: LegacyGalleryItem,
    isSelected: Boolean,
    selectionIndex: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(ElementTheme.colors.bgSubtleSecondary)
            .border(
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 0.dp,
                    color = if (isSelected) {
                        ElementTheme.colors.borderInteractivePrimary
                    } else {
                        ElementTheme.colors.bgSubtleSecondary
                    },
                ),
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize(),
        )

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ElementTheme.colors.bgActionPrimaryRest.copy(alpha = 0.18f))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(26.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(ElementTheme.colors.bgActionPrimaryRest),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = selectionIndex?.toString().orEmpty(),
                    color = ElementTheme.colors.textOnSolidPrimary,
                    style = ElementTheme.typography.fontBodySmMedium,
                )
            }
        }

        if (item.isVideo) {
            Text(
                text = stringResource(R.string.screen_room_legacy_gallery_picker_video_badge),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(ElementTheme.colors.bgCanvasDefault.copy(alpha = 0.8f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                color = ElementTheme.colors.textPrimary,
            )
        } else {
            Icon(
                imageVector = CompoundIcons.Image(),
                contentDescription = null,
                tint = ElementTheme.colors.textPrimary.copy(alpha = 0.84f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(ElementTheme.colors.bgCanvasDefault.copy(alpha = 0.74f))
                    .padding(4.dp)
                    .size(14.dp),
            )
        }
    }
}
