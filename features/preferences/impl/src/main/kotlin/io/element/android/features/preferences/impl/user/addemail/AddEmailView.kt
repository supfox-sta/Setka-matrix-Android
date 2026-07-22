package io.element.android.features.preferences.impl.user.addemail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.theme.components.FloatingActionButton
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text

private val FieldShape = RoundedCornerShape(16.dp)

@Composable
fun AddEmailView(
    state: AddEmailState,
    modifier: Modifier = Modifier,
) {
    BackHandler {
        state.eventSink(AddEmailEvent.Back)
    }

    Scaffold(
        modifier = modifier,
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BackButton(onClick = { state.eventSink(AddEmailEvent.Back) })
                    Text(
                        text = stringResource(R.string.screen_setka_add_email_title),
                        style = ElementTheme.typography.fontHeadingMdBold,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(
                        if (state.step == AddEmailStep.EnterEmail) {
                            R.string.screen_setka_add_email_instruction
                        } else {
                            R.string.screen_setka_add_email_code_hint
                        }
                    ),
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(32.dp))

                AnimatedContent(
                    targetState = state.step,
                    transitionSpec = {
                        if (targetState.ordinal > initialState.ordinal) {
                            (slideInHorizontally { it } + fadeIn()) togetherWith
                                (slideOutHorizontally { -it } + fadeOut())
                        } else {
                            (slideInHorizontally { -it } + fadeIn()) togetherWith
                                (slideOutHorizontally { it } + fadeOut())
                        }
                    },
                    label = "add_email_step",
                ) { step ->
                    when (step) {
                        AddEmailStep.EnterEmail -> EmailStepContent(
                            email = state.email,
                            error = state.emailError,
                            isValid = state.emailValid,
                            shakeToken = state.emailShakeToken,
                            enabled = !state.isBusy,
                            onEmailChange = { state.eventSink(AddEmailEvent.UpdateEmail(it)) },
                        )
                        AddEmailStep.EnterCode -> CodeStepContent(
                            code = state.code,
                            error = state.codeError,
                            isValid = state.codeValid,
                            shakeToken = state.codeShakeToken,
                            enabled = !state.isBusy,
                            onCodeChange = { state.eventSink(AddEmailEvent.UpdateCode(it)) },
                        )
                    }
                }
            }

            FloatingActionButton(
                onClick = {
                    when (state.step) {
                        AddEmailStep.EnterEmail -> state.eventSink(AddEmailEvent.SendEmail)
                        AddEmailStep.EnterCode -> state.eventSink(AddEmailEvent.SubmitCode)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .size(56.dp),
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = when (state.step) {
                        AddEmailStep.EnterEmail -> CompoundIcons.ChevronRight()
                        AddEmailStep.EnterCode -> CompoundIcons.Check()
                    },
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
private fun EmailStepContent(
    email: String,
    error: String?,
    isValid: Boolean,
    shakeToken: Int,
    enabled: Boolean,
    onEmailChange: (String) -> Unit,
) {
    val shakeOffset = rememberShakeOffset(shakeToken)
    val borderColor = fieldBorderColor(error, isValid && email.isNotBlank())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset(x = shakeOffset.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ShakeOutlinedField(
            value = email,
            onValueChange = onEmailChange,
            label = stringResource(R.string.screen_setka_add_email_email_label),
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            borderColor = borderColor,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        error?.let { ErrorText(it) }
    }
}

@Composable
private fun CodeStepContent(
    code: String,
    error: String?,
    isValid: Boolean,
    shakeToken: Int,
    enabled: Boolean,
    onCodeChange: (String) -> Unit,
) {
    val shakeOffset = rememberShakeOffset(shakeToken)
    val borderColor = fieldBorderColor(error, isValid && code.isNotBlank())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset(x = shakeOffset.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.screen_setka_add_email_enter_code),
            style = ElementTheme.typography.fontBodyLgMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        ShakeOutlinedField(
            value = code,
            onValueChange = onCodeChange,
            label = stringResource(R.string.screen_setka_add_email_code_label),
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            borderColor = borderColor,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )
        error?.let { ErrorText(it) }
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(
        text = message,
        color = ElementTheme.colors.textCriticalPrimary,
        style = ElementTheme.typography.fontBodySmRegular,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun fieldBorderColor(error: String?, valid: Boolean): Color = when {
    error != null -> ElementTheme.colors.borderCriticalPrimary
    valid -> ElementTheme.colors.borderSuccessSubtle
    else -> ElementTheme.colors.borderInteractiveSecondary
}

@Composable
private fun ShakeOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    borderColor: Color,
    keyboardOptions: KeyboardOptions,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = keyboardOptions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = borderColor,
            unfocusedBorderColor = borderColor,
            disabledBorderColor = borderColor.copy(alpha = 0.5f),
        ),
        shape = FieldShape,
    )
}

@Composable
private fun rememberShakeOffset(shakeToken: Int): Float {
    val offset = remember { Animatable(0f) }
    LaunchedEffect(shakeToken) {
        if (shakeToken == 0) return@LaunchedEffect
        repeat(3) {
            offset.animateTo(10f, animationSpec = tween(45))
            offset.animateTo(-10f, animationSpec = tween(45))
        }
        offset.animateTo(0f, animationSpec = tween(45))
    }
    return offset.value
}
