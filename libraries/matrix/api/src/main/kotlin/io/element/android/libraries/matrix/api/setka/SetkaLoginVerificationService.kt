/*

 * Copyright (c) 2026 Setka Matrix

 *

 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.

 */



package io.element.android.libraries.matrix.api.setka



interface SetkaLoginVerificationService {
    suspend fun getStatus(): Result<SetkaLoginVerificationStatus>
    suspend fun verifyCode(code: String): Result<SetkaLoginVerificationResult>
    suspend fun sendCodeToEmail(email: String? = null): Result<SetkaLoginVerificationEmailResult>
    suspend fun completeFallback(
        method: SetkaLoginVerificationFallbackMethod,
        recoveryKey: String? = null,
    ): Result<SetkaLoginVerificationResult>
    suspend fun storeRecoveryKey(recoveryKey: String): Result<SetkaLoginRecoveryStoreResult>
    suspend fun fetchRecoveryKey(): Result<SetkaLoginRecoveryBackupResult>
    suspend fun deleteRecoveryKey(): Result<SetkaLoginRecoveryDeleteResult>
}


