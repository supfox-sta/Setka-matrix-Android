package io.element.android.features.preferences.impl.user.changephone

enum class ChangePhoneStep {
    EnterPhone,
    EnterCode,
}

data class ChangePhoneNumberState(
    val step: ChangePhoneStep,
    val dialCode: String,
    val phoneNumber: String,
    val code: String,
    val phoneError: String?,
    val codeError: String?,
    val phoneShakeToken: Int,
    val codeShakeToken: Int,
    val phoneValid: Boolean,
    val codeValid: Boolean,
    val isBusy: Boolean,
    val eventSink: (ChangePhoneNumberEvent) -> Unit,
)
