/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.api.setka

import kotlinx.coroutines.flow.StateFlow

interface SetkaPlusPaymentCoordinator {
    val flowState: StateFlow<SetkaPlusPaymentFlowState>

    /** Opens checkout in browser (Custom Tab) and starts status polling. */
    fun openCheckout(paymentId: String, checkoutUrl: String?)

    /** Called after checkout URL was opened; polls server until ResultURL confirms payment. */
    fun beginPayment(paymentId: String)

    /** Called when user returns from browser (Success/Fail URL deep link). */
    fun onBrowserReturn(paymentId: String, outcome: SetkaPlusPaymentReturnOutcome)

    fun dismissResult()
}
