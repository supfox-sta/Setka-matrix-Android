/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.home.impl

import io.element.android.libraries.matrix.api.setka.SetkaPlusCatalog
import io.element.android.libraries.matrix.api.setka.SetkaPlusCheckoutConfig
import io.element.android.libraries.matrix.api.setka.SetkaPlusPayment
import io.element.android.libraries.matrix.api.setka.SetkaPlusPaymentCoordinator
import io.element.android.libraries.matrix.api.setka.SetkaPlusPaymentFlowState
import io.element.android.libraries.matrix.api.setka.SetkaPlusPaymentRequest
import io.element.android.libraries.matrix.api.setka.SetkaPlusPaymentReturnOutcome
import io.element.android.libraries.matrix.api.setka.SetkaPlusPlan
import io.element.android.libraries.matrix.api.setka.SetkaPlusService
import io.element.android.libraries.matrix.api.setka.SetkaPlusSubscription
import io.element.android.libraries.matrix.api.setka.SetkaPlusVoucherRedemption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeSetkaPlusService : SetkaPlusService {
    override suspend fun getSubscription(): Result<SetkaPlusSubscription?> = Result.success(null)

    override suspend fun getPlans(): Result<List<SetkaPlusPlan>> = getPlansCatalog().map { it.plans }

    override suspend fun getPlansCatalog(): Result<SetkaPlusCatalog> =
        Result.success(
            SetkaPlusCatalog(
                plans = emptyList(),
                checkoutConfig = SetkaPlusCheckoutConfig(),
            )
        )

    override suspend fun getCheckoutConfig(): Result<SetkaPlusCheckoutConfig> =
        Result.success(SetkaPlusCheckoutConfig())

    override suspend fun redeemVoucher(code: String): Result<SetkaPlusVoucherRedemption> =
        Result.failure(IllegalStateException("Not implemented"))

    override suspend fun createPayment(plan: SetkaPlusPlan): Result<SetkaPlusPaymentRequest> =
        Result.failure(IllegalStateException("Not implemented"))

    override suspend fun getPaymentStatus(paymentId: String): Result<SetkaPlusPayment> =
        Result.failure(IllegalStateException("Not implemented"))
}

class FakeSetkaPlusPaymentCoordinator(
    initialState: SetkaPlusPaymentFlowState = SetkaPlusPaymentFlowState.Idle,
) : SetkaPlusPaymentCoordinator {
    private val _flowState = MutableStateFlow(initialState)
    override val flowState: StateFlow<SetkaPlusPaymentFlowState> = _flowState

    override fun openCheckout(paymentId: String, checkoutUrl: String?) = Unit

    override fun beginPayment(paymentId: String) = Unit

    override fun onBrowserReturn(paymentId: String, outcome: SetkaPlusPaymentReturnOutcome) = Unit

    override fun dismissResult() {
        _flowState.value = SetkaPlusPaymentFlowState.Idle
    }
}
