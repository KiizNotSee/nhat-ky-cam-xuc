package com.diaryapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.diaryapp.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Snackbar.make(binding.root, R.string.camera_permission, Snackbar.LENGTH_LONG)
                .setAction("Settings") {
                    // Open app settings
                    val intent = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    val uri = android.net.Uri.fromParts("package", packageName, null)
                    startActivity(android.content.Intent(intent).apply { data = uri })
                }.show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Silently handle — not critical */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupEdgeToEdge()
        setupWebView()
        requestCameraPermission()
        loadContent()
    }

    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Light status bar icons for light theme
        if (!isDarkTheme()) {
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true
        }
    }

    private fun isDarkTheme(): Boolean {
        return when (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) {
            android.content.res.Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
    }

    private fun setupWebView() {
        val webView = binding.webView
        webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.setGeolocationEnabled(false)
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.mediaPlaybackRequiresUserGesture = false
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

            // Improve rendering
            settings.setRenderPriority(WebSettings.RenderPriority.HIGH)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                settings.safeBrowsingEnabled = false
            }

            // Dark mode support
            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                WebSettingsCompat.setForceDark(this@apply,
                    if (isDarkTheme()) WebSettingsCompat.FORCE_DARK_ON
                    else WebSettingsCompat.FORCE_DARK_OFF
                )
            }

            // Enable hardware acceleration for smooth animations
            setLayerType(View.LAYER_TYPE_HARDWARE, null)

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    binding.loadingOverlay.visibility = View.VISIBLE
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    binding.loadingOverlay.visibility = View.GONE
                    // Inject post-load fixes
                    injectAppearanceFixes(view)
                }
            }

            // Intercept notifications API requests
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.let {
                        if (it.origin.toString().contains("file://")) {
                            it.grant(it.resources)
                        }
                    }
                }
            }

            // Set transparent background to match theme
            setBackgroundColor(if (isDarkTheme()) 0xFF1c1b2e.toInt() else 0xFFF4F1FA.toInt())
        }
    }

    private fun injectAppearanceFixes(webView: WebView?) {
        webView?.let {
            val js = """
                (function() {
                    // Match system dark mode
                    var isDark = '${isDarkTheme()}' === 'true';
                    if (isDark) {
                        document.getElementById('phone').dataset.theme = 'dark';
                    }
                    // Fix viewport for mobile display
                    var meta = document.querySelector('meta[name="viewport"]');
                    if (meta) {
                        meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';
                    }
                    // Hide loading if any
                    document.getElementById('app')?.classList?.remove('hidden');
                })();
            """.trimIndent()
            it.evaluateJavascript(js, null)
        }
    }

    private fun requestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED -> { /* already granted */ }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                Snackbar.make(binding.root, R.string.camera_permission, Snackbar.LENGTH_INDEFINITE)
                    .setAction("Grant") {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }.show()
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun loadContent() {
        // Copy the HTML asset and load it
        binding.webView.loadUrl("file:///android_asset/nhat-ky-cam-xuc-clay.html")
    }

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
