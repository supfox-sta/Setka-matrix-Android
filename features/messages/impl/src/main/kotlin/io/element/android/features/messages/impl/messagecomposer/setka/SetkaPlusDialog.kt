/*
 * Copyright (c) 2026 Element Creations Ltd.
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.setka

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.R
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Surface
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.setka.formatSetkaPlusStatus
import io.element.android.libraries.matrix.ui.setka.SetkaPlusPaymentFlowBanner
import io.element.android.libraries.matrix.ui.setka.SetkaPlusVoucherCard
import io.element.android.libraries.matrix.ui.setka.SetkaPlusVoucherResultBannerHost
import io.element.android.libraries.ui.strings.CommonStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetkaPlusDialog(
    state: SetkaComposerState,
    onDismiss: () -> Unit,
    onBuyPlan: (SetkaPlusPlan) -> Unit,
    onDismissPaymentResult: () -> Unit,
    onVoucherCodeChange: (String) -> Unit,
    onRedeemVoucher: () -> Unit,
    onClearVoucherNotice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = ElementTheme.colors.bgCanvasDefault,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.screen_room_setka_plus_title),
                            style = ElementTheme.typography.fontHeadingMdBold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = CompoundIcons.Close(),
                                contentDescription = stringResource(CommonStrings.action_close),
                            )
                        }
                    }

                    if (state.isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(10.dp))
                    } else {
                        Spacer(Modifier.height(2.dp))
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (state.checkoutEnabled) {
                            item(key = "setka-plus-promo") {
                                SetkaPlusPromoCard()
                            }
                        }

                        if (state.paymentFlowState !is io.element.android.libraries.matrix.api.setka.SetkaPlusPaymentFlowState.Idle) {
                            item(key = "setka-plus-payment-flow") {
                                SetkaPlusPaymentFlowBanner(
                                    paymentFlowState = state.paymentFlowState,
                                    onDismiss = onDismissPaymentResult,
                                )
                            }
                        }

                        if (state.checkoutEnabled) {
                            item(key = "setka-plus-status") {
                                SetkaPlusStatusCard(
                                    status = state.subscription?.status,
                                    planName = state.subscription?.planName,
                                )
                            }
                        }

                        if (state.vouchersEnabled) {
                            item(key = "setka-plus-voucher") {
                                SetkaPlusVoucherCard(
                                    title = stringResource(R.string.screen_room_setka_voucher_title),
                                    voucherCode = state.voucherCode,
                                    onVoucherCodeChange = onVoucherCodeChange,
                                    hintText = stringResource(R.string.screen_room_setka_voucher_hint),
                                    footerText = state.voucherFooterText,
                                    defaultFooterText = stringResource(R.string.screen_room_setka_voucher_description),
                                    redeemButtonText = stringResource(R.string.screen_room_setka_voucher_redeem),
                                    processingText = stringResource(R.string.screen_room_setka_processing),
                                    isRedeeming = state.isRedeemingVoucher,
                                    onRedeem = onRedeemVoucher,
                                    subscriptionStatusLine = if (!state.checkoutEnabled) {
                                        "${stringResource(R.string.screen_room_setka_subscription_status)}: ${formatSetkaPlusStatus(state.subscription?.status)}"
                                    } else {
                                        null
                                    },
                                )
                            }
                        }

                        if (state.checkoutEnabled) {
                            if (state.plans.isEmpty()) {
                                item {
                                    Text(
                                        text = stringResource(R.string.screen_room_setka_no_plans),
                                        style = ElementTheme.typography.fontBodyMdRegular,
                                        color = ElementTheme.colors.textSecondary,
                                    )
                                }
                            }

                            items(state.plans, key = { it.id }) { plan ->
                                SetkaPlusPlanCard(
                                    plan = plan,
                                    state = state,
                                    onBuyPlan = onBuyPlan,
                                )
                            }
                        }
                    }
                }
            }

            SetkaPlusVoucherResultBannerHost(
                message = state.voucherNotice,
                isSuccess = state.voucherNoticeIsSuccess,
                onDismiss = onClearVoucherNotice,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 72.dp, start = 16.dp, end = 16.dp),
            )
        }
    }
}

@Composable
private fun SetkaPlusStatusCard(
    status: String?,
    planName: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ElementTheme.colors.bgSubtleSecondary, MaterialTheme.shapes.large)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.screen_room_setka_subscription_status),
            style = ElementTheme.typography.fontBodyLgMedium,
        )
        Text(
            text = status ?: stringResource(R.string.screen_room_setka_subscription_inactive),
            style = ElementTheme.typography.fontBodyMdMedium,
        )
        Text(
            text = planName ?: stringResource(R.string.screen_room_setka_subscription_default_plan),
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun SetkaPlusPlanCard(
    plan: SetkaPlusPlan,
    state: SetkaComposerState,
    onBuyPlan: (SetkaPlusPlan) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ElementTheme.colors.bgSubtleSecondary, MaterialTheme.shapes.large)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = plan.name,
            style = ElementTheme.typography.fontBodyLgMedium,
        )
        Text(
            text = stringResource(
                R.string.screen_room_setka_plan_price,
                plan.priceRub,
                plan.durationDays,
            ),
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
        )
        plan.features.filter { it.isNotBlank() }.forEach { feature ->
            Text(
                text = "- $feature",
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
        val isCurrentPlan = state.subscription?.isActive == true &&
            (state.subscription.tier == plan.id || state.subscription.planName == plan.name)
        if (isCurrentPlan) {
            OutlinedButton(onClick = {}, enabled = false) {
                Text(stringResource(R.string.screen_room_setka_plan_current))
            }
        } else {
            FilledTonalButton(
                onClick = { onBuyPlan(plan) },
                enabled = state.busyPlanId == null && state.subscription?.isActive != true,
            ) {
                Text(
                    text = if (state.busyPlanId == plan.id) {
                        stringResource(R.string.screen_room_setka_processing)
                    } else {
                        stringResource(R.string.screen_room_setka_buy_plan)
                    }
                )
            }
        }
    }
}

@Composable
private fun SetkaPlusPromoCard(
    modifier: Modifier = Modifier,
) {
    val gradient = Brush.linearGradient(
        colors = listOf(
            ElementTheme.colors.bgSubtlePrimary,
            ElementTheme.colors.bgSubtleSecondary,
        )
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(gradient)
            .border(1.dp, ElementTheme.colors.borderDisabled, RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.screen_room_setka_plus_promo_title),
            style = ElementTheme.typography.fontHeadingMdBold,
        )
        Text(
            text = stringResource(R.string.screen_room_setka_plus_promo_subtitle),
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "- " + stringResource(R.string.screen_room_setka_plus_promo_feature_1),
                style = ElementTheme.typography.fontBodySmRegular,
            )
            Text(
                text = "- " + stringResource(R.string.screen_room_setka_plus_promo_feature_2),
                style = ElementTheme.typography.fontBodySmRegular,
            )
            Text(
                text = "- " + stringResource(R.string.screen_room_setka_plus_promo_feature_3),
                style = ElementTheme.typography.fontBodySmRegular,
            )
        }
    }
}
