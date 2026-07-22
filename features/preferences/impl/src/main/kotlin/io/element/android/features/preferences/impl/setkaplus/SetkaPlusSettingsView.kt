/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.preferences.impl.setkaplus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.matrix.api.setka.formatSetkaPlusStatus
import io.element.android.libraries.matrix.ui.setka.SetkaPlusPaymentFlowBanner
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton

@Composable
fun SetkaPlusSettingsView(
    state: SetkaPlusSettingsState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePage(
        modifier = modifier,
        onBackClick = onBackClick,
        title = stringResource(R.string.screen_preferences_setka_plus_title),
    ) {
        if (state.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))
        }

        Text(
            text = stringResource(R.string.screen_preferences_setka_plus_description),
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ElementTheme.colors.bgSubtleSecondary, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.screen_preferences_setka_plus_status_title),
                style = ElementTheme.typography.fontBodyLgMedium,
            )
            Text(
                text = formatSetkaPlusStatus(state.subscription?.status),
                style = ElementTheme.typography.fontBodyMdMedium,
            )
            Text(
                text = state.subscription?.planName ?: stringResource(R.string.screen_preferences_setka_plus_title),
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }

        if (state.paymentFlowState !is io.element.android.libraries.matrix.api.setka.SetkaPlusPaymentFlowState.Idle) {
            Spacer(modifier = Modifier.height(12.dp))
            SetkaPlusPaymentFlowBanner(
                paymentFlowState = state.paymentFlowState,
                onDismiss = { state.eventSink(SetkaPlusSettingsEvent.DismissPaymentResult) },
            )
        }

        state.errorMessage?.let { errorMessage ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = errorMessage,
                color = ElementTheme.colors.textCriticalPrimary,
                style = ElementTheme.typography.fontBodyMdRegular,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (state.plans.isEmpty() && !state.isLoading) {
            Text(
                text = stringResource(R.string.screen_preferences_setka_plus_no_plans),
                color = ElementTheme.colors.textSecondary,
            )
        }

        state.plans.filter { it.active }.forEach { plan ->
            val isOwned = state.subscription?.isActive == true && state.subscription.tier == plan.id
            val hasActiveSubscription = state.subscription?.isActive == true
            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ElementTheme.colors.bgSubtleSecondary, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(plan.name, style = ElementTheme.typography.fontBodyLgMedium)
                Text(
                    text = stringResource(
                        R.string.screen_preferences_setka_plus_plan_price,
                        plan.priceRub,
                        plan.durationDays,
                    ),
                    color = ElementTheme.colors.textSecondary,
                )
                plan.features.forEach { feature ->
                    Text(
                        text = "- $feature",
                        color = ElementTheme.colors.textSecondary,
                        style = ElementTheme.typography.fontBodySmRegular,
                    )
                }
                if (isOwned) {
                    OutlinedButton(onClick = {}, enabled = false) {
                        Text(stringResource(R.string.screen_preferences_setka_plus_plan_current))
                    }
                } else {
                    FilledTonalButton(
                        onClick = { state.eventSink(SetkaPlusSettingsEvent.BuyPlan(plan.id)) },
                        enabled = !hasActiveSubscription && state.busyPlanId == null,
                    ) {
                        Text(
                            if (state.busyPlanId == plan.id) {
                                stringResource(R.string.screen_preferences_setka_plus_processing)
                            } else {
                                stringResource(R.string.screen_preferences_setka_plus_buy)
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                text = stringResource(R.string.screen_preferences_setka_plus_refresh),
                onClick = { state.eventSink(SetkaPlusSettingsEvent.Refresh) },
            )
        }
    }
}
