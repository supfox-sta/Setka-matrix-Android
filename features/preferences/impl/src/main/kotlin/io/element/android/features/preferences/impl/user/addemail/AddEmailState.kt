package io.element.android.features.preferences.impl.user.addemail

enum class AddEmailStep {
    EnterEmail,
    EnterCode,
}

data class AddEmailState(
    val step: AddEmailStep,
    val email: String,
    val code: String,
    val emailError: String?,
    val codeError: String?,
    val emailShakeToken: Int,
    val codeShakeToken: Int,
    val emailValid: Boolean,
    val codeValid: Boolean,
    val isBusy: Boolean,
    val eventSink: (AddEmailEvent) -> Unit,
)
