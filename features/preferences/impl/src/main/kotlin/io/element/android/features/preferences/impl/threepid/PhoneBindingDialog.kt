package io.element.android.features.preferences.impl.threepid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun PhoneBindingDialog(
    state: ThreePidDialogState.AddPhone,
    onDismiss: () -> Unit,
    onCountryChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onRequestCode: () -> Unit,
    onSubmitCode: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.screen_setka_add_phone_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.country,
                    onValueChange = onCountryChange,
                    label = { Text(stringResource(R.string.screen_setka_add_phone_country_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isBusy && state.step == ThreePidDialogState.Step.Input,
                )
                OutlinedTextField(
                    value = state.phone,
                    onValueChange = onPhoneChange,
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
                    onValueChange = onCodeChange,
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
                        ThreePidDialogState.Step.Input -> onRequestCode()
                        ThreePidDialogState.Step.Requested -> onSubmitCode()
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
        },
    )
}
