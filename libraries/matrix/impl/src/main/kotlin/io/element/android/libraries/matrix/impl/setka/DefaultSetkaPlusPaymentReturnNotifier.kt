/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.impl.setka

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.matrix.api.setka.SetkaPlusPaymentReturn
import io.element.android.libraries.matrix.api.setka.SetkaPlusPaymentReturnNotifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DefaultSetkaPlusPaymentReturnNotifier : SetkaPlusPaymentReturnNotifier {
    private val mutableReturns = MutableSharedFlow<SetkaPlusPaymentReturn>(extraBufferCapacity = 1)

    override val returns: Flow<SetkaPlusPaymentReturn> = mutableReturns.asSharedFlow()

    override fun notify(returnData: SetkaPlusPaymentReturn) {
        mutableReturns.tryEmit(returnData)
    }
}
