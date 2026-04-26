package io.element.android.features.preferences.impl.threepid

import io.element.android.libraries.architecture.AsyncData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class ThreePidSettingsState(
    val threePids: AsyncData<ImmutableList<ThreePid>> = AsyncData.Uninitialized,
    val privacy: AsyncData<SetkaPrivacy> = AsyncData.Uninitialized,
    val errorMessage: String? = null,
    val dialog: ThreePidDialogState? = null,
    val pendingDelete: ThreePid? = null,
    val eventSink: (ThreePidSettingsEvents) -> Unit,
)

sealed interface ThreePidDialogState {
    data class AddEmail(
        val email: String = "",
        val clientSecret: String? = null,
        val sid: String? = null,
        val password: String = "",
        val sendAttempt: Int = 1,
        val step: Step = Step.Input,
        val isBusy: Boolean = false,
        val errorMessage: String? = null,
    ) : ThreePidDialogState

    data class AddPhone(
        val country: String = "RU",
        val phone: String = "",
        val clientSecret: String? = null,
        val sid: String? = null,
        val msisdn: String? = null,
        val submitUrl: String? = null,
        val smsCode: String = "",
        val password: String = "",
        val sendAttempt: Int = 1,
        val step: Step = Step.Input,
        val isBusy: Boolean = false,
        val errorMessage: String? = null,
    ) : ThreePidDialogState

    enum class Step {
        Input,
        Requested,
        TokenSubmitted,
    }
}

internal fun emptyThreePidList(): ImmutableList<ThreePid> = persistentListOf()
