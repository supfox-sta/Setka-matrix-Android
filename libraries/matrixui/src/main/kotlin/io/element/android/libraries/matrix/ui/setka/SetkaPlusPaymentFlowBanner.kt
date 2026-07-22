/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.ui.setka

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.matrix.api.setka.SetkaPlusPaymentFlowState
import io.element.android.libraries.matrix.api.setka.formatSetkaPlusPaymentFlowMessage
import io.element.android.libraries.matrix.api.setka.isSetkaPlusPaymentFlowError
import io.element.android.libraries.matrix.api.setka.isSetkaPlusPaymentFlowSuccess

@Composable
fun SetkaPlusPaymentFlowBanner(
    paymentFlowState: SetkaPlusPaymentFlowState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = formatSetkaPlusPaymentFlowMessage(paymentFlowState) ?: return
    val backgroundColor = when {
        isSetkaPlusPaymentFlowSuccess(paymentFlowState) -> ElementTheme.colors.bgSuccessSubtle
        isSetkaPlusPaymentFlowError(paymentFlowState) -> ElementTheme.colors.bgCriticalSubtle
        else -> ElementTheme.colors.bgSubtleSecondary
    }
    val textColor = when {
        isSetkaPlusPaymentFlowSuccess(paymentFlowState) -> ElementTheme.colors.textSuccessPrimary
        isSetkaPlusPaymentFlowError(paymentFlowState) -> ElementTheme.colors.textCriticalPrimary
        else -> ElementTheme.colors.textPrimary
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = message,
            style = ElementTheme.typography.fontBodyMdMedium,
            color = textColor,
        )
        if (paymentFlowState is SetkaPlusPaymentFlowState.Confirming) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (paymentFlowState !is SetkaPlusPaymentFlowState.Confirming) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(text = "Закрыть", onClick = onDismiss)
            }
        }
    }
}
