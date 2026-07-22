/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.timeline.item.event.LocalEventSendState
import io.element.android.libraries.ui.utils.formatter.rememberFileSizeFormatter

@Composable
fun MediaUploadProgressOverlay(
    localSendState: LocalEventSendState?,
    showProgressLabel: Boolean,
    modifier: Modifier = Modifier,
) {
    if (localSendState !is LocalEventSendState.Sending) return

    val fileSizeFormatter = rememberFileSizeFormatter()
    val progressState = localSendState as? LocalEventSendState.Sending.MediaWithProgress

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (showProgressLabel && progressState != null && progressState.total > 0L) {
            val uploaded = fileSizeFormatter.format(progressState.progress.coerceAtLeast(0L))
            val total = fileSizeFormatter.format(progressState.total)
            Text(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .semantics { hideFromAccessibility() },
                text = "$uploaded / $total",
                style = ElementTheme.typography.fontBodyXsRegular,
                color = Color.White,
            )
        }
        CircularProgressIndicator(
            modifier = Modifier.semantics { hideFromAccessibility() },
            color = Color.White,
            strokeWidth = 3.dp,
        )
    }
}
