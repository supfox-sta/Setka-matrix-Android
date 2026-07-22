/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.x.update

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.androidutils.json.JsonProvider
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.meta.BuildMeta
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

interface AppUpdateService {
    suspend fun checkForUpdate(): Result<AppUpdateInfo?>
}

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DefaultAppUpdateService(
    private val buildMeta: BuildMeta,
    private val coroutineDispatchers: CoroutineDispatchers,
    private val jsonProvider: JsonProvider,
    private val okHttpClient: OkHttpClient,
) : AppUpdateService {
    override suspend fun checkForUpdate(): Result<AppUpdateInfo?> = withContext(coroutineDispatchers.io) {
        runCatching {
            val manifestUrl = buildManifestUrl()
            val manifest = fetchUpdateManifest(manifestUrl)
            if (!manifest.shouldOfferUpdate(buildMeta.versionCode, buildMeta.versionName)) {
                return@runCatching null
            }
            AppUpdateInfo(
                manifest = manifest,
                downloadUrl = manifest.resolvedDownloadUrl(),
            )
        }.onFailure { failure ->
            Timber.w(failure, "Failed to check the remote update manifest")
        }
    }

    private fun fetchUpdateManifest(url: String): AppUpdateManifest {
        val manifestJson = executeRequest(url)
        return jsonProvider().decodeFromString(AppUpdateManifest.serializer(), manifestJson)
    }

    private fun executeRequest(url: String): String {
        val request = Request.Builder()
            .url(url)
            .cacheControl(CacheControl.Builder().noCache().noStore().build())
            .header("Cache-Control", "no-cache, no-store")
            .header("Pragma", "no-cache")
            .get()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Request failed with code ${response.code} for $url" }
            val responseBody = checkNotNull(response.body) { "Empty response body for $url" }
            return responseBody.string()
        }
    }

    private fun buildManifestUrl(): String {
        return APP_UPDATE_BASE_URL + APP_UPDATE_MANIFEST_FILE + "?ts=" + System.currentTimeMillis()
    }
}
