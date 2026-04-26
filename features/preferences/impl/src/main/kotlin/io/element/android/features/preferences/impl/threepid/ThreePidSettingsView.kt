package io.element.android.features.preferences.impl.threepid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferenceCategory
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.components.preferences.PreferenceSwitch
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ListItemStyle
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings

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
            VisibilityPreference(
                title = stringResource(R.string.screen_setka_privacy_email_visibility_title),
                visibility = privacy?.emailVisibility ?: SetkaVisibility.Everyone,
                enabled = !privacyBusy,
                onSelect = { state.eventSink(ThreePidSettingsEvents.SetEmailVisibility(it)) },
            )
        }

        PreferenceCategory(title = stringResource(R.string.screen_setka_privacy_search_section_title)) {
            PreferenceSwitch(
                title = stringResource(R.string.screen_setka_privacy_discoverable_email_title),
                subtitle = stringResource(R.string.screen_setka_privacy_discoverable_email_subtitle),
                isChecked = privacy?.discoverableEmail ?: true,
                enabled = !privacyBusy,
                onCheckedChange = { state.eventSink(ThreePidSettingsEvents.SetDiscoverableEmail(it)) },
            )
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

        PreferenceCategory(title = stringResource(R.string.screen_setka_threepids_email_section_title)) {
            ThreePidList(
                items = state.threePids.dataOrNull().orEmpty(),
                medium = ThreePidMedium.Email,
                onDelete = { state.eventSink(ThreePidSettingsEvents.RequestDelete(it)) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.screen_setka_threepids_add_email)) },
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Plus())),
                onClick = { state.eventSink(ThreePidSettingsEvents.OpenAddEmail) },
            )
        }

        PreferenceCategory(title = stringResource(R.string.screen_setka_threepids_phone_section_title)) {
            ThreePidList(
                items = state.threePids.dataOrNull().orEmpty(),
                medium = ThreePidMedium.Msisdn,
                onDelete = { state.eventSink(ThreePidSettingsEvents.RequestDelete(it)) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.screen_setka_threepids_add_phone)) },
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Plus())),
                onClick = { state.eventSink(ThreePidSettingsEvents.OpenAddPhone) },
            )
        }

        Spacer(Modifier.height(8.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.screen_setka_threepids_refresh)) },
            trailingContent = when (state.threePids) {
                AsyncData.Uninitialized,
                is AsyncData.Loading -> ListItemContent.Custom {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                }
                else -> null
            },
            onClick = { state.eventSink(ThreePidSettingsEvents.Refresh) },
        )
    }

    when (val dialog = state.dialog) {
        is ThreePidDialogState.AddEmail -> AddEmailDialog(
            state = dialog,
            onDismiss = { state.eventSink(ThreePidSettingsEvents.DismissDialogs) },
            eventSink = state.eventSink,
        )
        is ThreePidDialogState.AddPhone -> AddPhoneDialog(
            state = dialog,
            onDismiss = { state.eventSink(ThreePidSettingsEvents.DismissDialogs) },
            eventSink = state.eventSink,
        )
        null -> Unit
    }

    state.pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { state.eventSink(ThreePidSettingsEvents.DismissDelete) },
            title = { Text(stringResource(R.string.screen_setka_delete_threepid_title)) },
            text = { Text("${item.medium.raw}: ${item.address}") },
            confirmButton = {
                TextButton(onClick = { state.eventSink(ThreePidSettingsEvents.ConfirmDelete) }) {
                    Text(stringResource(R.string.screen_setka_delete_threepid_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { state.eventSink(ThreePidSettingsEvents.DismissDelete) }) {
                    Text(stringResource(CommonStrings.action_cancel))
                }
            }
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

@Composable
private fun ThreePidList(
    items: List<ThreePid>,
    medium: ThreePidMedium,
    onDelete: (ThreePid) -> Unit,
) {
    val filtered = remember(items, medium) { items.filter { it.medium == medium } }
    if (filtered.isEmpty()) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.screen_setka_threepids_empty)) },
        )
        return
    }
    filtered.forEach { item ->
        ListItem(
            headlineContent = { Text(item.address) },
            supportingContent = {
                val validated = item.validatedAtMillis != null
                Text(
                    stringResource(
                        if (validated) R.string.screen_setka_threepids_verified else R.string.screen_setka_threepids_unverified
                    )
                )
            },
            trailingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Close())),
            onClick = { onDelete(item) },
        )
    }
}

