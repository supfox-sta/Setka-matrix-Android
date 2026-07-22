/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.test.setka

import io.element.android.libraries.matrix.api.setka.SetkaLoginRecoveryBackupResult
import io.element.android.libraries.matrix.api.setka.SetkaLoginRecoveryDeleteResult
import io.element.android.libraries.matrix.api.setka.SetkaLoginRecoveryStoreResult
import io.element.android.libraries.matrix.api.setka.SetkaLoginVerificationEmailResult
import io.element.android.libraries.matrix.api.setka.SetkaLoginVerificationFallbackMethod
import io.element.android.libraries.matrix.api.setka.SetkaLoginVerificationResult
import io.element.android.libraries.matrix.api.setka.SetkaLoginVerificationService
import io.element.android.libraries.matrix.api.setka.SetkaLoginVerificationStatus

class FakeSetkaLoginVerificationService(
    private val recoveryBackupAvailable: Boolean = true,
    private val recoveryKey: String = "test-recovery-key",
) : SetkaLoginVerificationService {
    override suspend fun getStatus(): Result<SetkaLoginVerificationStatus> {
        return Result.success(
            SetkaLoginVerificationStatus(
                required = false,
                verified = true,
                recoveryBackupAvailable = recoveryBackupAvailable,
            )
        )
    }

    override suspend fun verifyCode(code: String): Result<SetkaLoginVerificationResult> {
        return Result.success(SetkaLoginVerificationResult(verified = true, method = "code"))
    }

    override suspend fun sendCodeToEmail(email: String?): Result<SetkaLoginVerificationEmailResult> {
        return Result.success(SetkaLoginVerificationEmailResult(sent = true))
    }

    override suspend fun completeFallback(
        method: SetkaLoginVerificationFallbackMethod,
        recoveryKey: String?,
    ): Result<SetkaLoginVerificationResult> {
        return Result.success(SetkaLoginVerificationResult(verified = true, method = "emoji"))
    }

    override suspend fun storeRecoveryKey(recoveryKey: String): Result<SetkaLoginRecoveryStoreResult> {
        return Result.success(SetkaLoginRecoveryStoreResult(stored = true))
    }

    override suspend fun fetchRecoveryKey(): Result<SetkaLoginRecoveryBackupResult> {
        return Result.success(
            SetkaLoginRecoveryBackupResult(
                available = recoveryBackupAvailable,
                recoveryKey = recoveryKey,
            )
        )
    }

    override suspend fun deleteRecoveryKey(): Result<SetkaLoginRecoveryDeleteResult> {
        return Result.success(SetkaLoginRecoveryDeleteResult(deleted = true))
    }
}
