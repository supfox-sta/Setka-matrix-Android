/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.x.update

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File

data class AppUpdatePromptState(
    val updateInfo: AppUpdateInfo,
    val installedVersionName: String,
    val isDownloading: Boolean = false,
    val downloadedApkFile: File? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val downloadError: String? = null,
) {
    val isReadyToInstall: Boolean
        get() = downloadedApkFile?.exists() == true

    val downloadProgressFraction: Float?
        get() {
            val safeTotalBytes = totalBytes?.takeIf { it > 0L } ?: return null
            return (downloadedBytes.toFloat() / safeTotalBytes.toFloat()).coerceIn(0f, 1f)
        }

    val downloadProgressPercent: Int?
        get() = downloadProgressFraction?.let { (it * 100).toInt() }
}

@Composable
fun AppUpdatePrompt(
    state: AppUpdatePromptState,
    onUpdateClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isMandatory = state.updateInfo.manifest.isMandatoryUpdate()
    val remoteVersion = state.updateInfo.manifest.versionName
        ?.takeIf { it.isNotBlank() }
        ?: state.updateInfo.manifest.versionCode?.toString()
        ?: "-"

    BackHandler(enabled = isMandatory) {
        // Mandatory update blocks dismiss via back gesture.
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.66f)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = state.updateInfo.manifest.title
                            ?.takeIf { it.isNotBlank() }
                            ?: "\u0414\u043e\u0441\u0442\u0443\u043f\u043d\u043e \u043e\u0431\u043d\u043e\u0432\u043b\u0435\u043d\u0438\u0435",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (isMandatory) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                        ) {
                            Text(
                                text = "\u041e\u0431\u044f\u0437\u0430\u0442\u0435\u043b\u044c\u043d\u043e\u0435 \u043e\u0431\u043d\u043e\u0432\u043b\u0435\u043d\u0438\u0435",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = state.updateInfo.manifest.message
                            ?.takeIf { it.isNotBlank() }
                            ?: "\u0414\u043e\u0441\u0442\u0443\u043f\u043d\u0430 \u043d\u043e\u0432\u0430\u044f \u0432\u0435\u0440\u0441\u0438\u044f \u043f\u0440\u0438\u043b\u043e\u0436\u0435\u043d\u0438\u044f.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "\u0423\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d\u043e: ${state.installedVersionName}\n\u0414\u043e\u0441\u0442\u0443\u043f\u043d\u043e: $remoteVersion",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.updateInfo.manifest.changelog
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { changelog ->
                            Text(
                                text = changelog,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    state.downloadError
                        ?.takeIf { it.isNotBlank() }
                        ?.let { error ->
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    if (state.isDownloading) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.downloadProgressFraction?.let { progress ->
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } ?: LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = state.downloadProgressPercent
                                    ?.let { "\u0421\u043a\u0430\u0447\u0438\u0432\u0430\u043d\u0438\u0435... $it%" }
                                    ?: "\u0421\u043a\u0430\u0447\u0438\u0432\u0430\u043d\u0438\u0435 \u043e\u0431\u043d\u043e\u0432\u043b\u0435\u043d\u0438\u044f...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else if (state.isReadyToInstall) {
                        Text(
                            text = "\u0424\u0430\u0439\u043b \u043e\u0431\u043d\u043e\u0432\u043b\u0435\u043d\u0438\u044f \u0433\u043e\u0442\u043e\u0432 \u043a \u0443\u0441\u0442\u0430\u043d\u043e\u0432\u043a\u0435.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!isMandatory) {
                        TextButton(
                            onClick = onDismissClick,
                            enabled = !state.isDownloading,
                        ) {
                            Text("\u041f\u043e\u0437\u0436\u0435")
                        }
                    }
                    Button(
                        onClick = onUpdateClick,
                        enabled = !state.isDownloading,
                    ) {
                        if (state.isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Text(
                                text = "\u0421\u043a\u0430\u0447\u0438\u0432\u0430\u043d\u0438\u0435...",
                                modifier = Modifier.padding(start = 10.dp),
                            )
                        } else if (state.isReadyToInstall) {
                            Text("\u0423\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u044c")
                        } else {
                            Text("\u0421\u043a\u0430\u0447\u0430\u0442\u044c")
                        }
                    }
                }
            }
        }
    }
}
