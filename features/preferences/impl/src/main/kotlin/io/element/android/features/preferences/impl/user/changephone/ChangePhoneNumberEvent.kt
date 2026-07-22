package io.element.android.features.preferences.impl.user.changephone

sealed interface ChangePhoneNumberEvent {
    data object Back : ChangePhoneNumberEvent
    data class UpdateDialCode(val value: String) : ChangePhoneNumberEvent
    data class UpdatePhoneNumber(val value: String) : ChangePhoneNumberEvent
    data class UpdateCode(val value: String) : ChangePhoneNumberEvent
    data object Continue : ChangePhoneNumberEvent
    data object SubmitCode : ChangePhoneNumberEvent
}
