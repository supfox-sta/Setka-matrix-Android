/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.impl.setka

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.setka.SetkaPendingRecoveryKeyStore

@ContributesBinding(SessionScope::class)
@SingleIn(SessionScope::class)
class DefaultSetkaPendingRecoveryKeyStore : SetkaPendingRecoveryKeyStore {
    @Volatile
    private var recoveryKey: String? = null

    override fun set(recoveryKey: String) {
        this.recoveryKey = recoveryKey.trim().takeIf { it.isNotEmpty() }
    }

    override fun consume(): String? {
        val key = recoveryKey
        recoveryKey = null
        return key
    }
}
