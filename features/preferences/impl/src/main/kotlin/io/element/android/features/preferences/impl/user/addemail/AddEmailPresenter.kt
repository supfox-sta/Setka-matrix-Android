package io.element.android.features.preferences.impl.user.addemail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.preferences.impl.threepid.ThreePidService
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@AssistedInject
class AddEmailPresenter(
    @Assisted private val navigator: AddEmailNavigator,
    private val threePidService: ThreePidService,
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
) : Presenter<AddEmailState> {
    @AssistedFactory
    interface Factory {
        fun create(navigator: AddEmailNavigator): AddEmailPresenter
    }

    @Composable
    override fun present(): AddEmailState {
        var step by rememberSaveable { mutableStateOf(AddEmailStep.EnterEmail) }
        var email by rememberSaveable { mutableStateOf("") }
        var code by rememberSaveable { mutableStateOf("") }
        var emailError by remember { mutableStateOf<String?>(null) }
        var codeError by remember { mutableStateOf<String?>(null) }
        var emailShakeToken by remember { mutableIntStateOf(0) }
        var codeShakeToken by remember { mutableIntStateOf(0) }
        var isBusy by remember { mutableStateOf(false) }
        var clientSecret by rememberSaveable { mutableStateOf<String?>(null) }
        var sid by rememberSaveable { mutableStateOf<String?>(null) }
        var sendAttempt by rememberSaveable { mutableIntStateOf(1) }

        val emailValid = remember(email) { isValidEmail(email) }
        val codeValid = remember(code) { code.trim().matches(Regex("^\\d{6}$")) }

        fun handleEvent(event: AddEmailEvent) {
            when (event) {
                AddEmailEvent.Back -> {
                    if (step == AddEmailStep.EnterCode) {
                        step = AddEmailStep.EnterEmail
                        code = ""
                        codeError = null
                    } else {
                        navigator.close(changed = false)
                    }
                }
                is AddEmailEvent.UpdateEmail -> {
                    email = event.value
                    emailError = null
                }
                is AddEmailEvent.UpdateCode -> {
                    code = event.value.filter { it.isDigit() }.take(6)
                    codeError = null
                }
                AddEmailEvent.SendEmail -> {
                    if (isBusy) return
                    if (!emailValid) {
                        emailError = "Введите корректный email"
                        emailShakeToken++
                        return
                    }
                    emailError = null
                    isBusy = true
                    sessionCoroutineScope.launch {
                        val request = if (clientSecret != null) {
                            threePidService.requestEmailToken(
                                email = email.trim(),
                                sendAttempt = sendAttempt,
                                clientSecret = clientSecret!!,
                            )
                        } else {
                            threePidService.requestEmailToken(
                                email = email.trim(),
                                sendAttempt = sendAttempt,
                            )
                        }
                        request.fold(
                            onSuccess = { (secret, result) ->
                                clientSecret = secret
                                sid = result.sid
                                sendAttempt++
                                isBusy = false
                                step = AddEmailStep.EnterCode
                            },
                            onFailure = { failure ->
                                isBusy = false
                                emailError = failure.message ?: "Не удалось отправить код"
                                emailShakeToken++
                            },
                        )
                    }
                }
                AddEmailEvent.SubmitCode -> {
                    if (isBusy) return
                    val currentSid = sid
                    val currentSecret = clientSecret
                    if (!codeValid) {
                        codeError = "Введите 6-значный код"
                        codeShakeToken++
                        return
                    }
                    if (currentSid.isNullOrBlank() || currentSecret.isNullOrBlank()) {
                        codeError = "Сначала запросите код"
                        codeShakeToken++
                        return
                    }
                    codeError = null
                    isBusy = true
                    sessionCoroutineScope.launch {
                        threePidService.submitEmailToken(
                            sid = currentSid,
                            clientSecret = currentSecret,
                            token = code.trim(),
                        ).fold(
                            onSuccess = {
                                isBusy = false
                                navigator.close(changed = true)
                            },
                            onFailure = { failure ->
                                isBusy = false
                                codeError = failure.message ?: "Неверный код"
                                codeShakeToken++
                            },
                        )
                    }
                }
            }
        }

        return AddEmailState(
            step = step,
            email = email,
            code = code,
            emailError = emailError,
            codeError = codeError,
            emailShakeToken = emailShakeToken,
            codeShakeToken = codeShakeToken,
            emailValid = emailValid,
            codeValid = codeValid,
            isBusy = isBusy,
            eventSink = ::handleEvent,
        )
    }

    private fun isValidEmail(value: String): Boolean =
        value.trim().matches(Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", RegexOption.IGNORE_CASE))
}
