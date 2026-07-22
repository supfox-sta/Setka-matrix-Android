package io.element.android.features.preferences.impl.user.changephone

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
class ChangePhoneNumberPresenter(
    @Assisted private val navigator: ChangePhoneNumberNavigator,
    private val threePidService: ThreePidService,
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
) : Presenter<ChangePhoneNumberState> {
    @AssistedFactory
    interface Factory {
        fun create(navigator: ChangePhoneNumberNavigator): ChangePhoneNumberPresenter
    }

    @Composable
    override fun present(): ChangePhoneNumberState {
        var step by rememberSaveable { mutableStateOf(ChangePhoneStep.EnterPhone) }
        var dialCode by rememberSaveable { mutableStateOf("+7") }
        var phoneNumber by rememberSaveable { mutableStateOf("") }
        var code by rememberSaveable { mutableStateOf("") }
        var phoneError by remember { mutableStateOf<String?>(null) }
        var codeError by remember { mutableStateOf<String?>(null) }
        var phoneShakeToken by remember { mutableIntStateOf(0) }
        var codeShakeToken by remember { mutableIntStateOf(0) }
        var isBusy by remember { mutableStateOf(false) }
        var clientSecret by rememberSaveable { mutableStateOf<String?>(null) }
        var sid by rememberSaveable { mutableStateOf<String?>(null) }
        var sendAttempt by rememberSaveable { mutableIntStateOf(1) }

        val msisdn = remember(dialCode, phoneNumber) { buildMsisdn(dialCode, phoneNumber) }
        val phoneValid = remember(msisdn) { isValidMsisdn(msisdn) }
        val codeValid = remember(code) { code.trim().matches(Regex("^\\d{6}$")) }

        fun handleEvent(event: ChangePhoneNumberEvent) {
            when (event) {
                ChangePhoneNumberEvent.Back -> {
                    if (step == ChangePhoneStep.EnterCode) {
                        step = ChangePhoneStep.EnterPhone
                        code = ""
                        codeError = null
                    } else {
                        navigator.close(changed = false)
                    }
                }
                is ChangePhoneNumberEvent.UpdateDialCode -> {
                    dialCode = event.value.filter { it == '+' || it.isDigit() }.let { filtered ->
                        if (filtered.isEmpty()) "+" else filtered
                    }
                    phoneError = null
                }
                is ChangePhoneNumberEvent.UpdatePhoneNumber -> {
                    phoneNumber = event.value.filter { it.isDigit() }
                    phoneError = null
                }
                is ChangePhoneNumberEvent.UpdateCode -> {
                    code = event.value.filter { it.isDigit() }.take(6)
                    codeError = null
                }
                ChangePhoneNumberEvent.Continue -> {
                    if (isBusy) return
                    if (!phoneValid) {
                        phoneError = "Введите корректный номер телефона"
                        phoneShakeToken++
                        return
                    }
                    phoneError = null
                    isBusy = true
                    sessionCoroutineScope.launch {
                        val request = if (clientSecret != null) {
                            threePidService.requestMsisdnToken(
                                country = dialCodeToCountry(dialCode),
                                phoneNumber = msisdn,
                                sendAttempt = sendAttempt,
                                clientSecret = clientSecret!!,
                            )
                        } else {
                            threePidService.requestMsisdnToken(
                                country = dialCodeToCountry(dialCode),
                                phoneNumber = msisdn,
                                sendAttempt = sendAttempt,
                            )
                        }
                        request.fold(
                            onSuccess = { (secret, result) ->
                                clientSecret = secret
                                sid = result.sid
                                sendAttempt++
                                isBusy = false
                                step = ChangePhoneStep.EnterCode
                            },
                            onFailure = { failure ->
                                isBusy = false
                                phoneError = failure.message ?: "Не удалось отправить код"
                                phoneShakeToken++
                            }
                        )
                    }
                }
                ChangePhoneNumberEvent.SubmitCode -> {
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
                        threePidService.submitMsisdnTokenUnstable(
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
                            }
                        )
                    }
                }
            }
        }

        return ChangePhoneNumberState(
            step = step,
            dialCode = dialCode,
            phoneNumber = phoneNumber,
            code = code,
            phoneError = phoneError,
            codeError = codeError,
            phoneShakeToken = phoneShakeToken,
            codeShakeToken = codeShakeToken,
            phoneValid = phoneValid,
            codeValid = codeValid,
            isBusy = isBusy,
            eventSink = ::handleEvent,
        )
    }

    private fun buildMsisdn(dialCode: String, phoneNumber: String): String {
        val normalizedCode = dialCode.trim().let { code ->
            when {
                code.isEmpty() -> "+"
                code.startsWith("+") -> code
                else -> "+$code"
            }
        }
        return normalizedCode + phoneNumber.filter { it.isDigit() }
    }

    private fun isValidMsisdn(msisdn: String): Boolean =
        msisdn.matches(Regex("^\\+[1-9]\\d{7,14}$"))

    private fun dialCodeToCountry(dialCode: String): String = when (dialCode.trim()) {
        "+7" -> "RU"
        "+1" -> "US"
        "+44" -> "GB"
        "+49" -> "DE"
        else -> dialCode.removePrefix("+").ifBlank { "RU" }
    }
}
