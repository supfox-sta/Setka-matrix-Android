/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.utils

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.os.SystemClock
import androidx.core.net.toUri
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.element.android.features.call.impl.BuildConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import timber.log.Timber
import java.io.ByteArrayInputStream
import org.json.JSONObject

class WebViewWidgetMessageInterceptor(
    private val webView: WebView,
    private val onUrlLoaded: (String) -> Unit,
    private val onError: (String?) -> Unit,
) : WidgetMessageInterceptor {
    companion object {
        // We call both the WebMessageListener and the JavascriptInterface objects in JS with this
        // 'listenerName' so they can both receive the data from the WebView when
        // `${LISTENER_NAME}.postMessage(...)` is called
        const val LISTENER_NAME = "elementX"
    }

    // Keep widget protocol messages lossless. Dropping responses causes call widget state desync.
    private val messageChannel = Channel<String>(capacity = Channel.UNLIMITED)
    override val interceptedMessages: Flow<String> = messageChannel.receiveAsFlow()

    // Deduplicate bridge retries/listener duplicates while preserving request/response pairs.
    // Key shape: "<api>|<responseFlag>|<requestId>"
    private val seenBridgeMessageKeys = LinkedHashMap<String, Long>(512, 0.75f, true)
    private val seenBridgeMessageKeysLock = Any()

    init {
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(webView.context))
            .build()

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)

                // Due to https://github.com/element-hq/element-x-android/issues/4097
                // we need to supply a logging implementation that correctly includes
                // objects in log lines.
                view.evaluateJavascript(
                    """
                        function logFn(consoleLogFn, ...args) {
                            consoleLogFn(
                                args.map(
                                    a => typeof a === "string" ? a : JSON.stringify(a)
                                ).join(' ')
                            );
                        };
                        globalThis.console.debug = logFn.bind(null, console.debug);
                        globalThis.console.log = logFn.bind(null, console.log);
                        globalThis.console.info = logFn.bind(null, console.info);
                        globalThis.console.warn = logFn.bind(null, console.warn);
                        globalThis.console.error = logFn.bind(null, console.error);
                    """.trimIndent(),
                    null
                )

                // We inject this JS code when the page starts loading to attach a message listener to the window.
                // This listener will receive both messages:
                // - EC widget API -> Element X (message.data.api == "fromWidget")
                // - Element X -> EC widget API (message.data.api == "toWidget"), we should ignore these
                view.evaluateJavascript(
                    """
                        (() => {
                            if (window.__elementXMessageBridgeInstalled) return;
                            window.__elementXMessageBridgeInstalled = true;
                            window.addEventListener('message', function(event) {
                                const message = { data: event.data, origin: event.origin };
                                const api = message?.data?.api;
                                const isResponse = Boolean(message?.data?.response);
                                // Forward:
                                // - requests from widget -> app ("fromWidget")
                                // - responses to app -> widget requests ("toWidget" + response=true)
                                // Ignore everything else to avoid feedback loops.
                                if (api === "fromWidget" || (api === "toWidget" && isResponse)) {
                                    let json = JSON.stringify(event.data);
                                    ${"console.log('message sent: ' + json);".takeIf { BuildConfig.DEBUG }}
                                    $LISTENER_NAME.postMessage(json);
                                } else {
                                    ${"console.log('message received (ignored): ' + JSON.stringify(event.data));".takeIf { BuildConfig.DEBUG }}
                                }
                            });
                        })();
                    """.trimIndent(),
                    null
                )
            }

            override fun onPageFinished(view: WebView, url: String) {
                onUrlLoaded(url)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame != true) {
                    super.onReceivedError(view, request, error)
                    return
                }
                // No network for instance, transmit the error
                Timber.e("onReceivedError error: ${error?.errorCode} ${error?.description}")

                // Only propagate the error if it happens while loading the current page
                if (view?.url == request?.url.toString()) {
                    onError(error?.description.toString())
                }

                super.onReceivedError(view, request, error)
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                if (request?.isForMainFrame != true) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    return
                }
                Timber.e("onReceivedHttpError error: ${errorResponse?.statusCode} ${errorResponse?.reasonPhrase}")

                // Only propagate the error if it happens while loading the current page
                if (view?.url == request?.url.toString()) {
                    onError(errorResponse?.statusCode.toString())
                }

                super.onReceivedHttpError(view, request, errorResponse)
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                Timber.e("onReceivedSslError error: ${error?.primaryError}")

                // Only propagate the error if it happens while loading the current page
                if (view?.url == error?.url.toString()) {
                    onError(error?.toString())
                }

                super.onReceivedSslError(view, handler, error)
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
                if (request.url.lastPathSegment == "favicon.ico") {
                    return emptyIconResponse()
                }
                return assetLoader.shouldInterceptRequest(request.url)
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun shouldInterceptRequest(view: WebView?, url: String): WebResourceResponse? {
                val uri = url.toUri()
                if (uri.lastPathSegment == "favicon.ico") {
                    return emptyIconResponse()
                }
                return assetLoader.shouldInterceptRequest(url.toUri())
            }
        }

        // Create a WebMessageListener, which will receive messages from the WebView and reply to them
        val webMessageListener = WebViewCompat.WebMessageListener { _, message, _, _, _ ->
            onMessageReceived(message.data)
        }

        // Use WebMessageListener if supported, otherwise use JavascriptInterface
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(
                webView,
                LISTENER_NAME,
                setOf("*"),
                webMessageListener
            )
        } else {
            webView.addJavascriptInterface(object {
                @JavascriptInterface
                fun postMessage(json: String?) {
                    onMessageReceived(json)
                }
            }, LISTENER_NAME)
        }
    }

    override fun sendMessage(message: String) {
        webView.evaluateJavascript("window.postMessage($message, '*')", null)
    }

    private fun onMessageReceived(json: String?) {
        val payload = json ?: return
        if (!shouldForward(payload)) return
        val result = messageChannel.trySend(payload)
        if (result.isFailure) {
            Timber.w("Failed to enqueue widget message for Matrix driver")
        }
    }

    private fun shouldForward(json: String): Boolean {
        val key = runCatching {
            val jsonObject = JSONObject(json)
            val requestId = jsonObject.optString("requestId").takeIf { it.isNotBlank() } ?: return true
            val api = jsonObject.optString("api").takeIf { it.isNotBlank() } ?: return true
            val isResponse = jsonObject.optBoolean("response", false)
            "$api|$isResponse|$requestId"
        }.getOrElse {
            // If parsing fails we should not block the message; let the matrix driver decide.
            return true
        }
        synchronized(seenBridgeMessageKeysLock) {
            if (seenBridgeMessageKeys.containsKey(key)) {
                Timber.d("Dropping duplicate widget bridge message: $key")
                return false
            }
            seenBridgeMessageKeys[key] = SystemClock.elapsedRealtime()
            while (seenBridgeMessageKeys.size > 1024) {
                val oldestKey = seenBridgeMessageKeys.entries.firstOrNull()?.key ?: break
                seenBridgeMessageKeys.remove(oldestKey)
            }
        }
        return true
    }

    private fun emptyIconResponse(): WebResourceResponse {
        return WebResourceResponse(
            "image/x-icon",
            "utf-8",
            200,
            "OK",
            mapOf("Cache-Control" to "public, max-age=86400"),
            ByteArrayInputStream(byteArrayOf()),
        )
    }
}
