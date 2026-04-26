package io.element.android.features.preferences.impl.threepid

sealed interface ThreePidSettingsEvents {
    data object Refresh : ThreePidSettingsEvents
    data class SetDiscoverableEmail(val enabled: Boolean) : ThreePidSettingsEvents
    data class SetDiscoverableMsisdn(val enabled: Boolean) : ThreePidSettingsEvents
    data class SetEmailVisibility(val visibility: SetkaVisibility) : ThreePidSettingsEvents
    data class SetPhoneVisibility(val visibility: SetkaVisibility) : ThreePidSettingsEvents
    data class SetLastSeenVisibility(val visibility: SetkaVisibility) : ThreePidSettingsEvents

    data object OpenAddEmail : ThreePidSettingsEvents
    data object OpenAddPhone : ThreePidSettingsEvents
    data object DismissDialogs : ThreePidSettingsEvents

    data class UpdateEmail(val email: String) : ThreePidSettingsEvents
    data class UpdateCountry(val country: String) : ThreePidSettingsEvents
    data class UpdatePhone(val phone: String) : ThreePidSettingsEvents
    data class UpdateSmsCode(val code: String) : ThreePidSettingsEvents
    data class UpdatePassword(val password: String) : ThreePidSettingsEvents

    data object RequestEmailToken : ThreePidSettingsEvents
    data object RequestMsisdnToken : ThreePidSettingsEvents
    data object SubmitMsisdnToken : ThreePidSettingsEvents
    data object AddThreePid : ThreePidSettingsEvents

    data class RequestDelete(val threePid: ThreePid) : ThreePidSettingsEvents
    data object ConfirmDelete : ThreePidSettingsEvents
    data object DismissDelete : ThreePidSettingsEvents
}
