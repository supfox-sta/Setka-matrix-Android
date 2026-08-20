/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.ui.setka

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.theme.components.Text
import kotlinx.coroutines.delay

@Composable
fun SetkaPlusVoucherResultBannerHost(
    message: String?,
    isSuccess: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(message, isSuccess) {
        if (message == null) return@LaunchedEffect
        delay(if (isSuccess) 3500 else 5000)
        onDismiss()
    }

    AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        message?.let { text ->
            SetkaPlusVoucherResultBanner(
                message = text,
                isSuccess = isSuccess,
            )
        }
    }
}

@Composable
fun SetkaPlusVoucherResultBanner(
    message: String,
    isSuccess: Boolean,
    modifier: Modifier = Modifier,
) {
    val background = if (isSuccess) {
        ElementTheme.colors.bgSuccessSubtle
    } else {
        ElementTheme.colors.bgCriticalSubtle
    }
    val foreground = if (isSuccess) {
        ElementTheme.colors.textSuccessPrimary
    } else {
        ElementTheme.colors.textCriticalPrimary
    }
    Text(
        text = message,
        style = ElementTheme.typography.fontBodyMdMedium,
        color = foreground,
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(12.dp))
            .background(background, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}
