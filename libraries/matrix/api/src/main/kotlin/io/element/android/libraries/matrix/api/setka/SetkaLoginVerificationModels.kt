/*

 * Copyright (c) 2026 Setka Matrix

 *

 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.

 */



package io.element.android.libraries.matrix.api.setka



data class SetkaLoginVerificationStatus(
    val required: Boolean,
    val verified: Boolean = false,
    val ip: String = "",
    val region: String = "",
    val expiresAt: Long = 0,
    val emailAvailable: Boolean = false,
    val emailHint: String = "",
    val recoveryBackupAvailable: Boolean = false,
)

data class SetkaLoginVerificationEmailResult(
    val sent: Boolean,
    val email: String = "",
)



enum class SetkaLoginVerificationFallbackMethod {

    EMOJI,

    RECOVERY_KEY,

}



data class SetkaLoginVerificationResult(
    val verified: Boolean,
    val method: String,
    val recoveryBackupAvailable: Boolean = false,
)

data class SetkaLoginRecoveryBackupResult(
    val available: Boolean,
    val recoveryKey: String = "",
)

data class SetkaLoginRecoveryStoreResult(
    val stored: Boolean,
)

data class SetkaLoginRecoveryDeleteResult(
    val deleted: Boolean,
)