@Composable
private fun AddEmailDialog(
    state: ThreePidDialogState.AddEmail,
    onDismiss: () -> Unit,
    eventSink: (ThreePidSettingsEvents) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.screen_setka_add_email_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.email,
                    onValueChange = { eventSink(ThreePidSettingsEvents.UpdateEmail(it)) },
                    label = { Text(stringResource(R.string.screen_setka_add_email_email_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    enabled = !state.isBusy && state.step == ThreePidDialogState.Step.Input,
                )
                if (state.step != ThreePidDialogState.Step.Input) {
                    Text(stringResource(R.string.screen_setka_add_email_confirm_hint))
                }
                OutlinedTextField(
                    value = state.password,
                    onValueChange = { eventSink(ThreePidSettingsEvents.UpdatePassword(it)) },
                    label = { Text(stringResource(R.string.screen_setka_add_email_password_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isBusy,
                )
                state.errorMessage?.let { Text(it) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (state.step == ThreePidDialogState.Step.Input) {
                        eventSink(ThreePidSettingsEvents.RequestEmailToken)
                    } else {
                        eventSink(ThreePidSettingsEvents.AddThreePid)
                    }
                },
                enabled = !state.isBusy,
            ) {
                Text(
                    if (state.step == ThreePidDialogState.Step.Input) {
                        stringResource(R.string.screen_setka_add_email_request)
                    } else {
                        stringResource(R.string.screen_setka_add_threepid_add)
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isBusy) {
                Text(stringResource(CommonStrings.action_cancel))
            }
        }
    )
}

@Composable
private fun AddPhoneDialog(
    state: ThreePidDialogState.AddPhone,
    onDismiss: () -> Unit,
    eventSink: (ThreePidSettingsEvents) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.screen_setka_add_phone_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.country,
                    onValueChange = { eventSink(ThreePidSettingsEvents.UpdateCountry(it.uppercase())) },
                    label = { Text(stringResource(R.string.screen_setka_add_phone_country_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isBusy && state.step == ThreePidDialogState.Step.Input,
                )
                OutlinedTextField(
                    value = state.phone,
                    onValueChange = { eventSink(ThreePidSettingsEvents.UpdatePhone(it)) },
                    label = { Text(stringResource(R.string.screen_setka_add_phone_number_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    enabled = !state.isBusy && state.step == ThreePidDialogState.Step.Input,
                )
                if (state.step != ThreePidDialogState.Step.Input) {
                    Text(stringResource(R.string.screen_setka_add_phone_hint))
                }
                OutlinedTextField(
                    value = state.smsCode,
                    onValueChange = { eventSink(ThreePidSettingsEvents.UpdateSmsCode(it)) },
                    label = { Text(stringResource(R.string.screen_setka_add_phone_code_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isBusy && state.step == ThreePidDialogState.Step.Requested,
                )
                state.errorMessage?.let { Text(it) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (state.step) {
                        ThreePidDialogState.Step.Input -> eventSink(ThreePidSettingsEvents.RequestMsisdnToken)
                        ThreePidDialogState.Step.Requested -> eventSink(ThreePidSettingsEvents.SubmitMsisdnToken)
                        ThreePidDialogState.Step.TokenSubmitted -> onDismiss()
                    }
                },
                enabled = !state.isBusy,
            ) {
                Text(
                    when (state.step) {
                        ThreePidDialogState.Step.Input -> stringResource(R.string.screen_setka_add_phone_send_code)
                        ThreePidDialogState.Step.Requested -> stringResource(R.string.screen_setka_add_phone_submit_code)
                        ThreePidDialogState.Step.TokenSubmitted -> stringResource(CommonStrings.action_done)
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isBusy) {
                Text(stringResource(CommonStrings.action_cancel))
            }
        }
    )
}
