package com.xinghe.screentranslator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions

class ScreenCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var worker: HandlerThread? = null
    private var handled = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) return START_NOT_STICKY
        createNotificationChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentTitle("星河屏幕翻译正在工作")
            .setContentText("正在识别屏幕文字")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(1, notification)

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val data = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                   else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_DATA)
        if (data == null) { stopSelf(); return START_NOT_STICKY }
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, data)
        worker = HandlerThread("xinghe-screen-capture").also { it.start() }
        val metrics = resources.displayMetrics
        reader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { releaseAll() }
        }, Handler(worker!!.looper))
        display = projection?.createVirtualDisplay("XingheCapture", metrics.widthPixels, metrics.heightPixels,
            metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, Handler(worker!!.looper))
        reader?.setOnImageAvailableListener({ source ->
            if (handled) { source.acquireLatestImage()?.close(); return@setOnImageAvailableListener }
            val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
            handled = true
            val input = InputImage.fromMediaImage(image, 0)
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(input)
                .addOnSuccessListener { result ->
                    val english = result.text
                    if (english.isBlank()) broadcastResult("没有识别到清晰的文字，请重试。")
                    else translate(english)
                }
                .addOnFailureListener { broadcastResult("文字识别失败：${it.localizedMessage}") }
                .addOnCompleteListener { image.close(); releaseAll() }
        }, Handler(worker!!.looper))
        return START_NOT_STICKY
    }

    private fun translate(text: String) {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.CHINESE)
            .build()
        val translator = Translation.getClient(options)
        translator.downloadModelIfNeeded()
            .onSuccessTask { translator.translate(text) }
            .addOnSuccessListener { broadcastResult(it) }
            .addOnFailureListener { broadcastResult("翻译暂不可用，请检查网络后重试：${it.localizedMessage}") }
            .addOnCompleteListener { translator.close() }
    }

    private fun broadcastResult(text: String) {
        sendBroadcast(Intent(ACTION_RESULT).setPackage(packageName).putExtra(EXTRA_TEXT, text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "屏幕翻译", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun releaseAll() {
        reader?.setOnImageAvailableListener(null, null)
        display?.release(); display = null
        reader?.close(); reader = null
        projection?.stop(); projection = null
        worker?.quitSafely(); worker = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_START = "com.xinghe.screentranslator.START"
        const val ACTION_RESULT = "com.xinghe.screentranslator.RESULT"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "result_data"
        const val EXTRA_TEXT = "translated_text"
        private const val CHANNEL = "xinghe_screen_translate"
    }
}
