package io.element.android.features.preferences.impl.user.editprofile

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.architecture.runCatchingUpdatingState
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.core.mimetype.MimeTypes
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.mediapickers.api.PickerProvider
import io.element.android.libraries.mediaupload.api.MediaOptimizationConfigProvider
import io.element.android.libraries.mediaupload.api.MediaPreProcessor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.Locale

@AssistedInject
class ProfileCustomizationPresenter(
    @Assisted private val matrixUser: MatrixUser,
    @Assisted private val navigator: ProfileCustomizationNavigator,
    private val matrixClient: MatrixClient,
    private val mediaPickerProvider: PickerProvider,
    private val mediaPreProcessor: MediaPreProcessor,
    private val mediaOptimizationConfigProvider: MediaOptimizationConfigProvider,
) : Presenter<ProfileCustomizationState> {
    @AssistedFactory
    interface Factory {
        fun create(
            matrixUser: MatrixUser,
            navigator: ProfileCustomizationNavigator,
        ): ProfileCustomizationPresenter
    }

    @Composable
    override fun present(): ProfileCustomizationState {
        var profileColorHex by rememberSaveable { mutableStateOf("") }
        var badgeEmojiMxcUrl by rememberSaveable { mutableStateOf<String?>(null) }
        var statusEmojiMxcUrl by rememberSaveable { mutableStateOf<String?>(null) }
        var profileBackgroundUrl by rememberSaveable { mutableStateOf<String?>(null) }
        var loadedSetkaProfile by remember { mutableStateOf<JSONObject?>(null) }

        val setkaProfile by produceState<JSONObject?>(initialValue = null, matrixUser.userId) {
            value = runCatching {
                val encodedUser = Uri.encode(matrixUser.userId.value)
                matrixClient.executeAuthenticatedRequest(
                    method = "GET",
                    path = "/_matrix/client/v3/user/$encodedUser/setka_profile",
                ).getOrThrow().decodeToString().let(::JSONObject)
            }.getOrNull()
        }

        val isSetkaPlusActive by produceState(initialValue = false, matrixUser.userId) {
            value = runCatching {
                val encodedUser = Uri.encode(matrixUser.userId.value)
                val raw = matrixClient.executeAuthenticatedRequest(
                    method = "GET",
                    path = "/_matrix/client/v3/user/$encodedUser/setka_plus/subscription",
                ).getOrThrow().decodeToString()
                val json = JSONObject(raw)
                json.optBoolean("is_active", false) || json.optBoolean("active", false)
            }.getOrDefault(false)
        }

        val badgeEmojiPacks by produceState<ImmutableList<SetkaEmojiPack>>(initialValue = persistentListOf()) {
            value = runCatching {
                val encodedUser = Uri.encode(matrixUser.userId.value)
                val raw = matrixClient.executeAuthenticatedRequest(
                    method = "GET",
                    path = "/_matrix/client/v3/user/$encodedUser/setka_plus/sticker_packs",
                ).getOrThrow().decodeToString()
                parseEmojiPacksFromStickerPacksResponse(JSONObject(raw))
            }.getOrDefault(persistentListOf())
        }

        LaunchedEffect(setkaProfile) {
            val incoming = setkaProfile ?: return@LaunchedEffect
            if (loadedSetkaProfile == null) {
                loadedSetkaProfile = incoming
                profileColorHex = incoming.optString("color", "")
                badgeEmojiMxcUrl = incoming.optString("badge_emoji_mxc", "").takeIf { it.isNotBlank() }
                statusEmojiMxcUrl = incoming.optString("status_emoji_mxc", "").takeIf { it.isNotBlank() }
                profileBackgroundUrl = incoming.optString("background_mxc", "").takeIf { it.isNotBlank() }
            }
        }

        val backgroundImagePicker = mediaPickerProvider.registerGalleryImagePicker(
            onResult = { uri ->
                if (uri != null) {
                    profileBackgroundUrl = uri.toString()
                }
            }
        )

        val saveAction: MutableState<AsyncAction<Unit>> = remember { mutableStateOf(AsyncAction.Uninitialized) }
        val localCoroutineScope = rememberCoroutineScope()

        val canSave = remember(
            profileColorHex,
            badgeEmojiMxcUrl,
            statusEmojiMxcUrl,
            profileBackgroundUrl,
            loadedSetkaProfile,
        ) {
            hasCustomizationChanged(
                loaded = loadedSetkaProfile,
                color = profileColorHex,
                badge = badgeEmojiMxcUrl,
                statusEmoji = statusEmojiMxcUrl,
                background = profileBackgroundUrl,
            )
        }

        fun handleEvent(event: ProfileCustomizationEvent) {
            when (event) {
                ProfileCustomizationEvent.Save -> localCoroutineScope.saveChanges(
                    color = profileColorHex,
                    badgeEmojiMxcUrl = badgeEmojiMxcUrl,
                    statusEmojiMxcUrl = statusEmojiMxcUrl,
                    profileBackgroundUrl = profileBackgroundUrl,
                    loadedSetkaProfile = loadedSetkaProfile,
                    action = saveAction,
                )
                ProfileCustomizationEvent.PickProfileBackground -> {
                    if (isSetkaPlusActive) {
                        backgroundImagePicker.launch()
                    }
                }
                ProfileCustomizationEvent.RemoveProfileBackground -> {
                    profileBackgroundUrl = null
                }
                is ProfileCustomizationEvent.UpdateProfileColorHex -> profileColorHex = event.color
                is ProfileCustomizationEvent.SetBadgeEmoji -> badgeEmojiMxcUrl = event.mxcUrl
                is ProfileCustomizationEvent.SetStatusEmoji -> statusEmojiMxcUrl = event.mxcUrl
                ProfileCustomizationEvent.Exit -> {
                    when (saveAction.value) {
                        is AsyncAction.Confirming -> {
                            saveAction.value = AsyncAction.Uninitialized
                            navigator.close()
                        }
                        AsyncAction.Loading -> navigator.close()
                        is AsyncAction.Failure,
                        is AsyncAction.Success -> Unit
                        AsyncAction.Uninitialized -> {
                            if (canSave) {
                                saveAction.value = AsyncAction.ConfirmingCancellation
                            } else {
                                navigator.close()
                            }
                        }
                    }
                }
                ProfileCustomizationEvent.DiscardChanges -> {
                    saveAction.value = AsyncAction.Uninitialized
                    navigator.close()
                }
                ProfileCustomizationEvent.CloseDialog -> saveAction.value = AsyncAction.Uninitialized
            }
        }

        return ProfileCustomizationState(
            profileColorHex = profileColorHex,
            badgeEmojiMxcUrl = badgeEmojiMxcUrl,
            statusEmojiMxcUrl = statusEmojiMxcUrl,
            profileBackgroundUrl = profileBackgroundUrl,
            isSetkaPlusActive = isSetkaPlusActive,
            badgeEmojiPacks = badgeEmojiPacks,
            saveButtonEnabled = canSave && saveAction.value !is AsyncAction.Loading,
            saveAction = saveAction.value,
            eventSink = ::handleEvent,
        )
    }

    private fun CoroutineScope.saveChanges(
        color: String,
        badgeEmojiMxcUrl: String?,
        statusEmojiMxcUrl: String?,
        profileBackgroundUrl: String?,
        loadedSetkaProfile: JSONObject?,
        action: MutableState<AsyncAction<Unit>>,
    ) = launch {
        suspend {
            updateSetkaProfileIfNeeded(
                loaded = loadedSetkaProfile,
                color = color,
                badgeEmojiMxcUrl = badgeEmojiMxcUrl,
                statusEmojiMxcUrl = statusEmojiMxcUrl,
                backgroundMxcUrl = uploadProfileBackgroundIfNeeded(profileBackgroundUrl).getOrThrow(),
            )?.getOrThrow() ?: Unit
        }.runCatchingUpdatingState(action)
    }

    private suspend fun uploadProfileBackgroundIfNeeded(profileBackgroundUrl: String?): Result<String?> {
        if (profileBackgroundUrl.isNullOrBlank()) return Result.success(null)
        if (profileBackgroundUrl.startsWith("mxc://")) return Result.success(profileBackgroundUrl)
        return runCatchingExceptions {
            val preprocessed = mediaPreProcessor.process(
                uri = profileBackgroundUrl.toUri(),
                mimeType = MimeTypes.Jpeg,
                deleteOriginal = false,
                mediaOptimizationConfig = mediaOptimizationConfigProvider.get(),
            ).getOrThrow()
            matrixClient.uploadMedia(MimeTypes.Jpeg, preprocessed.file.readBytes()).getOrThrow()
        }.onFailure { Timber.e(it, "Unable to upload profile background") }
    }

    private suspend fun updateSetkaProfileIfNeeded(
        loaded: JSONObject?,
        color: String,
        badgeEmojiMxcUrl: String?,
        statusEmojiMxcUrl: String?,
        backgroundMxcUrl: String?,
    ): Result<Unit>? {
        if (loaded == null) return null
        val loadedColor = loaded.optString("color", "")
        val loadedBadge = loaded.optString("badge_emoji_mxc", "").takeIf { it.isNotBlank() }
        val loadedStatusEmoji = loaded.optString("status_emoji_mxc", "").takeIf { it.isNotBlank() }
        val loadedBackground = loaded.optString("background_mxc", "").takeIf { it.isNotBlank() }
        val normalizedColor = color.trim()
        val normalizedBadge = badgeEmojiMxcUrl?.trim().orEmpty()
        val normalizedStatusEmoji = statusEmojiMxcUrl?.trim().orEmpty()
        val normalizedBackground = backgroundMxcUrl?.trim().orEmpty()
        val body = JSONObject()
        var changed = false
        if (loadedColor.trim() != normalizedColor) {
            body.put("color", normalizedColor)
            changed = true
        }
        if ((loadedBadge?.trim() ?: "") != normalizedBadge) {
            body.put("badge_emoji_mxc", normalizedBadge)
            changed = true
        }
        if ((loadedStatusEmoji?.trim() ?: "") != normalizedStatusEmoji) {
            body.put("status_emoji_mxc", normalizedStatusEmoji)
            changed = true
        }
        if ((loadedBackground?.trim() ?: "") != normalizedBackground) {
            body.put("background_mxc", normalizedBackground)
            changed = true
        }
        if (!changed) return null
        val encodedUser = Uri.encode(matrixUser.userId.value)
        return matrixClient.executeAuthenticatedRequest(
            method = "PUT",
            path = "/_matrix/client/v3/user/$encodedUser/setka_profile",
            body = body.toString().encodeToByteArray(),
        ).map { Unit }
    }

    private fun hasCustomizationChanged(
        loaded: JSONObject?,
        color: String,
        badge: String?,
        statusEmoji: String?,
        background: String?,
    ): Boolean {
        if (loaded == null) return false
        val loadedColor = loaded.optString("color", "")
        val loadedBadge = loaded.optString("badge_emoji_mxc", "").takeIf { it.isNotBlank() }
        val loadedStatusEmoji = loaded.optString("status_emoji_mxc", "").takeIf { it.isNotBlank() }
        val loadedBackground = loaded.optString("background_mxc", "").takeIf { it.isNotBlank() }
        return loadedColor.trim() != color.trim() ||
            (loadedBadge?.trim() ?: "") != (badge?.trim() ?: "") ||
            (loadedStatusEmoji?.trim() ?: "") != (statusEmoji?.trim() ?: "") ||
            (loadedBackground?.trim() ?: "") != (background?.trim() ?: "")
    }

    private fun parseEmojiPacksFromStickerPacksResponse(root: JSONObject): ImmutableList<SetkaEmojiPack> {
        val packsJson = root.optJSONArray("packs")
            ?: root.optJSONArray("sticker_packs")
            ?: JSONArray()
        val out = ArrayList<SetkaEmojiPack>()
        for (i in 0 until packsJson.length()) {
            val pack = packsJson.optJSONObject(i) ?: continue
            val kind = pack.optString("kind")
            val id = pack.optString("id").takeIf { it.isNotBlank() } ?: continue
            val name = pack.optString("name").takeIf { it.isNotBlank() } ?: id
            val stickersJson = pack.optJSONArray("stickers") ?: JSONArray()
            val stickers = ArrayList<SetkaEmojiSticker>()
            for (j in 0 until stickersJson.length()) {
                val sticker = stickersJson.optJSONObject(j) ?: continue
                val mxc = sticker.optString("mxc_url").takeIf { it.isNotBlank() }
                    ?: sticker.optString("mxcUrl").takeIf { it.isNotBlank() }
                    ?: continue
                val stickerId = sticker.optString("id").takeIf { it.isNotBlank() } ?: mxc
                val stickerName = sticker.optString("name").takeIf { it.isNotBlank() }
                stickers.add(SetkaEmojiSticker(id = stickerId, name = stickerName, mxcUrl = mxc))
            }
            val isEmojiPack = kind.lowercase(Locale.ROOT) == "emoji" ||
                stickers.any { sticker ->
                    val normalized = sticker.name.orEmpty()
                    normalized.startsWith(":") && normalized.endsWith(":")
                }
            if (!isEmojiPack) continue
            val icon = pack.optString("icon_mxc").takeIf { it.isNotBlank() }
                ?: pack.optString("iconMxcUrl").takeIf { it.isNotBlank() }
                ?: stickers.firstOrNull()?.mxcUrl
            out.add(
                SetkaEmojiPack(
                    id = id,
                    name = name,
                    iconMxcUrl = icon,
                    stickers = stickers.toImmutableList(),
                )
            )
        }
        return out.toImmutableList()
    }
}
