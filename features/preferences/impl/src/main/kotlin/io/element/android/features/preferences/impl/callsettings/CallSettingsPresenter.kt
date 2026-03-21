/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.callsettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.preferences.api.store.AppPreferencesStore
import kotlinx.coroutines.launch

@Inject
class CallSettingsPresenter(
    private val appPreferencesStore: AppPreferencesStore,
) : Presenter<CallSettingsState> {
    @Composable
    override fun present(): CallSettingsState {
        val coroutineScope = rememberCoroutineScope()
        val preferEarpieceByDefault by remember {
            appPreferencesStore.getCallPreferEarpieceByDefaultFlow()
        }.collectAsState(initial = true)
        val proximitySensorEnabled by remember {
            appPreferencesStore.getCallEnableProximitySensorFlow()
        }.collectAsState(initial = true)

        fun handleEvent(event: CallSettingsEvents) {
            when (event) {
                is CallSettingsEvents.SetPreferEarpieceByDefault -> {
                    coroutineScope.launch {
                        appPreferencesStore.setCallPreferEarpieceByDefault(event.enabled)
                    }
                }
                is CallSettingsEvents.SetProximitySensorEnabled -> {
                    coroutineScope.launch {
                        appPreferencesStore.setCallEnableProximitySensor(event.enabled)
                    }
                }
            }
        }

        return CallSettingsState(
            preferEarpieceByDefault = preferEarpieceByDefault,
            proximitySensorEnabled = proximitySensorEnabled,
            eventSink = ::handleEvent,
        )
    }
}

