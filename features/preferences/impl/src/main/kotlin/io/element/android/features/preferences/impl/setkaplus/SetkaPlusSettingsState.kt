/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.preferences.impl.setkaplus

data class SetkaPlusSettingsState(
    val isLoading: Boolean = false,
    val subscription: SetkaPlusSubscriptionUi? = null,
    val plans: List<SetkaPlusPlanUi> = emptyList(),
    val checkoutEnabled: Boolean = true,
    val vouchersEnabled: Boolean = true,
    val voucherCode: String = "",
    val voucherFooterText: String = "",
    val voucherNotice: String? = null,
    val voucherNoticeIsSuccess: Boolean = false,
    val isRedeemingVoucher: Boolean = false,
    val busyPlanId: String? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val paymentFlowState: io.element.android.libraries.matrix.api.setka.SetkaPlusPaymentFlowState =
        io.element.android.libraries.matrix.api.setka.SetkaPlusPaymentFlowState.Idle,
    val eventSink: (SetkaPlusSettingsEvent) -> Unit,
)

data class SetkaPlusSubscriptionUi(
    val tier: String,
    val planName: String?,
    val status: String,
    val isActive: Boolean,
)

data class SetkaPlusPlanUi(
    val id: String,
    val name: String,
    val priceRub: Double,
    val durationDays: Int,
    val features: List<String>,
    val active: Boolean,
)

sealed interface SetkaPlusSettingsEvent {
    data object Refresh : SetkaPlusSettingsEvent
    data class BuyPlan(val planId: String) : SetkaPlusSettingsEvent
    data class VoucherCodeChanged(val code: String) : SetkaPlusSettingsEvent
    data object RedeemVoucher : SetkaPlusSettingsEvent
    data object ClearVoucherNotice : SetkaPlusSettingsEvent
    data object ClearError : SetkaPlusSettingsEvent
    data object ClearSuccess : SetkaPlusSettingsEvent
    data object DismissPaymentResult : SetkaPlusSettingsEvent
}
