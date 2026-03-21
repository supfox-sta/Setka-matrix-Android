/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.callsettings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.designsystem.components.preferences.PreferenceCategory
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.components.preferences.PreferenceSwitch

@Composable
fun CallSettingsView(
    state: CallSettingsState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePage(
        modifier = modifier,
        onBackClick = onBackClick,
        title = stringResource(id = R.string.screen_preferences_call_settings_title),
    ) {
        PreferenceCategory(title = stringResource(id = R.string.screen_preferences_call_settings_audio_routing)) {
            PreferenceSwitch(
                title = stringResource(id = R.string.screen_preferences_call_settings_prefer_earpiece),
                subtitle = stringResource(id = R.string.screen_preferences_call_settings_prefer_earpiece_description),
                isChecked = state.preferEarpieceByDefault,
                onCheckedChange = {
                    state.eventSink(CallSettingsEvents.SetPreferEarpieceByDefault(it))
                },
            )
            PreferenceSwitch(
                title = stringResource(id = R.string.screen_preferences_call_settings_proximity),
                subtitle = stringResource(id = R.string.screen_preferences_call_settings_proximity_description),
                isChecked = state.proximitySensorEnabled,
                onCheckedChange = {
                    state.eventSink(CallSettingsEvents.SetProximitySensorEnabled(it))
                },
            )
        }
    }
}

