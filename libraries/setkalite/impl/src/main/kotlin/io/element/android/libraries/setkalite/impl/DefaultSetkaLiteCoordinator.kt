/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.setkalite.impl

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.appconfig.SetkaMatrixConfig
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.sync.SyncService
import io.element.android.libraries.preferences.api.store.AppPreferencesStore
import io.element.android.libraries.setkalite.api.SetkaLiteCoordinator
import io.element.android.libraries.setkalite.api.SetkaLiteState
import io.element.android.libraries.setkalite.api.SetkaLiteSyncPhase
import io.element.android.libraries.setkalite.api.SetkaLiteSyncProgress
import io.element.android.libraries.setkalite.api.SetkaTransferQueue
import io.element.android.services.appnavstate.api.AppForegroundStateService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@ContributesBinding(SessionScope::class)
@SingleIn(SessionScope::class)
class DefaultSetkaLiteCoordinator(
    private val matrixClient: MatrixClient,
    private val syncService: SyncService,
    private val appPreferencesStore: AppPreferencesStore,
    private val transferQueue: SetkaTransferQueue,
    private val speedProbe: SetkaLiteSpeedProbe,
    private val syncEngine: SetkaLiteSyncEngine,
    private val apiClient: SetkaLiteApiClient,
    private val coordinatorState: SetkaLiteCoordinatorStateAccessor,
    private val appForegroundStateService: AppForegroundStateService,
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
) : SetkaLiteCoordinator {
    private var lastAutoRefreshAtMs = 0L

    private val isSetkaSession = SetkaMatrixConfig.isSetkaHomeserver(
        matrixClient.sessionId.value.substringAfter(':', "")
    )

    private val _state = MutableStateFlow(SetkaLiteState(isAvailable = false))
    override val state: StateFlow<SetkaLiteState> = _state.asStateFlow()

    init {
        sessionCoroutineScope.launch {
            refreshAvailability()
            runSpeedProbeIfNeeded()
        }
        sessionCoroutineScope.launch {
            combine(
                appPreferencesStore.getSetkaLiteEnabledFlow(),
                appPreferencesStore.getSetkaLiteSuggestionDismissedFlow(),
            ) { enabled, suggestionDismissed ->
                enabled to suggestionDismissed
            }.collectLatest { (enabled, suggestionDismissed) ->
                val available = _state.value.isAvailable
                val effectiveEnabled = enabled && available
                coordinatorState.setEnabled(effectiveEnabled)
                val probe = _state.value.speedProbe
                val showSuggestion = available &&
                    !effectiveEnabled &&
                    !suggestionDismissed &&
                    probe?.suggestedLiteMode == true
                _state.update {
                    it.copy(
                        isEnabled = effectiveEnabled,
                        showLiteSuggestion = showSuggestion,
                    )
                }
                if (effectiveEnabled) {
                    startSyncIfNeeded()
                } else if (!enabled) {
                    _state.update { it.copy(syncProgress = SetkaLiteSyncProgress()) }
                }
            }
        }
        sessionCoroutineScope.launch {
            transferQueue.items.collectLatest { queue ->
                _state.update { current ->
                    current.copy(transferQueue = queue)
                }
            }
        }
        observeAutoRefreshTriggers()
    }

    private fun observeAutoRefreshTriggers() {
        appForegroundStateService.startObservingForeground()
        sessionCoroutineScope.launch {
            var wasForeground = appForegroundStateService.isInForeground.value
            appForegroundStateService.isInForeground.collectLatest { inForeground ->
                val becameForeground = inForeground && !wasForeground
                wasForeground = inForeground
                if (becameForeground) {
                    onAppForegrounded()
                }
            }
        }
        sessionCoroutineScope.launch {
            var wasOnline = syncService.isOnline.value
            syncService.isOnline.collectLatest { online ->
                val becameOnline = online && !wasOnline
                wasOnline = online
                if (becameOnline) {
                    onNetworkOnline()
                }
            }
        }
    }

    private suspend fun onAppForegrounded() {
        if (!isSetkaSession) return
        if (!_state.value.isAvailable) {
            refreshAvailability()
        }
        when {
            _state.value.isEnabled -> maybeAutoRefreshSync()
            _state.value.isAvailable -> runSpeedProbeIfNeeded()
        }
    }

    private suspend fun onNetworkOnline() {
        if (!isSetkaSession || !_state.value.isAvailable) return
        if (_state.value.isEnabled) {
            maybeAutoRefreshSync()
        } else {
            runSpeedProbeIfNeeded()
        }
    }

    private fun maybeAutoRefreshSync() {
        val now = System.currentTimeMillis()
        if (now - lastAutoRefreshAtMs < AUTO_REFRESH_MIN_INTERVAL_MS) return
        if (_state.value.syncProgress.isActive) return
        lastAutoRefreshAtMs = now
        startSyncIfNeeded(force = true)
    }

    override suspend fun refreshAvailability() {
        if (!isSetkaSession) {
            _state.update { it.copy(isAvailable = false, isEnabled = false, showLiteSuggestion = false) }
            coordinatorState.setEnabled(false)
            return
        }
        val available = apiClient.getConfig() != null
        _state.update { it.copy(isAvailable = available) }
    }

    override suspend fun runSpeedProbeIfNeeded() {
        if (!_state.value.isAvailable) {
            refreshAvailability()
        }
        if (!_state.value.isAvailable) return
        val result = speedProbe.measure()
        val suggestionDismissed = appPreferencesStore.getSetkaLiteSuggestionDismissedFlow().first()
        _state.update { current ->
            current.copy(
                speedProbe = result,
                showLiteSuggestion = current.isAvailable &&
                    !current.isEnabled &&
                    !suggestionDismissed &&
                    result?.suggestedLiteMode == true,
            )
        }
    }

    override suspend fun setEnabled(enabled: Boolean) {
        appPreferencesStore.setSetkaLiteEnabled(enabled)
        if (enabled) {
            appPreferencesStore.setSetkaLiteSuggestionDismissed(true)
        }
        val available = _state.value.isAvailable
        val effectiveEnabled = enabled && available
        coordinatorState.setEnabled(effectiveEnabled)
        _state.update {
            it.copy(
                isEnabled = effectiveEnabled,
                showLiteSuggestion = false,
            )
        }
        if (effectiveEnabled) {
            startSyncIfNeeded()
        } else {
            _state.update { it.copy(syncProgress = SetkaLiteSyncProgress()) }
        }
    }

    override suspend fun refreshSync() {
        if (!_state.value.isEnabled || !_state.value.isAvailable) return
        startSyncIfNeeded(force = true)
    }

    override suspend fun dismissLiteSuggestion() {
        appPreferencesStore.setSetkaLiteSuggestionDismissed(true)
        _state.update { it.copy(showLiteSuggestion = false) }
    }

    private fun startSyncIfNeeded(force: Boolean = false) {
        val current = _state.value.syncProgress
        if (!force && (current.phase == SetkaLiteSyncPhase.DONE || current.isActive)) return
        sessionCoroutineScope.launch {
            if (force) {
                _state.update { it.copy(syncProgress = SetkaLiteSyncProgress()) }
            }
            // Lite backfill uses throttled setka_lite/*; Matrix /sync keeps running in parallel
            // so keys and timeline can catch up when the network allows it.
            syncService.startSync()
            syncEngine.runSync { progress ->
                _state.update { it.copy(syncProgress = progress) }
            }
        }
    }

    companion object {
        private const val AUTO_REFRESH_MIN_INTERVAL_MS = 10 * 60 * 1000L
    }
}
