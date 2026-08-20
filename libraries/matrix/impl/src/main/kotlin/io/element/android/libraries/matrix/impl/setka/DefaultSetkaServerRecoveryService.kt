/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.impl.setka

import android.util.Log
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.appconfig.SetkaMatrixConfig
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.encryption.BackupState
import io.element.android.libraries.matrix.api.encryption.EnableRecoveryProgress
import io.element.android.libraries.matrix.api.encryption.EncryptionService
import io.element.android.libraries.matrix.api.encryption.RecoveryException
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import io.element.android.libraries.matrix.api.setka.SetkaLoginRecoveryEvaluation
import io.element.android.libraries.matrix.api.setka.SetkaLoginVerificationService
import io.element.android.libraries.matrix.api.setka.SetkaPendingRecoveryKeyStore
import io.element.android.libraries.matrix.api.setka.SetkaServerRecoveryService
import io.element.android.libraries.matrix.api.setka.SetkaServerRecoverySyncState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

@ContributesBinding(SessionScope::class)
@SingleIn(SessionScope::class)
class DefaultSetkaServerRecoveryService(
    matrixClient: MatrixClient,
    private val encryptionService: EncryptionService,
    private val setkaLoginVerificationService: SetkaLoginVerificationService,
    private val pendingRecoveryKeyStore: SetkaPendingRecoveryKeyStore,
    @SessionCoroutineScope scope: CoroutineScope,
) : SetkaServerRecoveryService {
    private val syncMutex = Mutex()
    private val _syncState = MutableStateFlow(SetkaServerRecoverySyncState.Unknown)
    override val syncState: StateFlow<SetkaServerRecoverySyncState> = _syncState.asStateFlow()
    override val isSetkaSession = SetkaMatrixConfig.isSetkaHomeserver(
        matrixClient.sessionId.value.substringAfter(':', "")
    )

    init {
        if (isSetkaSession) {
            scope.launch {
                runCatching {
                    awaitEncryptionReady()
                    refreshSyncStateFromLocalAndServer()
                }.onFailure { error ->
                    Timber.w(error, "Failed to refresh Setka recovery state on session start")
                }
            }
        }
    }

    override suspend fun evaluateLoginRecovery(): SetkaLoginRecoveryEvaluation {
        if (!isSetkaSession) return SetkaLoginRecoveryEvaluation.ProceedWithoutFreshKey
        return syncMutex.withLock {
            awaitEncryptionReady()
            log("Evaluating Setka login recovery")

            val status = fetchStatus() ?: return@withLock SetkaLoginRecoveryEvaluation.NeedsVerification
            val recoveryState = encryptionService.recoveryStateStateFlow.value
            val loginVerified = !status.required || status.verified

            if (loginVerified) {
                if (recoveryState == RecoveryState.ENABLED) {
                    _syncState.value = SetkaServerRecoverySyncState.Synced
                    log("Login already verified and local recovery enabled")
                    return@withLock SetkaLoginRecoveryEvaluation.ProceedWithoutFreshKey
                }
                if (status.recoveryBackupAvailable && applyServerRecoveryKeyForLogin()) {
                    return@withLock SetkaLoginRecoveryEvaluation.ProceedWithFreshKey
                }
                _syncState.value = SetkaServerRecoverySyncState.Synced
                log("Login verified on server; skipping verification gate")
                return@withLock SetkaLoginRecoveryEvaluation.ProceedWithoutFreshKey
            }

            if (!status.recoveryBackupAvailable) {
                log("No server recovery backup available for login")
                return@withLock SetkaLoginRecoveryEvaluation.NeedsVerification
            }

            if (applyServerRecoveryKeyForLogin()) {
                SetkaLoginRecoveryEvaluation.ProceedWithFreshKey
            } else {
                SetkaLoginRecoveryEvaluation.NeedsVerification
            }
        }
    }

    override suspend fun publishFreshRecoveryKeyAfterLogin(): Result<Unit> {
        if (!isSetkaSession) return Result.success(Unit)
        return syncMutex.withLock {
            runCatching {
                awaitEncryptionReady()
                if (!isVerifiedOnServer()) {
                    _syncState.value = SetkaServerRecoverySyncState.LoginNotVerified
                    error("Login is not verified on Setka server")
                }
                _syncState.value = SetkaServerRecoverySyncState.Uploading
                log("Publishing fresh Setka recovery key after login")
                publishFreshRecoveryKey()
                markSyncedAfterUpload()
            }
        }
    }

    override suspend fun syncWhenReady() {
        if (!isSetkaSession) return
        runCatching { ensureSessionRecoveryReady(force = false) }
            .onFailure { error ->
                Timber.w(error, "Setka recovery sync failed")
                Log.w(TAG, "Setka recovery sync failed", error)
            }
    }

    override suspend fun refreshAndSync(): Result<Unit> {
        if (!isSetkaSession) return Result.success(Unit)
        return runCatching { ensureSessionRecoveryReady(force = true) }
    }

    override suspend fun uploadKeysToServer(): Result<Unit> {
        if (!isSetkaSession) return Result.success(Unit)
        return syncMutex.withLock {
            runCatching {
                awaitEncryptionReady()
                _syncState.value = SetkaServerRecoverySyncState.Uploading
                log("Re-enabling Setka server backup from settings")
                publishFreshRecoveryKey()
                markSyncedAfterUpload()
            }
        }
    }

    override suspend fun uploadRecoveryKeyIfVerified(recoveryKey: String) {
        if (!isSetkaSession) return
        val trimmed = recoveryKey.trim()
        if (trimmed.isEmpty()) return
        if (!isVerifiedOnServer()) {
            pendingRecoveryKeyStore.set(trimmed)
            return
        }
        storeOnServer(trimmed)
        markSyncedAfterUpload()
    }

    override suspend fun disableServerBackup(): Result<Unit> {
        if (!isSetkaSession) return Result.success(Unit)
        return syncMutex.withLock {
            runCatching {
                _syncState.value = SetkaServerRecoverySyncState.Checking
                setkaLoginVerificationService.deleteRecoveryKey().getOrThrow()
                _syncState.value = SetkaServerRecoverySyncState.ServerBackupMissing
                log("Setka server backup disabled by user")
            }
        }
    }

    private suspend fun ensureSessionRecoveryReady(force: Boolean) {
        syncMutex.withLock {
            awaitEncryptionReady()
            if (!force && isSessionRecoveryReady()) {
                log("Setka session recovery already ready, skipping")
                return
            }

            _syncState.value = SetkaServerRecoverySyncState.Checking
            log("Ensuring Setka session recovery is ready")

            val pendingKey = pendingRecoveryKeyStore.consume()
            if (pendingKey != null) {
                if (!isVerifiedOnServer()) {
                    pendingRecoveryKeyStore.set(pendingKey)
                    _syncState.value = SetkaServerRecoverySyncState.LoginNotVerified
                    return
                }
                storeOnServer(pendingKey)
                markSyncedAfterUpload()
                return
            }

            refreshSyncStateFromLocalAndServer(forceRestore = force)
        }
    }

    private suspend fun refreshSyncStateFromLocalAndServer(forceRestore: Boolean = false) {
        val status = fetchStatus() ?: run {
            _syncState.value = SetkaServerRecoverySyncState.Error
            return
        }

        if (status.required && !status.verified) {
            _syncState.value = SetkaServerRecoverySyncState.LoginNotVerified
            return
        }

        val recoveryState = encryptionService.recoveryStateStateFlow.value
        when {
            recoveryState == RecoveryState.ENABLED && status.recoveryBackupAvailable -> {
                _syncState.value = SetkaServerRecoverySyncState.Synced
                log("Local recovery enabled and server backup exists")
            }
            status.recoveryBackupAvailable && (forceRestore || recoveryState != RecoveryState.ENABLED) -> {
                if (applyServerRecoveryKeyForLogin()) {
                    markSyncedAfterUpload()
                } else {
                    _syncState.value = SetkaServerRecoverySyncState.ServerBackupMissing
                }
            }
            recoveryState == RecoveryState.ENABLED -> {
                _syncState.value = SetkaServerRecoverySyncState.ServerBackupMissing
            }
            else -> {
                publishFreshRecoveryKey()
                markSyncedAfterUpload()
            }
        }
    }

    private suspend fun applyServerRecoveryKeyForLogin(): Boolean {
        runCatching { ensureKeyStorageEnabled() }
            .onFailure { error ->
                Timber.w(error, "Failed to prepare key storage for Setka login recovery")
                Log.w(TAG, "Failed to prepare key storage for Setka login recovery", error)
                return false
            }

        val backup = setkaLoginVerificationService.fetchRecoveryKey().getOrElse { error ->
            Timber.w(error, "Failed to fetch Setka recovery backup from server")
            Log.w(TAG, "Failed to fetch Setka recovery backup from server", error)
            return false
        }
        if (!backup.available) return false

        val recoveryKey = backup.recoveryKey.trim()
        if (recoveryKey.isEmpty()) return false

        val recoverResult = encryptionService.recover(recoveryKey)
        if (recoverResult.isFailure) {
            recoverResult.exceptionOrNull()?.let { error ->
                Timber.w(error, "Server recovery key rejected during login")
                Log.w(TAG, "Server recovery key rejected during login", error)
            }
            return false
        }

        awaitRecoveryStateSettled()
        val success = encryptionService.recoveryStateStateFlow.value == RecoveryState.ENABLED
        log("Setka login recovery restore success=$success")
        return success
    }

    private suspend fun publishFreshRecoveryKey() {
        runCatching { ensureKeyStorageEnabled() }
            .onFailure { error ->
                Timber.w(error, "Failed to prepare key storage before publishing fresh key")
                throw error
            }
        val recoveryKey = when (encryptionService.recoveryStateStateFlow.value) {
            RecoveryState.DISABLED,
            RecoveryState.UNKNOWN -> createRecoveryKeyViaEnableRecovery()
            RecoveryState.INCOMPLETE,
            RecoveryState.ENABLED -> createRecoveryKeyViaReset()
            RecoveryState.WAITING_FOR_SYNC -> {
                awaitRecoveryStateSettled()
                publishFreshRecoveryKey()
                return
            }
            else -> createRecoveryKeyViaEnableRecovery()
        }
        storeOnServer(recoveryKey)
        log("Fresh recovery key uploaded to Setka server")
    }

    private suspend fun isSessionRecoveryReady(): Boolean {
        if (_syncState.value != SetkaServerRecoverySyncState.Synced) return false
        if (encryptionService.recoveryStateStateFlow.value != RecoveryState.ENABLED) return false
        return fetchStatus()?.recoveryBackupAvailable == true
    }

    private suspend fun markSyncedAfterUpload() {
        awaitRecoveryStateSettled()
        updateStateFromServerStatus()
    }

    private suspend fun createRecoveryKeyViaEnableRecovery(): String {
        return withTimeout(RECOVERY_OPERATION_TIMEOUT) {
            val progressDeferred = kotlinx.coroutines.coroutineScope {
                async {
                    encryptionService.enableRecoveryProgressStateFlow
                        .filterIsInstance<EnableRecoveryProgress.Done>()
                        .first()
                        .recoveryKey
                }
            }
            encryptionService.enableRecovery(waitForBackupsToUpload = false).getOrThrow()
            progressDeferred.await().trim()
        }
    }

    private suspend fun createRecoveryKeyViaReset(): String {
        return encryptionService.resetRecoveryKey().getOrThrow().trim()
    }

    private suspend fun storeOnServer(recoveryKey: String) {
        val trimmed = recoveryKey.trim()
        if (trimmed.isEmpty()) return
        setkaLoginVerificationService.storeRecoveryKey(trimmed)
            .onSuccess {
                Timber.d("Setka recovery key stored on server")
                log("Setka recovery key stored on server")
            }
            .getOrThrow()
    }

    private suspend fun updateStateFromServerStatus() {
        val status = fetchStatus()
        val recoveryState = encryptionService.recoveryStateStateFlow.value
        _syncState.value = when {
            status == null -> SetkaServerRecoverySyncState.Error
            status.required && !status.verified -> SetkaServerRecoverySyncState.LoginNotVerified
            status.recoveryBackupAvailable && recoveryState == RecoveryState.ENABLED ->
                SetkaServerRecoverySyncState.Synced
            recoveryState == RecoveryState.INCOMPLETE ||
                (recoveryState == RecoveryState.ENABLED && status.recoveryBackupAvailable.not()) ->
                SetkaServerRecoverySyncState.ServerBackupMissing
            else -> SetkaServerRecoverySyncState.Synced
        }
        log("Setka recovery sync state=${_syncState.value}")
    }

    private suspend fun fetchStatus() =
        setkaLoginVerificationService.getStatus()
            .onFailure { error ->
                Timber.w(error, "Failed to load Setka login status during recovery sync")
                Log.w(TAG, "Failed to load Setka login status", error)
            }
            .getOrNull()

    private suspend fun isVerifiedOnServer(): Boolean {
        val status = fetchStatus() ?: return false
        return !status.required || status.verified
    }

    private suspend fun awaitEncryptionReady() {
        withTimeout(ENCRYPTION_READY_TIMEOUT) {
            encryptionService.backupStateStateFlow.first { it != BackupState.WAITING_FOR_SYNC }
            encryptionService.recoveryStateStateFlow.first { it != RecoveryState.WAITING_FOR_SYNC }
        }
    }

    private suspend fun awaitRecoveryStateSettled() {
        withTimeout(RECOVERY_STATE_TIMEOUT) {
            encryptionService.recoveryStateStateFlow.first { state ->
                state != RecoveryState.WAITING_FOR_SYNC && state != RecoveryState.UNKNOWN
            }
        }
    }

    private suspend fun ensureKeyStorageEnabled() {
        when (encryptionService.backupStateStateFlow.value) {
            BackupState.ENABLED,
            BackupState.ENABLING,
            BackupState.CREATING,
            BackupState.RESUMING,
            BackupState.DOWNLOADING -> return
            else -> Unit
        }
        val enableResult = encryptionService.enableBackups()
        if (enableResult.isFailure) {
            when (enableResult.exceptionOrNull()) {
                is RecoveryException.BackupExistsOnServer -> {
                    log("Matrix key backup already exists on server")
                }
                else -> enableResult.getOrThrow()
            }
        }
        withTimeout(BACKUP_ENABLE_TIMEOUT) {
            encryptionService.backupStateStateFlow.first {
                it == BackupState.ENABLED || it == BackupState.UNKNOWN
            }
        }
    }

    private fun log(message: String) {
        Log.i(TAG, message)
    }

    private companion object {
        const val TAG = "SetkaRecovery"
        val ENCRYPTION_READY_TIMEOUT = 60.seconds
        val RECOVERY_STATE_TIMEOUT = 30.seconds
        val RECOVERY_OPERATION_TIMEOUT = 120.seconds
        val BACKUP_ENABLE_TIMEOUT = 30.seconds
    }
}
