package com.tixik.chessbot

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var web: WebView
    private var proc: Process? = null
    private var out: OutputStreamWriter? = null
    private val io = Executors.newSingleThreadExecutor()

    inner class Bridge {
        @JavascriptInterface
        fun send(line: String) {
            io.execute {
                try {
                    out?.write(line + "\n")
                    out?.flush()
                } catch (_: Exception) {
                }
            }
        }

        @JavascriptInterface
        fun threads(): Int = Runtime.getRuntime().availableProcessors()

        @JavascriptInterface
        fun memMb(): Int {
            val mi = ActivityManager.MemoryInfo()
            (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
            return (mi.totalMem / 1048576L).toInt()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val resp = loader.shouldInterceptRequest(request.url)
                return if (resp != null && request.url.path?.endsWith(".wasm") == true)
                    WebResourceResponse("application/wasm", null, resp.data)
                else resp
            }
        }
        web.addJavascriptInterface(Bridge(), "Android")
        startEngine()
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    private fun startEngine() {
        try {
            val exe = applicationInfo.nativeLibraryDir + "/libfairystockfish.so"
            val p = ProcessBuilder(exe).redirectErrorStream(true).start()
            proc = p
            out = OutputStreamWriter(p.outputStream)
            Thread {
                BufferedReader(InputStreamReader(p.inputStream)).forEachLine { l ->
                    if (l.startsWith("bestmove")) {
                        runOnUiThread {
                            web.evaluateJavascript("window.onEngine&&onEngine(" + JSONObject.quote(l) + ")", null)
                        }
                    }
                }
            }.start()
        } catch (e: Exception) {
            Toast.makeText(this, "Движок не запустился: " + e.message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        proc?.destroy()
        io.shutdownNow()
        super.onDestroy()
    }
}
