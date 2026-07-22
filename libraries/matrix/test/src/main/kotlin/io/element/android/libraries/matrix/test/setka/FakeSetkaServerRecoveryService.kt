/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.test.setka

import io.element.android.libraries.matrix.api.setka.SetkaLoginRecoveryEvaluation
import io.element.android.libraries.matrix.api.setka.SetkaServerRecoveryService
import io.element.android.libraries.matrix.api.setka.SetkaServerRecoverySyncState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeSetkaServerRecoveryService(
    override val isSetkaSession: Boolean = false,
    initialSyncState: SetkaServerRecoverySyncState = SetkaServerRecoverySyncState.Unknown,
    private val loginRecoveryEvaluation: SetkaLoginRecoveryEvaluation =
        SetkaLoginRecoveryEvaluation.NeedsVerification,
) : SetkaServerRecoveryService {
    override val syncState: StateFlow<SetkaServerRecoverySyncState> = MutableStateFlow(initialSyncState)

    override suspend fun evaluateLoginRecovery(): SetkaLoginRecoveryEvaluation = loginRecoveryEvaluation

    override suspend fun publishFreshRecoveryKeyAfterLogin(): Result<Unit> = Result.success(Unit)

    override suspend fun syncWhenReady() = Unit

    override suspend fun refreshAndSync(): Result<Unit> = Result.success(Unit)

    override suspend fun uploadKeysToServer(): Result<Unit> = Result.success(Unit)

    override suspend fun uploadRecoveryKeyIfVerified(recoveryKey: String) = Unit

    override suspend fun disableServerBackup(): Result<Unit> = Result.success(Unit)
}
