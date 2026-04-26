package io.element.android.features.preferences.impl.threepid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Inject
class ThreePidSettingsPresenter(
    private val threePidService: ThreePidService,
    @SessionCoroutineScope
    private val sessionCoroutineScope: CoroutineScope,
) : Presenter<ThreePidSettingsState> {
    @Composable
    override fun present(): ThreePidSettingsState {
        var threePids by remember { mutableStateOf<AsyncData<kotlinx.collections.immutable.ImmutableList<ThreePid>>>(AsyncData.Uninitialized) }
        var privacy by remember { mutableStateOf<AsyncData<SetkaPrivacy>>(AsyncData.Uninitialized) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var dialog by remember { mutableStateOf<ThreePidDialogState?>(null) }
        var pendingDelete by remember { mutableStateOf<ThreePid?>(null) }

        fun refresh() {
            threePids = AsyncData.Loading()
            privacy = AsyncData.Loading(privacy.dataOrNull())
            errorMessage = null
            sessionCoroutineScope.launch {
                threePidService.getThreePids()
                    .onSuccess { list ->
                        threePids = AsyncData.Success(list.toImmutableList())
                    }
                    .onFailure { failure ->
                        threePids = AsyncData.Success(emptyThreePidList())
                        errorMessage = failure.message ?: "Не удалось загрузить email/телефон"
                    }
            }
            sessionCoroutineScope.launch {
                threePidService.getSetkaPrivacy()
                    .onSuccess { p -> privacy = AsyncData.Success(p) }
                    .onFailure { failure ->
                        privacy = AsyncData.Failure(failure, prevData = privacy.dataOrNull())
                    }
            }
        }

        LaunchedEffect(Unit) {
            refresh()
        }

        fun handleEvent(event: ThreePidSettingsEvents) {
            when (event) {
                ThreePidSettingsEvents.Refresh -> refresh()
                is ThreePidSettingsEvents.SetDiscoverableEmail -> {
                    val prev = privacy.dataOrNull()
                    privacy = AsyncData.Loading(prev)
                    sessionCoroutineScope.launch {
                        threePidService.setSetkaPrivacy(discoverableEmail = event.enabled)
                            .onSuccess { p -> privacy = AsyncData.Success(p) }
                            .onFailure { failure ->
                                privacy = AsyncData.Failure(failure, prevData = prev)
                                errorMessage = failure.message ?: "Не удалось сохранить"
                            }
                    }
                }
                is ThreePidSettingsEvents.SetDiscoverableMsisdn -> {
                    val prev = privacy.dataOrNull()
                    privacy = AsyncData.Loading(prev)
                    sessionCoroutineScope.launch {
                        threePidService.setSetkaPrivacy(discoverableMsisdn = event.enabled)
                            .onSuccess { p -> privacy = AsyncData.Success(p) }
                            .onFailure { failure ->
                                privacy = AsyncData.Failure(failure, prevData = prev)
                                errorMessage = failure.message ?: "Не удалось сохранить"
                            }
                    }
                }
                is ThreePidSettingsEvents.SetEmailVisibility -> {
                    val prev = privacy.dataOrNull()
                    privacy = AsyncData.Loading(prev)
                    sessionCoroutineScope.launch {
                        threePidService.setSetkaPrivacy(emailVisibility = event.visibility)
                            .onSuccess { p -> privacy = AsyncData.Success(p) }
                            .onFailure { failure ->
                                privacy = AsyncData.Failure(failure, prevData = prev)
                                errorMessage = failure.message ?: "Не удалось сохранить приватность email"
                            }
                    }
                }
                is ThreePidSettingsEvents.SetPhoneVisibility -> {
                    val prev = privacy.dataOrNull()
                    privacy = AsyncData.Loading(prev)
                    sessionCoroutineScope.launch {
                        threePidService.setSetkaPrivacy(phoneVisibility = event.visibility)
                            .onSuccess { p -> privacy = AsyncData.Success(p) }
                            .onFailure { failure ->
                                privacy = AsyncData.Failure(failure, prevData = prev)
                                errorMessage = failure.message ?: "Не удалось сохранить приватность телефона"
                            }
                    }
                }
                is ThreePidSettingsEvents.SetLastSeenVisibility -> {
                    val prev = privacy.dataOrNull()
                    privacy = AsyncData.Loading(prev)
                    sessionCoroutineScope.launch {
                        threePidService.setSetkaPrivacy(lastSeenVisibility = event.visibility)
                            .onSuccess { p -> privacy = AsyncData.Success(p) }
                            .onFailure { failure ->
                                privacy = AsyncData.Failure(failure, prevData = prev)
                                errorMessage = failure.message ?: "Не удалось сохранить приватность времени входа"
                            }
                    }
                }
                ThreePidSettingsEvents.OpenAddEmail -> {
                    dialog = ThreePidDialogState.AddEmail()
                }
                ThreePidSettingsEvents.OpenAddPhone -> {
                    dialog = ThreePidDialogState.AddPhone()
                }
                ThreePidSettingsEvents.DismissDialogs -> {
                    dialog = null
                }
                is ThreePidSettingsEvents.UpdateEmail -> {
                    dialog = (dialog as? ThreePidDialogState.AddEmail)?.copy(email = event.email, errorMessage = null)
                        ?: dialog
                }
                is ThreePidSettingsEvents.UpdateCountry -> {
                    dialog = (dialog as? ThreePidDialogState.AddPhone)?.copy(country = event.country, errorMessage = null)
                        ?: dialog
                }
                is ThreePidSettingsEvents.UpdatePhone -> {
                    dialog = (dialog as? ThreePidDialogState.AddPhone)?.copy(phone = event.phone, errorMessage = null)
                        ?: dialog
                }
                is ThreePidSettingsEvents.UpdateSmsCode -> {
                    dialog = (dialog as? ThreePidDialogState.AddPhone)?.copy(smsCode = event.code, errorMessage = null)
                        ?: dialog
                }
                is ThreePidSettingsEvents.UpdatePassword -> {
                    dialog = when (val d = dialog) {
                        is ThreePidDialogState.AddEmail -> d.copy(password = event.password, errorMessage = null)
                        is ThreePidDialogState.AddPhone -> d.copy(password = event.password, errorMessage = null)
                        else -> d
                    }
                }
                ThreePidSettingsEvents.RequestEmailToken -> {
                    val d = dialog as? ThreePidDialogState.AddEmail ?: return
                    if (d.email.trim().isBlank()) {
                        dialog = d.copy(errorMessage = "Введите email")
                        return
                    }
                    dialog = d.copy(isBusy = true, errorMessage = null)
                    sessionCoroutineScope.launch {
                        threePidService.requestEmailToken(
                            email = d.email.trim(),
                            sendAttempt = d.sendAttempt,
                        ).fold(
                            onSuccess = { (clientSecret, result) ->
                                dialog = d.copy(
                                    isBusy = false,
                                    clientSecret = clientSecret,
                                    sid = result.sid,
                                    sendAttempt = d.sendAttempt + 1,
                                    step = ThreePidDialogState.Step.Requested,
                                    errorMessage = null,
                                )
                            },
                            onFailure = { failure ->
                                dialog = d.copy(
                                    isBusy = false,
                                    errorMessage = failure.message ?: "Не удалось отправить письмо",
                                )
                            }
                        )
                    }
                }
                ThreePidSettingsEvents.RequestMsisdnToken -> {
                    val d = dialog as? ThreePidDialogState.AddPhone ?: return
                    if (d.phone.trim().isBlank()) {
                        dialog = d.copy(errorMessage = "Введите номер")
                        return
                    }
                    dialog = d.copy(isBusy = true, errorMessage = null)
                    sessionCoroutineScope.launch {
                        threePidService.requestMsisdnToken(
                            country = d.country.trim(),
                            phoneNumber = d.phone.trim(),
                            sendAttempt = d.sendAttempt,
                        ).fold(
                            onSuccess = { (clientSecret, result) ->
                                dialog = d.copy(
                                    isBusy = false,
                                    clientSecret = clientSecret,
                                    sid = result.sid,
                                    msisdn = result.msisdn,
                                    submitUrl = result.submitUrl,
                                    sendAttempt = d.sendAttempt + 1,
                                    step = ThreePidDialogState.Step.Requested,
                                    errorMessage = null,
                                )
                            },
                            onFailure = { failure ->
                                dialog = d.copy(
                                    isBusy = false,
                                    errorMessage = failure.message ?: "Не удалось отправить код на почту",
                                )
                            }
                        )
                    }
                }
                ThreePidSettingsEvents.SubmitMsisdnToken -> {
                    val d = dialog as? ThreePidDialogState.AddPhone ?: return
                    val sid = d.sid ?: run {
                        dialog = d.copy(errorMessage = "Сначала запросите код")
                        return
                    }
                    val clientSecret = d.clientSecret ?: run {
                        dialog = d.copy(errorMessage = "Сначала запросите код")
                        return
                    }
                    val token = d.smsCode.trim()
                    if (token.isBlank()) {
                        dialog = d.copy(errorMessage = "Введите код из почты")
                        return
                    }
                    dialog = d.copy(isBusy = true, errorMessage = null)
                    sessionCoroutineScope.launch {
                        threePidService.submitMsisdnTokenUnstable(sid = sid, clientSecret = clientSecret, token = token)
                            .fold(
                                onSuccess = {
                                    dialog = null
                                    refresh()
                                },
                                onFailure = { failure ->
                                    dialog = d.copy(
                                        isBusy = false,
                                        errorMessage = failure.message ?: "Не удалось проверить код",
                                    )
                                }
                            )
                    }
                }
                ThreePidSettingsEvents.AddThreePid -> {
                    when (val d = dialog) {
                        is ThreePidDialogState.AddEmail -> {
                            val sid = d.sid ?: run {
                                dialog = d.copy(errorMessage = "Сначала подтвердите email")
                                return
                            }
                            val clientSecret = d.clientSecret ?: run {
                                dialog = d.copy(errorMessage = "Сначала подтвердите email")
                                return
                            }
                            val password = d.password
                            if (password.isBlank()) {
                                dialog = d.copy(errorMessage = "Введите пароль")
                                return
                            }
                            dialog = d.copy(isBusy = true, errorMessage = null)
                            sessionCoroutineScope.launch {
                                threePidService.addThreePidWithPassword(sid = sid, clientSecret = clientSecret, password = password)
                                    .fold(
                                        onSuccess = {
                                            dialog = null
                                            refresh()
                                        },
                                        onFailure = { failure ->
                                            dialog = d.copy(
                                                isBusy = false,
                                                errorMessage = failure.message ?: "Не удалось добавить email",
                                            )
                                        }
                                    )
                            }
                        }
                        is ThreePidDialogState.AddPhone -> {
                            // For phone numbers we bind the 3PID directly during token submission.
                            dialog = null
                            refresh()
                        }
                        else -> Unit
                    }
                }
                is ThreePidSettingsEvents.RequestDelete -> {
                    pendingDelete = event.threePid
                }
                ThreePidSettingsEvents.DismissDelete -> {
                    pendingDelete = null
                }
                ThreePidSettingsEvents.ConfirmDelete -> {
                    val item = pendingDelete ?: return
                    pendingDelete = null
                    errorMessage = null
                    sessionCoroutineScope.launch {
                        threePidService.deleteThreePid(item.medium, item.address)
                            .onSuccess { refresh() }
                            .onFailure { failure ->
                                errorMessage = failure.message ?: "Не удалось удалить"
                            }
                    }
                }
            }
        }

        return ThreePidSettingsState(
            threePids = threePids,
            privacy = privacy,
            errorMessage = errorMessage,
            dialog = dialog,
            pendingDelete = pendingDelete,
            eventSink = ::handleEvent,
        )
    }
}
