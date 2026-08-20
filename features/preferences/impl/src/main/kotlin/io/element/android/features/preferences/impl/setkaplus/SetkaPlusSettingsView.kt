/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.preferences.impl.setkaplus

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.designsystem.components.ClickableLinkText
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.matrix.api.setka.formatSetkaPlusStatus
import io.element.android.libraries.matrix.ui.setka.SetkaPlusPaymentFlowBanner
import kotlinx.coroutines.delay

@Composable
fun SetkaPlusSettingsView(
    state: SetkaPlusSettingsState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val voucherBanner = state.voucherNotice?.let { VoucherBanner(it, state.voucherNoticeIsSuccess) }

    LaunchedEffect(state.voucherNotice, state.voucherNoticeIsSuccess) {
        if (state.voucherNotice == null) return@LaunchedEffect
        delay(if (state.voucherNoticeIsSuccess) 3500 else 5000)
        state.eventSink(SetkaPlusSettingsEvent.ClearVoucherNotice)
    }

    Box(modifier = modifier.fillMaxSize()) {
        PreferencePage(
            onBackClick = onBackClick,
            title = stringResource(R.string.screen_preferences_setka_plus_title),
        ) {
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (state.checkoutEnabled) {
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

            if (state.vouchersEnabled) {
                Spacer(modifier = Modifier.height(if (state.checkoutEnabled) 16.dp else 4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ElementTheme.colors.bgSubtleSecondary, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.screen_preferences_setka_plus_voucher_title),
                        style = ElementTheme.typography.fontBodyLgMedium,
                    )
                    if (!state.checkoutEnabled) {
                        Text(
                            text = "${stringResource(R.string.screen_preferences_setka_plus_status_title)}: ${formatSetkaPlusStatus(state.subscription?.status)}",
                            style = ElementTheme.typography.fontBodySmRegular,
                            color = ElementTheme.colors.textSecondary,
                        )
                    }
                    OutlinedTextField(
                        value = state.voucherCode,
                        onValueChange = { state.eventSink(SetkaPlusSettingsEvent.VoucherCodeChanged(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.screen_preferences_setka_plus_voucher_hint)) },
                        singleLine = true,
                        enabled = !state.isRedeemingVoucher,
                    )
                    val footerText = state.voucherFooterText.ifBlank {
                        stringResource(R.string.screen_preferences_setka_plus_voucher_description)
                    }
                    ClickableLinkText(
                        text = footerText,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        style = ElementTheme.typography.fontBodySmRegular,
                        color = ElementTheme.colors.textSecondary,
                    )
                    FilledTonalButton(
                        onClick = { state.eventSink(SetkaPlusSettingsEvent.RedeemVoucher) },
                        enabled = !state.isRedeemingVoucher && state.voucherCode.isNotBlank(),
                    ) {
                        Text(
                            if (state.isRedeemingVoucher) {
                                stringResource(R.string.screen_preferences_setka_plus_processing)
                            } else {
                                stringResource(R.string.screen_preferences_setka_plus_voucher_redeem)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (state.checkoutEnabled) {
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
            } else if (!state.vouchersEnabled) {
                Text(
                    text = stringResource(R.string.screen_preferences_setka_plus_checkout_disabled),
                    color = ElementTheme.colors.textSecondary,
                )
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

        AnimatedVisibility(
            visible = voucherBanner != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 72.dp, start = 16.dp, end = 16.dp),
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            voucherBanner?.let { banner ->
                VoucherResultBanner(
                    message = banner.message,
                    isSuccess = banner.isSuccess,
                )
            }
        }
    }
}

private data class VoucherBanner(
    val message: String,
    val isSuccess: Boolean,
)

@Composable
private fun VoucherResultBanner(
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
