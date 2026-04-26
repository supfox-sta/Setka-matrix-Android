/*
 * Copyright (c) 2026 Element Creations Ltd.
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.setka

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import dev.zacsweers.metro.Inject
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.mediaviewer.api.local.LocalMediaFactory
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLEncoder
import kotlin.random.Random

private const val SETKA_SHARE_LINK_PREFIX = "https://web.setka-matrix.ru/#/setka-pack/"

@Inject
class SetkaService(
    private val matrixClient: MatrixClient,
    @ApplicationContext private val context: Context,
    private val localMediaFactory: LocalMediaFactory,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    suspend fun bootstrap(): Result<SetkaBootstrap> {
        val subscription = refreshSubscription().getOrNull()
        val plans = refreshPlans().getOrDefault(emptyList())
        val packs = refreshStickerPacks().getOrDefault(emptyList())
        return Result.success(
            SetkaBootstrap(
                subscription = subscription,
                plans = plans,
                stickerPacks = packs,
            )
        )
    }

    suspend fun refreshSubscription(): Result<SetkaPlusSubscription?> {
        return requestGet<SubscriptionResponse>(setkaPath("/subscription"))
            .map { response -> response?.toDomain() }
            .recover { null }
    }

    suspend fun refreshPlans(): Result<List<SetkaPlusPlan>> {
        return requestGet<PlansResponse>(setkaPath("/plans"))
            .map { response ->
                response.plans
                    .mapNotNull { it.toDomain() }
                    .sortedWith(compareBy<SetkaPlusPlan> { it.sortOrder }.thenBy { it.priceRub })
            }
            .recover { emptyList() }
    }

    suspend fun refreshStickerPacks(): Result<List<SetkaStickerPack>> {
        return requestGet<PacksResponse>(setkaPath("/sticker_packs"))
            .map { response ->
                response.packs
                    .mapNotNull { it.toDomain() }
                    .sortedBy { it.name.lowercase() }
            }
            .recover { emptyList() }
    }

    suspend fun createStickerPack(
        name: String,
        kind: SetkaPackKind,
    ): Result<SetkaStickerPack> {
        return requestBody<CreatePackBody, StickerPackResponse>(
            method = "POST",
            path = setkaPath("/sticker_packs"),
            body = CreatePackBody(name = name.trim(), kind = kind.raw),
        ).mapCatching { response ->
            response.toDomain() ?: error("Server returned invalid sticker pack")
        }
    }

    suspend fun saveStickerPack(pack: SetkaStickerPack): Result<SetkaStickerPack> {
        val body = SavePackBody(
            name = pack.name.trim(),
            kind = pack.kind.raw,
            stickers = pack.stickers.map { sticker ->
                StickerResponse(
                    id = sticker.id,
                    name = sticker.name,
                    mxcUrl = sticker.mxcUrl,
                    mimeType = sticker.mimeType,
                    width = sticker.width,
                    height = sticker.height,
                    size = sticker.size,
                )
            },
        )
        return requestBody<SavePackBody, StickerPackResponse>(
            method = "PUT",
            path = setkaPath("/sticker_packs/${encodePathSegment(pack.id)}"),
            body = body,
        ).mapCatching { response ->
            response.toDomain() ?: error("Server returned invalid sticker pack")
        }
    }

    suspend fun deleteStickerPack(packId: String): Result<Unit> {
        return requestUnit(
            method = "DELETE",
            path = setkaPath("/sticker_packs/${encodePathSegment(packId)}"),
        )
    }

    suspend fun addStickerToPack(pack: SetkaStickerPack, sticker: SetkaSticker): Result<SetkaStickerPack> {
        return saveStickerPack(
            pack.copy(
                stickers = pack.stickers + sticker,
            )
        )
    }

    suspend fun removeStickerFromPack(pack: SetkaStickerPack, stickerId: String): Result<SetkaStickerPack> {
        return saveStickerPack(
            pack.copy(
                stickers = pack.stickers.filterNot { it.id == stickerId },
            )
        )
    }

    suspend fun uploadPackMedia(uri: Uri, kind: SetkaPackKind): Result<SetkaSticker> {
        return runCatching {
            val localMedia = localMediaFactory.createFromUri(
                uri = uri,
                mimeType = null,
                name = null,
                formattedFileSize = null,
            )
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Unable to read media")
            val uploadedUrl = matrixClient.uploadMedia(localMedia.info.mimeType, bytes).getOrThrow()
            val dimensions = readImageDimensions(uri)
            SetkaSticker(
                id = "sticker-${System.currentTimeMillis()}-${Random.nextInt(1000, 9999)}",
                name = localMedia.info.filename.substringBeforeLast(".").ifBlank { "Sticker" },
                mxcUrl = uploadedUrl,
                mimeType = localMedia.info.mimeType,
                width = if (kind == SetkaPackKind.EMOJI) 50 else dimensions.first,
                height = if (kind == SetkaPackKind.EMOJI) 50 else dimensions.second,
                size = localMedia.info.fileSize,
            )
        }
    }

    suspend fun createPlusPayment(plan: SetkaPlusPlan): Result<SetkaPlusPaymentRequest> {
        return requestBody<CreatePaymentBody, PaymentResponse>(
            method = "POST",
            path = setkaPath("/payments/yoomoney/create"),
            body = CreatePaymentBody(
                amount = plan.priceRub,
                description = plan.name,
                planId = plan.id,
            )
        ).map { response -> response.toDomain() }
    }

    suspend fun createPackShareLink(packId: String): Result<String> {
        return requestNoBody<SharePackResponse>(
            method = "POST",
            path = setkaPath("/sticker_packs/${encodePathSegment(packId)}/share"),
        ).mapCatching { response ->
            response.url.takeIf { it.isNotBlank() } ?: error("Share URL is empty")
        }
    }

    suspend fun resolveSharedPack(token: String): Result<SetkaStickerPack> {
        return requestGet<SharedPackResponse>(
            setkaPath("/shared_packs/${encodePathSegment(token)}")
        ).mapCatching { response ->
            response.pack.toDomain() ?: error("Server returned invalid pack")
        }
    }

    suspend fun importSharedPack(token: String): Result<SetkaStickerPack> {
        return requestBody<EmptyBody, StickerPackResponse>(
            method = "POST",
            path = setkaPath("/shared_packs/${encodePathSegment(token)}/import"),
            body = EmptyBody,
        ).mapCatching { response ->
            response.toDomain() ?: error("Server returned invalid pack")
        }
    }

    suspend fun sendSticker(
        timeline: Timeline,
        sticker: SetkaSticker,
    ): Result<Unit> {
        val width = sticker.width ?: 200
        val height = sticker.height ?: 200
        val content = buildJsonObject {
            put("body", sticker.name)
            put("url", sticker.mxcUrl)
            put(
                "info",
                buildJsonObject {
                    sticker.mimeType?.let { put("mimetype", it) }
                    put("w", width)
                    put("h", height)
                    sticker.size?.let { put("size", it) }
                }
            )
        }
        return timeline.sendRaw(
            eventType = "m.sticker",
            content = content.toString(),
        )
    }

    fun buildPackShareLink(pack: SetkaStickerPack): String {
        val payload = SharePackPayload(
            v = 1,
            name = pack.name,
            kind = pack.kind.raw,
            stickers = pack.stickers.mapIndexed { index, sticker ->
                ShareStickerPayload(
                    id = sticker.id.ifBlank { "shared-${System.currentTimeMillis()}-$index" },
                    name = sticker.name,
                    mxcUrl = sticker.mxcUrl,
                    mimeType = sticker.mimeType,
                    width = sticker.width,
                    height = sticker.height,
                    size = sticker.size,
                )
            }
        )
        val encoded = Base64.encodeToString(
            json.encodeToString(payload).encodeToByteArray(),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        return "$SETKA_SHARE_LINK_PREFIX$encoded"
    }

    fun applyCustomEmojiFormatting(
        body: String,
        htmlBody: String?,
        packs: List<SetkaStickerPack>,
    ): Pair<String, String?> {
        // If the rich editor already produced proper custom emoji HTML, do not try to replace tokens.
        // Naively replacing ':token:' in raw HTML would also affect attribute values like alt=":token:".
        if (htmlBody?.contains("data-mx-emoticon") == true) return body to htmlBody

        val customEmojiEntries = packs
            .asSequence()
            .filter { it.kind == SetkaPackKind.EMOJI }
            .flatMap { pack -> pack.stickers.asSequence() }
            .mapNotNull { sticker ->
                val token = sticker.token()
                if (token == null) null else token to sticker
            }
            .distinctBy { it.first }
            .sortedByDescending { it.first.length }
            .toList()
        if (customEmojiEntries.isEmpty()) return body to htmlBody

        val matchingEntries = customEmojiEntries.filter { (token, _) ->
            body.contains(token) || htmlBody?.contains(token) == true
        }
        if (matchingEntries.isEmpty()) return body to htmlBody

        var formattedHtml = htmlBody ?: body.toBasicHtml()
        matchingEntries.forEach { (token, sticker) ->
            formattedHtml = formattedHtml.replace(token, sticker.toInlineEmojiHtml(token))
        }
        return body to formattedHtml
    }

    fun containsCustomEmoji(body: String, packs: List<SetkaStickerPack>): Boolean {
        if (body.isBlank()) return false
        val emojiTokens = packs
            .asSequence()
            .filter { it.kind == SetkaPackKind.EMOJI }
            .flatMap { it.stickers.asSequence() }
            .map { it.inlineEmojiToken() }
            .distinct()
            .toList()
        if (emojiTokens.isEmpty()) return false
        return emojiTokens.any { token -> body.contains(token) }
    }

    fun customEmojiFallbackBodyFromHtml(htmlBody: String?): String? {
        if (htmlBody.isNullOrBlank()) return null
        if (!htmlBody.contains("data-mx-emoticon")) return null
        // Extract alt=":token:" attributes from data-mx-emoticon images.
        val matches = Regex("""<img[^>]*data-mx-emoticon[^>]*alt="([^"]+)"[^>]*>""")
            .findAll(htmlBody)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .toList()
        if (matches.isEmpty()) return null
        return matches.joinToString(separator = " ")
    }

    private suspend inline fun <reified T> requestGet(path: String): Result<T> {
        return matrixClient.executeAuthenticatedRequest(
            method = "GET",
            path = clientV3Path(path),
        ).mapCatching { bytes ->
            json.decodeFromString<T>(bytes.decodeToString())
        }
    }

    private suspend inline fun <reified T> requestNoBody(
        method: String,
        path: String,
    ): Result<T> {
        return matrixClient.executeAuthenticatedRequest(
            method = method,
            path = clientV3Path(path),
        ).mapCatching { bytes ->
            json.decodeFromString<T>(bytes.decodeToString())
        }
    }

    private suspend inline fun <reified B, reified T> requestBody(
        method: String,
        path: String,
        body: B,
    ): Result<T> {
        return matrixClient.executeAuthenticatedRequest(
            method = method,
            path = clientV3Path(path),
            body = json.encodeToString(body).encodeToByteArray(),
        ).mapCatching { bytes ->
            json.decodeFromString<T>(bytes.decodeToString())
        }
    }

    private suspend fun requestUnit(
        method: String,
        path: String,
    ): Result<Unit> {
        return matrixClient.executeAuthenticatedRequest(
            method = method,
            path = clientV3Path(path),
        ).map { Unit }
    }

    private fun clientV3Path(path: String): String {
        return "/_matrix/client/v3$path"
    }

    private fun setkaPath(suffix: String): String {
        return "/user/${encodePathSegment(matrixClient.sessionId.value)}/setka_plus$suffix"
    }

    private fun encodePathSegment(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name())
    }

    private fun readImageDimensions(uri: Uri): Pair<Int?, Int?> {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, options)
        }
        val width = options.outWidth.takeIf { it > 0 }
        val height = options.outHeight.takeIf { it > 0 }
        return width to height
    }
}

private fun SetkaSticker.token(): String? {
    return inlineEmojiToken()
}

private fun SetkaSticker.toInlineEmojiHtml(token: String): String {
    val width = (width ?: 50).coerceAtLeast(20)
    val height = (height ?: 50).coerceAtLeast(20)
    val escapedName = name.escapeHtml()
    return buildString {
        append("<img data-mx-emoticon")
        append(" src=\"")
        append(mxcUrl.escapeHtml())
        append("\" alt=\"")
        append(token.escapeHtml())
        append("\" title=\"")
        append(escapedName)
        append("\" width=\"")
        append(width)
        append("\" height=\"")
        append(height)
        append("\" />")
    }
}

private fun String.toBasicHtml(): String {
    return escapeHtml()
        .replace("\r\n", "\n")
        .replace("\n", "<br>")
}

private fun String.escapeHtml(): String {
    return buildString(length) {
        for (char in this@escapeHtml) {
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(char)
            }
        }
    }
}

@Serializable
private data class SubscriptionResponse(
    val tier: String = "setka_plus",
    @SerialName("plan_name") val planName: String? = null,
    val status: String = "inactive",
    @SerialName("is_active") val isActive: Boolean = false,
    @SerialName("started_at") val startedAt: Long = 0,
    @SerialName("expires_at") val expiresAt: Long = 0,
    @SerialName("updated_at") val updatedAt: Long = 0,
    @SerialName("price_rub") val amountRub: Double? = null,
    @SerialName("duration_days") val durationDays: Int? = null,
) {
    fun toDomain(): SetkaPlusSubscription {
        return SetkaPlusSubscription(
            tier = tier,
            planName = planName,
            status = status,
            isActive = isActive,
            startedAt = startedAt,
            expiresAt = expiresAt,
            updatedAt = updatedAt,
            amountRub = amountRub,
            durationDays = durationDays,
        )
    }
}

@Serializable
private data class PlansResponse(
    val plans: List<PlanResponse> = emptyList(),
)

@Serializable
private data class PlanResponse(
    val id: String = "",
    val name: String = "",
    @SerialName("price_rub") val priceRub: Double = 0.0,
    @SerialName("duration_days") val durationDays: Int = 0,
    val features: List<String> = emptyList(),
    val active: Boolean = true,
    @SerialName("is_default") val isDefault: Boolean = false,
    @SerialName("sort_order") val sortOrder: Int = 0,
) {
    fun toDomain(): SetkaPlusPlan? {
        if (id.isBlank() || name.isBlank() || priceRub <= 0.0 || durationDays <= 0) return null
        return SetkaPlusPlan(
            id = id,
            name = name,
            priceRub = priceRub,
            durationDays = durationDays,
            features = features.filter { it.isNotBlank() },
            active = active,
            isDefault = isDefault,
            sortOrder = sortOrder,
        )
    }
}

@Serializable
private data class PacksResponse(
    val packs: List<StickerPackResponse> = emptyList(),
)

@Serializable
private data class SharePackResponse(
    val token: String = "",
    val url: String = "",
)

@Serializable
private data class SharedPackResponse(
    val pack: StickerPackResponse = StickerPackResponse(),
)

@Serializable
private object EmptyBody

@Serializable
private data class StickerPackResponse(
    val id: String = "",
    val name: String = "",
    val kind: String? = null,
    val stickers: List<StickerResponse> = emptyList(),
    @SerialName("created_at") val createdAt: Long? = null,
    @SerialName("updated_at") val updatedAt: Long? = null,
) {
    fun toDomain(): SetkaStickerPack? {
        if (id.isBlank() || name.isBlank()) return null
        return SetkaStickerPack(
            id = id,
            name = name,
            kind = SetkaPackKind.fromRaw(kind),
            stickers = stickers.mapNotNull { it.toDomain() },
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}

@Serializable
private data class StickerResponse(
    val id: String = "",
    val name: String = "",
    @SerialName("mxc_url") val mxcUrl: String = "",
    @SerialName("mime_type") val mimeType: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val size: Long? = null,
) {
    fun toDomain(): SetkaSticker? {
        if (id.isBlank() || name.isBlank() || !mxcUrl.startsWith("mxc://")) return null
        return SetkaSticker(
            id = id,
            name = name,
            mxcUrl = mxcUrl,
            mimeType = mimeType,
            width = width,
            height = height,
            size = size,
        )
    }
}

@Serializable
private data class CreatePackBody(
    val name: String,
    val kind: String,
)

@Serializable
private data class SavePackBody(
    val name: String,
    val kind: String,
    val stickers: List<StickerResponse>,
)

@Serializable
private data class CreatePaymentBody(
    val amount: Double,
    val description: String,
    @SerialName("plan_id") val planId: String,
)

@Serializable
private data class PaymentResponse(
    @SerialName("payment_id") val paymentId: String = "",
    val provider: String = "",
    val status: String = "",
    val amount: Double = 0.0,
    val currency: String = "",
    val label: String = "",
    @SerialName("checkout_url") val checkoutUrl: String? = null,
) {
    fun toDomain(): SetkaPlusPaymentRequest {
        return SetkaPlusPaymentRequest(
            paymentId = paymentId,
            provider = provider,
            status = status,
            amount = amount,
            currency = currency,
            label = label,
            checkoutUrl = checkoutUrl,
        )
    }
}

@Serializable
private data class SharePackPayload(
    val v: Int,
    val name: String,
    val kind: String,
    val stickers: List<ShareStickerPayload>,
)

@Serializable
private data class ShareStickerPayload(
    val id: String,
    val name: String,
    @SerialName("mxc_url") val mxcUrl: String,
    @SerialName("mime_type") val mimeType: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val size: Long? = null,
)
