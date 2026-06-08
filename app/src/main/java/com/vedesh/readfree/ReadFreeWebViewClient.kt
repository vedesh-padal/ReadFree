package com.vedesh.readfree

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * A named WebViewClient for the ReadFree reader.
 *
 * Instead of coupling directly to MainActivity's binding or private methods,
 * it delegates all significant events to a [Listener] interface. This makes
 * the client independently testable and swappable.
 *
 * @param listener Receives page lifecycle and error events.
 */
class ReadFreeWebViewClient(
    private val listener: Listener,
) : WebViewClient() {
    interface Listener {
        fun onPageStarted()

        fun onPageFinished(url: String?)

        fun onMainFrameError()

        fun onMainFrameHttpError(statusCode: Int)

        fun onSslError(handler: SslErrorHandler?)
    }

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?,
    ): Boolean {
        // All navigation stays inside the WebView.
        // The bottom bar "Open in Browser" button is the only way to open externally.
        return false
    }

    override fun onPageStarted(
        view: WebView?,
        url: String?,
        favicon: Bitmap?,
    ) {
        super.onPageStarted(view, url, favicon)
        listener.onPageStarted()
    }

    override fun onPageFinished(
        view: WebView?,
        url: String?,
    ) {
        super.onPageFinished(view, url)
        listener.onPageFinished(url)
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?,
    ) {
        super.onReceivedError(view, request, error)
        // Ignore sub-resource errors (images, scripts) — only react to main-frame failures
        if (request?.isForMainFrame == true) listener.onMainFrameError()
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?,
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        val statusCode = errorResponse?.statusCode ?: return
        // Only failover on server-side errors (5xx); 4xx are not transient mirror failures
        if (request?.isForMainFrame == true && statusCode >= 500) {
            listener.onMainFrameHttpError(statusCode)
        }
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?,
    ): WebResourceResponse? {
        // cid: URLs are used inside .mht files; intercept to prevent crash
        if (request?.url?.scheme == "cid") {
            return WebResourceResponse("text/plain", "UTF-8", null)
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun onReceivedSslError(
        view: WebView?,
        handler: SslErrorHandler?,
        error: SslError?,
    ) {
        listener.onSslError(handler)
    }
}
