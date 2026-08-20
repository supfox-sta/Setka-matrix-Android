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
        var privacy by remember { mutableStateOf<AsyncData<SetkaPrivacy>>(AsyncData.Uninitialized) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        fun refresh() {
            privacy = AsyncData.Loading(privacy.dataOrNull())
            errorMessage = null
            sessionCoroutineScope.launch {
                threePidService.getSetkaPrivacy()
                    .onSuccess { p -> privacy = AsyncData.Success(p) }
                    .onFailure { failure ->
                        privacy = AsyncData.Failure(failure, prevData = privacy.dataOrNull())
                        errorMessage = failure.message ?: "Не удалось загрузить настройки приватности"
                    }
            }
        }

        LaunchedEffect(Unit) {
            refresh()
        }

        fun handleEvent(event: ThreePidSettingsEvents) {
            when (event) {
                ThreePidSettingsEvents.Refresh -> refresh()
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
                is ThreePidSettingsEvents.SetCallVisibility -> {
                    val prev = privacy.dataOrNull()
                    privacy = AsyncData.Loading(prev)
                    sessionCoroutineScope.launch {
                        threePidService.setSetkaPrivacy(callVisibility = event.visibility)
                            .onSuccess { p -> privacy = AsyncData.Success(p) }
                            .onFailure { failure ->
                                privacy = AsyncData.Failure(failure, prevData = prev)
                                errorMessage = failure.message ?: "Не удалось сохранить настройки звонков"
                            }
                    }
                }
            }
        }

        return ThreePidSettingsState(
            privacy = privacy,
            errorMessage = errorMessage,
            eventSink = ::handleEvent,
        )
    }
}
