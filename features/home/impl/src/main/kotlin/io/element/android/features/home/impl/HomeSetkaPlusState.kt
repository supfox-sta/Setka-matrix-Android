/*
 * Copyright (c) 2026 Element Creations Ltd.
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl

data class HomeSetkaPlusState(
    val isLoading: Boolean = false,
    val subscription: HomeSetkaPlusSubscription? = null,
    val plans: List<HomeSetkaPlusPlan> = emptyList(),
    val checkoutEnabled: Boolean = true,
    val vouchersEnabled: Boolean = true,
    val voucherCode: String = "",
    val voucherFooterText: String = "",
    val voucherNotice: String? = null,
    val voucherNoticeIsSuccess: Boolean = false,
    val isRedeemingVoucher: Boolean = false,
    val busyPlanId: String? = null,
    val errorMessage: String? = null,
    val paymentFlowState: io.element.android.libraries.matrix.api.setka.SetkaPlusPaymentFlowState =
        io.element.android.libraries.matrix.api.setka.SetkaPlusPaymentFlowState.Idle,
)

data class HomeSetkaPlusSubscription(
    val tier: String,
    val planName: String?,
    val status: String,
    val isActive: Boolean,
    val expiresAt: Long,
)

data class HomeSetkaPlusPlan(
    val id: String,
    val name: String,
    val priceRub: Double,
    val durationDays: Int,
    val features: List<String>,
    val active: Boolean,
    val sortOrder: Int,
)
