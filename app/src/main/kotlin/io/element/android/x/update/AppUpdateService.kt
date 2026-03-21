/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.x.update

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.androidutils.json.JsonProvider
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.di.annotations.ApplicationContext
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.util.Locale

interface AppUpdateService {
    suspend fun checkForUpdate(): Result<AppUpdateInfo?>
    suspend fun getDownloadedUpdateApk(updateInfo: AppUpdateInfo): Result<File?>
    suspend fun downloadUpdateApk(
        updateInfo: AppUpdateInfo,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    ): Result<File>
}

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DefaultAppUpdateService(
    @ApplicationContext private val context: Context,
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
                apkUrl = manifest.resolvedApkUrl(),
            )
        }.onFailure { failure ->
            Timber.w(failure, "Failed to check the remote update manifest")
        }
    }

    override suspend fun getDownloadedUpdateApk(updateInfo: AppUpdateInfo): Result<File?> = withContext(coroutineDispatchers.io) {
        runCatching {
            findDownloadedUpdateApk(updateInfo)
        }.onFailure { failure ->
            Timber.w(failure, "Failed to resolve the cached update APK")
        }
    }

    override suspend fun downloadUpdateApk(
        updateInfo: AppUpdateInfo,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): Result<File> = withContext(coroutineDispatchers.io) {
        val targetFile = resolveTargetFile(updateInfo)
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.part")
        runCatching {
            findDownloadedUpdateApk(updateInfo)?.let { cachedApk ->
                onProgress(cachedApk.length(), cachedApk.length())
                return@runCatching cachedApk
            }
            targetFile.parentFile?.mkdirs()
            if (tempFile.exists()) {
                tempFile.delete()
            }
            downloadFile(updateInfo.apkUrl, tempFile, onProgress)
            verifyDownloadedFile(updateInfo, tempFile)
            moveTempFile(tempFile, targetFile)
            targetFile
        }.onFailure { failure ->
            tempFile.delete()
            Timber.w(failure, "Failed to download the remote update APK")
        }
    }

    private fun fetchUpdateManifest(url: String): AppUpdateManifest {
        val manifestJson = executeRequest(url)
        return jsonProvider().decodeFromString(AppUpdateManifest.serializer(), manifestJson)
    }

    private fun downloadFile(
        url: String,
        targetFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ) {
        executeRequest(url) { responseBody ->
            targetFile.outputStream().use { outputStream ->
                responseBody.byteStream().use { inputStream ->
                    val totalBytes = responseBody.contentLength().takeIf { it > 0L }
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloadedBytes = 0L
                    var lastReportedBytes = Long.MIN_VALUE
                    var lastReportedPercent = -1
                    onProgress(0L, totalBytes)
                    while (true) {
                        val bytesRead = inputStream.read(buffer)
                        if (bytesRead <= 0) break
                        outputStream.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (shouldReportProgress(downloadedBytes, totalBytes, lastReportedBytes, lastReportedPercent)) {
                            onProgress(downloadedBytes, totalBytes)
                            lastReportedBytes = downloadedBytes
                            lastReportedPercent = totalBytes
                                ?.takeIf { it > 0L }
                                ?.let { (downloadedBytes * 100 / it).toInt() }
                                ?: -1
                        }
                    }
                    outputStream.flush()
                    if (downloadedBytes != lastReportedBytes) {
                        onProgress(downloadedBytes, totalBytes)
                    }
                }
            }
        }
    }

    private fun executeRequest(url: String): String {
        return executeRequest(url) { responseBody ->
            responseBody.string()
        }
    }

    private fun <T> executeRequest(url: String, block: (okhttp3.ResponseBody) -> T): T {
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
            return block(responseBody)
        }
    }

    private fun buildManifestUrl(): String {
        return APP_UPDATE_BASE_URL + APP_UPDATE_MANIFEST_FILE + "?ts=" + System.currentTimeMillis()
    }

    private fun findDownloadedUpdateApk(updateInfo: AppUpdateInfo): File? {
        val targetFile = resolveTargetFile(updateInfo)
        if (!targetFile.exists()) return null
        if (targetFile.length() <= 0L) {
            targetFile.delete()
            return null
        }
        return if (isValidDownloadedFile(updateInfo, targetFile)) {
            targetFile
        } else {
            targetFile.delete()
            null
        }
    }

    private fun resolveTargetFile(updateInfo: AppUpdateInfo): File {
        return File(File(context.cacheDir, "app-update"), updateInfo.cachedApkFileName())
    }

    private fun verifyDownloadedFile(updateInfo: AppUpdateInfo, file: File) {
        check(file.length() > 0L) { "Downloaded APK is empty" }
        check(isValidDownloadedFile(updateInfo, file)) { "Downloaded APK checksum mismatch" }
    }

    private fun isValidDownloadedFile(updateInfo: AppUpdateInfo, file: File): Boolean {
        val expectedSha256 = updateInfo.manifest.normalizedSha256() ?: return true
        val actualSha256 = file.sha256()
        return actualSha256.equals(expectedSha256, ignoreCase = true)
    }

    private fun moveTempFile(tempFile: File, targetFile: File) {
        if (targetFile.exists() && !targetFile.delete()) {
            error("Could not replace previously downloaded APK at ${targetFile.absolutePath}")
        }
        if (tempFile.renameTo(targetFile)) return
        tempFile.copyTo(targetFile, overwrite = true)
        tempFile.delete()
    }
}

private fun shouldReportProgress(
    downloadedBytes: Long,
    totalBytes: Long?,
    lastReportedBytes: Long,
    lastReportedPercent: Int,
): Boolean {
    val safeTotalBytes = totalBytes?.takeIf { it > 0L }
    if (safeTotalBytes != null) {
        val progressPercent = (downloadedBytes * 100 / safeTotalBytes).toInt()
        return progressPercent != lastReportedPercent
    }
    if (lastReportedBytes == Long.MIN_VALUE) return true
    return downloadedBytes - lastReportedBytes >= UNKNOWN_SIZE_PROGRESS_STEP_BYTES
}

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { inputStream ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val bytesRead = inputStream.read(buffer)
            if (bytesRead <= 0) break
            digest.update(buffer, 0, bytesRead)
        }
    }
    val locale = Locale.ROOT
    return digest.digest().joinToString(separator = "") { "%02x".format(locale, it) }
}

private fun AppUpdateManifest.normalizedSha256(): String? {
    val normalized = sha256?.trim()?.lowercase(Locale.ROOT).orEmpty()
    if (normalized.isEmpty()) return null
    if (normalized == "optional_sha256_of_apk") return null
    return normalized.takeIf { SHA256_REGEX.matches(it) }
}

private val SHA256_REGEX = Regex("[a-f0-9]{64}")
private const val UNKNOWN_SIZE_PROGRESS_STEP_BYTES = 256 * 1024L
