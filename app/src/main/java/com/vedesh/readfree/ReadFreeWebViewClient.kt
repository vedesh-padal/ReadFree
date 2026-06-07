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
 * @param mirrors Used to determine which URLs should stay inside the WebView.
 * @param listener Receives page lifecycle and error events.
 */
class ReadFreeWebViewClient(
    private val mirrors: MirrorRepository,
    private val listener: Listener
) : WebViewClient() {

    interface Listener {
        fun onPageStarted()
        fun onPageFinished(url: String?)
        fun onMainFrameError()
        fun onMainFrameHttpError(statusCode: Int)
        fun onSslError(handler: SslErrorHandler?)
        fun onExternalUrlRequested(url: String)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        // Mirror URLs and known Medium domains stay inside the WebView
        val isMirror = url.startsWith(mirrors.getActiveMirror()) || url.startsWith(MirrorRepository.DEFAULT_MIRROR)
        return if (isMirror || UrlUtils.isMediumDomain(url)) {
            false
        } else {
            listener.onExternalUrlRequested(url)
            true
        }
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        listener.onPageStarted()
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        listener.onPageFinished(url)
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
        // Ignore sub-resource errors (images, scripts) — only react to main-frame failures
        if (request?.isForMainFrame == true) listener.onMainFrameError()
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        val statusCode = errorResponse?.statusCode ?: return
        // Only failover on server-side errors (5xx); 4xx are not transient mirror failures
        if (request?.isForMainFrame == true && statusCode >= 500) {
            listener.onMainFrameHttpError(statusCode)
        }
    }

    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        listener.onSslError(handler)
    }
}
