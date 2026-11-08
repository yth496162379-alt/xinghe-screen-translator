package com.xinghe.screentranslator

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.util.TypedValue
import kotlin.math.abs

class FloatingBubbleService : Service() {
    private var windowManager: WindowManager? = null
    private var bubble: TextView? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SHOW) showBubble()
        return START_STICKY
    }

    private fun showBubble() {
        if (bubble != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 58f, resources.displayMetrics).toInt()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        params = WindowManager.LayoutParams(
            size, size, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - size - 24
            y = resources.displayMetrics.heightPixels / 3
        }
        val view = TextView(this).apply {
            text = "译"
            setTextColor(Color.WHITE)
            textSize = 21f
            gravity = Gravity.CENTER
            elevation = 12f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(35, 112, 235))
                setStroke(dp(2), Color.WHITE)
            }
        }
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragged = false
        view.setOnTouchListener { _, event ->
            val lp = params ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = lp.x
                    startY = lp.y
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (abs(dx) > dp(5) || abs(dy) > dp(5)) dragged = true
                    lp.x = startX + dx
                    lp.y = startY + dy
                    windowManager?.updateViewLayout(view, lp)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) {
                        val launch = Intent(this, MainActivity::class.java).apply {
                            action = MainActivity.ACTION_CAPTURE_REQUEST
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        startActivity(launch)
                    }
                    true
                }
                else -> true
            }
        }
        try {
            windowManager?.addView(view, params)
            bubble = view
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    override fun onDestroy() {
        bubble?.let { view ->
            try { windowManager?.removeView(view) } catch (_: Exception) { }
        }
        bubble = null
        params = null
        windowManager = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_SHOW = "com.xinghe.screentranslator.SHOW_BUBBLE"
    }
}
