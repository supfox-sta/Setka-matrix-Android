/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.appconfig

object SetkaMatrixConfig {
    const val STICKER_PACK_SHARE_PATH: String = "/setka-pack/"
    const val WEB_BASE_URL: String = "https://web.setka-matrix.ru"
    const val SHARE_BASE_URL: String = "https://share.setka-matrix.ru"
    /** Public site; Matrix Client-Server API is on [MATRIX_CLIENT_API_URL]. */
    const val HOME_SERVER_URL: String = "https://setka-matrix.ru"
    const val MATRIX_CLIENT_API_URL: String = "https://matrix.setka-matrix.ru"
    const val MATRIX_SERVER_NAME: String = "matrix.setka-matrix.ru"
    const val STICKER_PACK_SHARE_URL_PREFIX: String = "$MATRIX_CLIENT_API_URL$STICKER_PACK_SHARE_PATH"
    const val LIVEKIT_JWT_URL: String = "https://call.setka-matrix.ru/livekit/jwt"
    const val CALL_BASE_URL: String = "https://call.setka-matrix.ru/"

    /** PayAnyWay (MONETA.Assistant) server callbacks — Pay URL, Check URL, Success/Fail return. */
    const val PAYANYWAY_CALLBACK_BASE_URL: String = "$MATRIX_CLIENT_API_URL/setka/payanyway"

    /** HTTPS return links opened after PayAnyWay checkout. */
    const val PAYANYWAY_RETURN_BASE_URL: String = "$PAYANYWAY_CALLBACK_BASE_URL/return"

    /** Custom-scheme return links used after server-side redirect. */
    const val SETKA_PLUS_PAYMENT_DEEPLINK_PREFIX: String = "elementx://open"

    fun isSetkaHomeserver(serverName: String): Boolean {
        return serverName.equals(MATRIX_SERVER_NAME, ignoreCase = true) ||
            serverName.endsWith("setka-matrix.ru", ignoreCase = true)
    }

    fun buildStickerPackShareUrl(token: String): String =
        "$STICKER_PACK_SHARE_URL_PREFIX$token"

    fun isSetkaPaymentReturnHost(host: String): Boolean {
        return host.equals("matrix.setka-matrix.ru", ignoreCase = true) ||
            host.equals("setka-matrix.ru", ignoreCase = true)
    }

    fun buildPayAnyWaySuccessReturnUrl(userId: String, paymentId: String): String =
        "$PAYANYWAY_RETURN_BASE_URL/success?user_id=${encodeQuery(userId)}&payment_id=${encodeQuery(paymentId)}"

    fun buildPayAnyWayFailReturnUrl(userId: String, paymentId: String): String =
        "$PAYANYWAY_RETURN_BASE_URL/fail?user_id=${encodeQuery(userId)}&payment_id=${encodeQuery(paymentId)}"

    fun buildSetkaPlusPaymentSuccessDeeplink(userId: String, paymentId: String): String =
        "$SETKA_PLUS_PAYMENT_DEEPLINK_PREFIX/${encodePath(userId)}/setka-plus/payment/success?payment_id=${encodeQuery(paymentId)}"

    fun buildSetkaPlusPaymentFailDeeplink(userId: String, paymentId: String): String =
        "$SETKA_PLUS_PAYMENT_DEEPLINK_PREFIX/${encodePath(userId)}/setka-plus/payment/fail?payment_id=${encodeQuery(paymentId)}"

    private fun encodeQuery(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun encodePath(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}
