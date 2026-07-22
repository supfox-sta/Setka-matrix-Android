/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalCoroutinesApi::class)

package io.element.android.features.securebackup.impl.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.freeletics.flowredux.compose.StateAndDispatch
import com.freeletics.flowredux.compose.rememberStateAndDispatch
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.securebackup.impl.loggerTagSetup
import io.element.android.features.securebackup.impl.setup.views.RecoveryKeyUserStory
import io.element.android.features.securebackup.impl.setup.views.RecoveryKeyViewState
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.encryption.BackupState
import io.element.android.libraries.matrix.api.encryption.EnableRecoveryProgress
import io.element.android.libraries.matrix.api.encryption.EncryptionService
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

@AssistedInject
class SecureBackupSetupPresenter(
    @Assisted private val isChangeRecoveryKeyUserStory: Boolean,
    private val stateMachine: SecureBackupSetupStateMachine,
    private val encryptionService: EncryptionService,
) : Presenter<SecureBackupSetupState> {
    @AssistedFactory
    interface Factory {
        fun create(isChangeRecoveryKeyUserStory: Boolean): SecureBackupSetupPresenter
    }

    @Composable
    override fun present(): SecureBackupSetupState {
        val coroutineScope = rememberCoroutineScope()
        val stateAndDispatch = stateMachine.rememberStateAndDispatch()
        val setupState by remember {
            derivedStateOf { stateAndDispatch.state.value.toSetupState() }
        }
        var showSaveConfirmationDialog by remember { mutableStateOf(false) }

        fun handleEvent(event: SecureBackupSetupEvents) {
            when (event) {
                SecureBackupSetupEvents.CreateRecoveryKey -> {
                    coroutineScope.createOrChangeRecoveryKey(stateAndDispatch)
                }
                SecureBackupSetupEvents.RecoveryKeyHasBeenSaved ->
                    stateAndDispatch.dispatchAction(SecureBackupSetupStateMachine.Event.UserSavedKey)
                SecureBackupSetupEvents.DismissDialog -> {
                    showSaveConfirmationDialog = false
                    stateAndDispatch.dispatchAction(SecureBackupSetupStateMachine.Event.ClearError)
                }
                SecureBackupSetupEvents.Done -> {
                    showSaveConfirmationDialog = true
                }
            }
        }

        val recoveryKeyViewState = RecoveryKeyViewState(
            recoveryKeyUserStory = if (isChangeRecoveryKeyUserStory) RecoveryKeyUserStory.Change else RecoveryKeyUserStory.Setup,
            formattedRecoveryKey = setupState.recoveryKey(),
            displayTextFieldContents = true,
            inProgress = setupState is SetupState.Creating,
        )

        return SecureBackupSetupState(
            isChangeRecoveryKeyUserStory = isChangeRecoveryKeyUserStory,
            recoveryKeyViewState = recoveryKeyViewState,
            setupState = setupState,
            showSaveConfirmationDialog = showSaveConfirmationDialog,
            eventSink = ::handleEvent,
        )
    }

    private fun SecureBackupSetupStateMachine.State?.toSetupState(): SetupState {
        return when (this) {
            null,
            SecureBackupSetupStateMachine.State.Initial -> SetupState.Init
            SecureBackupSetupStateMachine.State.CreatingKey -> SetupState.Creating
            is SecureBackupSetupStateMachine.State.KeyCreated -> SetupState.Created(formattedRecoveryKey = key)
            is SecureBackupSetupStateMachine.State.KeyCreatedAndSaved -> SetupState.CreatedAndSaved(formattedRecoveryKey = key)
            is SecureBackupSetupStateMachine.State.Error -> SetupState.Error(exception)
        }
    }

    private fun CoroutineScope.createOrChangeRecoveryKey(
        stateAndDispatch: StateAndDispatch<SecureBackupSetupStateMachine.State, SecureBackupSetupStateMachine.Event>
    ) = launch {
        stateAndDispatch.dispatchAction(SecureBackupSetupStateMachine.Event.UserCreatesKey)
        if (isChangeRecoveryKeyUserStory) {
            try {
                awaitEncryptionReady()
                ensureKeyStorageEnabled()
                Timber.tag(loggerTagSetup.value).d("Calling encryptionService.resetRecoveryKey()")
                val recoveryKey = encryptionService.resetRecoveryKey().getOrThrow()
                stateAndDispatch.dispatchAction(SecureBackupSetupStateMachine.Event.SdkHasCreatedKey(recoveryKey))
            } catch (exception: Exception) {
                Timber.tag(loggerTagSetup.value).e(exception, "Failed to reset recovery key")
                stateAndDispatch.dispatchAction(SecureBackupSetupStateMachine.Event.SdkError(exception))
            }
        } else {
            try {
                awaitEncryptionReady()
                ensureKeyStorageEnabled()
                val recoveryKey = withTimeout(120.seconds) {
                    val progressDeferred = async {
                        encryptionService.enableRecoveryProgressStateFlow
                            .filterIsInstance<EnableRecoveryProgress.Done>()
                            .first()
                            .recoveryKey
                    }
                    Timber.tag(loggerTagSetup.value).d("Calling encryptionService.enableRecovery()")
                    encryptionService.enableRecovery(waitForBackupsToUpload = false).getOrThrow()
                    progressDeferred.await()
                }
                stateAndDispatch.dispatchAction(
                    SecureBackupSetupStateMachine.Event.SdkHasCreatedKey(recoveryKey)
                )
            } catch (exception: Exception) {
                Timber.tag(loggerTagSetup.value).e(exception, "Failed to enable recovery")
                stateAndDispatch.dispatchAction(SecureBackupSetupStateMachine.Event.SdkError(exception))
            }
        }
    }

    private suspend fun awaitEncryptionReady() {
        withTimeout(30.seconds) {
            encryptionService.backupStateStateFlow.first { it != BackupState.WAITING_FOR_SYNC }
            encryptionService.recoveryStateStateFlow.first { it != RecoveryState.WAITING_FOR_SYNC }
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
        Timber.tag(loggerTagSetup.value).d("Calling encryptionService.enableBackups()")
        encryptionService.enableBackups().getOrThrow()
        withTimeout(30.seconds) {
            encryptionService.backupStateStateFlow.first {
                it == BackupState.ENABLED || it == BackupState.UNKNOWN
            }
        }
    }
}
