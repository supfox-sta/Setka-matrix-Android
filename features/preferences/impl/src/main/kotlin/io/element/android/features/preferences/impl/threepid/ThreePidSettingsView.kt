package io.element.android.features.preferences.impl.threepid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferenceCategory
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.components.preferences.PreferenceSwitch
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ListItemStyle
import io.element.android.libraries.designsystem.theme.components.Text

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThreePidSettingsView(
    state: ThreePidSettingsState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePage(
        modifier = modifier,
        onBackClick = onBackClick,
        title = stringResource(R.string.screen_preferences_contacts_privacy_title),
    ) {
        val privacy = state.privacy.dataOrNull()
        val privacyBusy = state.privacy is AsyncData.Loading

        PreferenceCategory(title = stringResource(R.string.screen_setka_privacy_visibility_section_title)) {
            VisibilityPreference(
                title = stringResource(R.string.screen_setka_privacy_last_seen_title),
                visibility = privacy?.lastSeenVisibility ?: SetkaVisibility.Everyone,
                enabled = !privacyBusy,
                onSelect = { state.eventSink(ThreePidSettingsEvents.SetLastSeenVisibility(it)) },
            )
            VisibilityPreference(
                title = stringResource(R.string.screen_setka_privacy_phone_visibility_title),
                visibility = privacy?.phoneVisibility ?: SetkaVisibility.Everyone,
                enabled = !privacyBusy,
                onSelect = { state.eventSink(ThreePidSettingsEvents.SetPhoneVisibility(it)) },
            )
        }

        PreferenceCategory(title = stringResource(R.string.screen_setka_privacy_search_section_title)) {
            PreferenceSwitch(
                title = stringResource(R.string.screen_setka_privacy_discoverable_msisdn_title),
                subtitle = stringResource(R.string.screen_setka_privacy_discoverable_msisdn_subtitle),
                isChecked = privacy?.discoverableMsisdn ?: true,
                enabled = !privacyBusy,
                onCheckedChange = { state.eventSink(ThreePidSettingsEvents.SetDiscoverableMsisdn(it)) },
            )
        }

        state.errorMessage?.let { message ->
            ListItem(
                headlineContent = { Text(message) },
                style = ListItemStyle.Destructive,
            )
        }

        Spacer(Modifier.height(8.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.screen_setka_threepids_refresh)) },
            trailingContent = when (state.privacy) {
                AsyncData.Uninitialized,
                is AsyncData.Loading -> ListItemContent.Custom {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                }
                else -> null
            },
            onClick = { state.eventSink(ThreePidSettingsEvents.Refresh) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VisibilityPreference(
    title: String,
    visibility: SetkaVisibility,
    enabled: Boolean,
    onSelect: (SetkaVisibility) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text = title)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SetkaVisibility.entries.forEach { option ->
                FilterChip(
                    selected = visibility == option,
                    onClick = { onSelect(option) },
                    enabled = enabled,
                    label = {
                        Text(
                            text = when (option) {
                                SetkaVisibility.Everyone -> stringResource(R.string.screen_setka_privacy_everyone)
                                SetkaVisibility.Contacts -> stringResource(R.string.screen_setka_privacy_contacts)
                                SetkaVisibility.Nobody -> stringResource(R.string.screen_setka_privacy_nobody)
                            }
                        )
                    },
                )
            }
        }
    }
}
