package com.xinghe.screentranslator

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val captureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val service = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_DATA, result.data)
            }
            startForegroundService(service)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleCaptureIntent(intent)
        setContent {
            var status by remember { mutableStateOf("准备就绪。先开启悬浮球，再点击悬浮球翻译。") }
            var translated by remember { mutableStateOf("翻译结果将在这里显示。") }
            var overlayEnabled by remember { mutableStateOf(Settings.canDrawOverlays(this)) }
            DisposableEffect(Unit) {
                val receiver = object : android.content.BroadcastReceiver() {
                    override fun onReceive(context: android.content.Context?, intent: Intent?) {
                        translated = intent?.getStringExtra(ScreenCaptureService.EXTRA_TEXT).orEmpty()
                        status = "本次屏幕识别完成"
                    }
                }
                val filter = android.content.IntentFilter(ScreenCaptureService.ACTION_RESULT)
                if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
                else @Suppress("DEPRECATION") registerReceiver(receiver, filter)
                onDispose { unregisterReceiver(receiver) }
            }
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(22.dp).verticalScroll(rememberScrollState())) {
                        Text("星河屏幕翻译", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("英文屏幕识别 · 简体中文翻译", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = {
                            if (!Settings.canDrawOverlays(this@MainActivity)) {
                                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                                status = "请在系统设置中允许“显示在其他应用上层”，然后返回本应用。"
                            } else {
                                startService(Intent(this@MainActivity, FloatingBubbleService::class.java).setAction(FloatingBubbleService.ACTION_SHOW))
                                overlayEnabled = true
                                status = "悬浮球已开启。点击屏幕上的星河圆球开始翻译。"
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (overlayEnabled) "开启 / 显示悬浮球" else "授权并开启悬浮球")
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = {
                            stopService(Intent(this@MainActivity, FloatingBubbleService::class.java))
                            overlayEnabled = false
                            status = "悬浮球已关闭"
                        }, modifier = Modifier.fillMaxWidth()) { Text("关闭悬浮球") }
                        Spacer(Modifier.height(16.dp))
                        Text(status, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(20.dp))
                        Text("中文翻译结果", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Card(Modifier.fillMaxWidth()) {
                            Text(translated, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                        Spacer(Modifier.height(20.dp))
                        Text("提示：Android 系统要求你每次新建屏幕共享会话时明确授权。首次翻译需要联网下载语言模型。", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCaptureIntent(intent)
    }

    private fun handleCaptureIntent(intent: Intent?) {
        if (intent?.action == ACTION_CAPTURE_REQUEST) {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            captureLauncher.launch(manager.createScreenCaptureIntent())
        }
    }

    companion object {
        const val ACTION_CAPTURE_REQUEST = "com.xinghe.screentranslator.REQUEST_CAPTURE"
    }
}
