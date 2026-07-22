/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.api.setka

interface SetkaPendingRecoveryKeyStore {
    fun set(recoveryKey: String)
    fun consume(): String?
}
