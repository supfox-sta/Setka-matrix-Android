/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.preferences.impl.storage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.preferences.impl.R
import io.element.android.features.preferences.impl.tasks.StorageCategory
import io.element.android.features.preferences.impl.tasks.StorageCategoryBreakdown
import io.element.android.libraries.androidutils.filesize.FileSizeFormatter
import io.element.android.libraries.designsystem.components.ProgressDialog
import io.element.android.libraries.designsystem.components.preferences.PreferenceCategory
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.utils.formatter.rememberFileSizeFormatter

@Composable
fun StorageSettingsView(
    state: StorageSettingsState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.showLoader) {
        ProgressDialog()
    }

    val fileSizeFormatter = rememberFileSizeFormatter()

    PreferencePage(
        modifier = modifier,
        onBackClick = onBackClick,
        title = stringResource(R.string.screen_storage_settings_title),
    ) {
        StorageChartSection(
            totalSizeLabel = state.totalSizeLabel,
            categories = state.categories,
            fileSizeFormatter = fileSizeFormatter,
        )
        Button(
            text = stringResource(R.string.screen_storage_settings_clear_cache),
            onClick = { state.eventSink(StorageSettingsEvents.ClearCache) },
            showProgress = state.clearCacheAction.isLoading(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (state.rooms.isNotEmpty()) {
            PreferenceCategory(title = stringResource(R.string.screen_storage_settings_chats_title)) {
                state.rooms.forEach { room ->
                    ListItem(
                        headlineContent = { Text(room.name) },
                        supportingContent = {
                            Text(
                                text = fileSizeFormatter.format(room.sizeBytes),
                                style = ElementTheme.typography.fontBodySmRegular,
                                color = ElementTheme.colors.textSecondary,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageChartSection(
    totalSizeLabel: String,
    categories: List<StorageCategoryBreakdown>,
    fileSizeFormatter: FileSizeFormatter,
) {
    val colors = listOf(
        Color(0xFF5B8DEF),
        Color(0xFF7B61FF),
        Color(0xFF34C759),
        Color(0xFFFF9500),
        Color(0xFFFF3B30),
        Color(0xFF8E8E93),
    )
    val total = categories.sumOf { it.sizeBytes }.coerceAtLeast(1L)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(180.dp)) {
                if (categories.isNotEmpty()) {
                    val stroke = Stroke(width = 28.dp.toPx())
                    val diameter = size.minDimension - stroke.width
                    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                    val arcSize = Size(diameter, diameter)
                    var startAngle = -90f
                    categories.forEachIndexed { index, item ->
                        val sweep = 360f * (item.sizeBytes.toFloat() / total.toFloat())
                        drawArc(
                            color = colors[index % colors.size],
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = stroke,
                        )
                        startAngle += sweep
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = totalSizeLabel.ifBlank { fileSizeFormatter.format(0L) },
                    style = ElementTheme.typography.fontBodyLgMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.screen_storage_settings_total),
                    style = ElementTheme.typography.fontBodySmRegular,
                    color = ElementTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }

        categories.forEachIndexed { index, item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Canvas(modifier = Modifier.size(12.dp)) {
                        drawCircle(color = colors[index % colors.size])
                    }
                    Text(
                        text = categoryLabel(item.category),
                        style = ElementTheme.typography.fontBodyMdRegular,
                    )
                }
                Text(
                    text = fileSizeFormatter.format(item.sizeBytes),
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun categoryLabel(category: StorageCategory): String {
    return stringResource(
        when (category) {
            StorageCategory.PHOTOS -> R.string.screen_storage_settings_category_photos
            StorageCategory.VIDEOS -> R.string.screen_storage_settings_category_videos
            StorageCategory.VOICE -> R.string.screen_storage_settings_category_voice
            StorageCategory.FILES -> R.string.screen_storage_settings_category_files
            StorageCategory.DATABASE -> R.string.screen_storage_settings_category_database
            StorageCategory.OTHER -> R.string.screen_storage_settings_category_other
        }
    )
}
