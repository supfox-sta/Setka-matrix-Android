package io.element.android.features.preferences.impl.user.addemail

sealed interface AddEmailEvent {
    data object Back : AddEmailEvent
    data class UpdateEmail(val value: String) : AddEmailEvent
    data class UpdateCode(val value: String) : AddEmailEvent
    data object SendEmail : AddEmailEvent
    data object SubmitCode : AddEmailEvent
}
