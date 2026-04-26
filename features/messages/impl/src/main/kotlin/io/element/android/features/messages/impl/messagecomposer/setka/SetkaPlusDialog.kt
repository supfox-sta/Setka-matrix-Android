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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.R
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Surface
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetkaPlusDialog(
    state: SetkaComposerState,
    onDismiss: () -> Unit,
    onBuyPlan: (SetkaPlusPlan) -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = modifier.fillMaxSize(),
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
                    item(key = "setka-plus-promo") {
                        SetkaPlusPromoCard()
                    }

                    state.errorMessage?.let { errorMessage ->
                        item {
                            Text(
                                text = errorMessage,
                                style = ElementTheme.typography.fontBodyMdRegular,
                                color = ElementTheme.colors.textCriticalPrimary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ElementTheme.colors.bgCriticalSubtle, MaterialTheme.shapes.medium)
                                    .padding(12.dp),
                            )
                        }
                    }

                    item {
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
                                text = state.subscription?.status ?: stringResource(R.string.screen_room_setka_subscription_inactive),
                                style = ElementTheme.typography.fontBodyMdMedium,
                            )
                            Text(
                                text = state.subscription?.planName ?: stringResource(R.string.screen_room_setka_subscription_default_plan),
                                style = ElementTheme.typography.fontBodyMdRegular,
                                color = ElementTheme.colors.textSecondary,
                            )
                        }
                    }

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
                }
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
