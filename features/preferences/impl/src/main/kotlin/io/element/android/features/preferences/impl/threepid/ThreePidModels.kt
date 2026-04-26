package io.element.android.features.preferences.impl.threepid

data class ThreePid(
    val medium: ThreePidMedium,
    val address: String,
    val addedAtMillis: Long?,
    val validatedAtMillis: Long?,
)

enum class ThreePidMedium(val raw: String) {
    Email("email"),
    Msisdn("msisdn"),
    ;

    companion object {
        fun fromRaw(value: String?): ThreePidMedium? = when (value) {
            Email.raw -> Email
            Msisdn.raw -> Msisdn
            else -> null
        }
    }
}

data class ThreePidRequestTokenResult(
    val sid: String,
    val submitUrl: String? = null,
    val msisdn: String? = null,
)

data class ThreePidUiaSession(
    val session: String,
)

data class SetkaPrivacy(
    val discoverableEmail: Boolean,
    val discoverableMsisdn: Boolean,
    val emailVisibility: SetkaVisibility,
    val phoneVisibility: SetkaVisibility,
    val lastSeenVisibility: SetkaVisibility,
)

enum class SetkaVisibility(val raw: String) {
    Everyone("everyone"),
    Contacts("contacts"),
    Nobody("nobody");

    companion object {
        fun fromRaw(value: String?): SetkaVisibility = entries.firstOrNull { it.raw == value } ?: Everyone
    }
}
