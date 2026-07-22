/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.api.setka

import kotlinx.coroutines.flow.StateFlow

enum class SetkaServerRecoverySyncState {
    Unknown,
    Checking,
    Synced,
    ServerBackupMissing,
    Uploading,
    LoginNotVerified,
    Error,
}

enum class SetkaLoginRecoveryEvaluation {
    /** Session already has local recovery and a server backup; skip emoji and key rotation. */
    ProceedWithoutFreshKey,
    /** Server key restored locally; publish a fresh key after login completes. */
    ProceedWithFreshKey,
    /** Server key missing or rejected; show emoji verification. */
    NeedsVerification,
}

interface SetkaServerRecoveryService {
    val syncState: StateFlow<SetkaServerRecoverySyncState>
    val isSetkaSession: Boolean

    /**
     * Checks whether login can proceed silently and whether a fresh key should be uploaded afterwards.
     */
    suspend fun evaluateLoginRecovery(): SetkaLoginRecoveryEvaluation

    /**
     * Creates a fresh recovery key (same as Encryption → Change key) and stores it on the server.
     * Must run once after login verification completes.
     */
    suspend fun publishFreshRecoveryKeyAfterLogin(): Result<Unit>

    suspend fun syncWhenReady()

    suspend fun refreshAndSync(): Result<Unit>

    suspend fun uploadKeysToServer(): Result<Unit>

    suspend fun uploadRecoveryKeyIfVerified(recoveryKey: String)

    suspend fun disableServerBackup(): Result<Unit>
}
