package com.xinghe.screentranslator

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
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
        setContent {
            var status by remember { mutableStateOf("准备就绪。点击下方按钮，允许系统共享屏幕。") }
            var translated by remember { mutableStateOf("翻译结果将在这里显示。") }
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
                            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                            captureLauncher.launch(manager.createScreenCaptureIntent())
                            status = "等待系统屏幕共享授权…"
                        }, modifier = Modifier.fillMaxWidth()) { Text("开始翻译当前屏幕") }
                        Spacer(Modifier.height(12.dp))
                        Text(status, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(20.dp))
                        Text("中文翻译结果", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Card(Modifier.fillMaxWidth()) {
                            Text(translated, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                        Spacer(Modifier.height(20.dp))
                        Text("说明：每次开始屏幕识别都需要你在 Android 系统弹窗中明确授权。首次翻译需要联网下载语言模型；模型下载后可尝试离线翻译。", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
