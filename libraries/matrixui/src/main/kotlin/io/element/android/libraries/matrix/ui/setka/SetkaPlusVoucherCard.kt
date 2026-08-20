/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.ui.setka

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.components.ClickableLinkText
import io.element.android.libraries.designsystem.theme.components.Text

@Composable
fun SetkaPlusVoucherCard(
    title: String,
    voucherCode: String,
    onVoucherCodeChange: (String) -> Unit,
    hintText: String,
    footerText: String,
    defaultFooterText: String,
    redeemButtonText: String,
    processingText: String,
    isRedeeming: Boolean,
    onRedeem: () -> Unit,
    modifier: Modifier = Modifier,
    subscriptionStatusLine: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ElementTheme.colors.bgSubtleSecondary, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = ElementTheme.typography.fontBodyLgMedium,
        )
        subscriptionStatusLine?.let { line ->
            Text(
                text = line,
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
        OutlinedTextField(
            value = voucherCode,
            onValueChange = onVoucherCodeChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(hintText) },
            singleLine = true,
            enabled = !isRedeeming,
        )
        ClickableLinkText(
            text = footerText.ifBlank { defaultFooterText },
            interactionSource = remember { MutableInteractionSource() },
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textSecondary,
        )
        FilledTonalButton(
            onClick = onRedeem,
            enabled = !isRedeeming && voucherCode.isNotBlank(),
        ) {
            Text(if (isRedeeming) processingText else redeemButtonText)
        }
    }
}
