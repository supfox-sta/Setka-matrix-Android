/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.api.setka

import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.coroutines.flow.Flow

data class SetkaPlusPaymentReturn(
    val sessionId: SessionId,
    val paymentId: String,
    val outcome: SetkaPlusPaymentReturnOutcome,
)

interface SetkaPlusPaymentReturnNotifier {
    val returns: Flow<SetkaPlusPaymentReturn>

    fun notify(returnData: SetkaPlusPaymentReturn)
}
