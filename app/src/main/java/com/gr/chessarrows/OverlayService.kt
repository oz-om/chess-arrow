package com.gr.chessarrows

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.ServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowMetrics
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import android.animation.ValueAnimator
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OverlayService : Service() {

    private lateinit var app: ChessArrowsApp
    private lateinit var windowManager: WindowManager
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var overlayView: ArrowView? = null
    private var undoButton: TextView? = null
    private var drawButton: TextView? = null
    private var boardButton: TextView? = null

    private var undoButtonParams: WindowManager.LayoutParams? = null
    private var drawButtonParams: WindowManager.LayoutParams? = null
    private var boardButtonParams: WindowManager.LayoutParams? = null
    private var drawingParams: WindowManager.LayoutParams? = null

    private var isDrawingActive = false
    private var screenWidth = 0
    private var screenHeight = 0

    private val actionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.example.chessarrowoverlay.ACTION_STYLE_UPDATED" -> updateOverlayStyle()
                "com.example.chessarrowoverlay.ACTION_STOP" -> {
                    stopSelf()
                }
                "com.example.chessarrowoverlay.ACTION_CLEAR" -> {
                    overlayView?.clearArrows()
                }
                "com.example.chessarrowoverlay.ACTION_LOCALE_UPDATED" -> {
                    // Handle locale change if needed
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        app = application as ChessArrowsApp
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Register receiver for all actions
        val filter = IntentFilter().apply {
            addAction("com.example.chessarrowoverlay.ACTION_STYLE_UPDATED")
            addAction("com.example.chessarrowoverlay.ACTION_STOP")
            addAction("com.example.chessarrowoverlay.ACTION_CLEAR")
            addAction("com.example.chessarrowoverlay.ACTION_LOCALE_UPDATED")
        }
        val permission = "$packageName.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        val handler = Handler(Looper.getMainLooper())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(actionReceiver, filter, permission, handler, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(actionReceiver, filter, permission, handler)
        }

        // Create notification channel
        createNotificationChannel()

        // Get screen metrics
        updateScreenMetrics()

        // Start foreground first
        startForegroundService()

        // Show overlay
        showOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(actionReceiver)
        hideOverlay()
        app.clearOverlayGeometry()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        updateScreenMetrics()
        repositionButtons()
        overlayView?.invalidate()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "overlay_service_channel",
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundService() {
        val stopIntent = Intent("com.example.chessarrowoverlay.ACTION_STOP").apply { setPackage(packageName) }
        val clearIntent = Intent("com.example.chessarrowoverlay.ACTION_CLEAR").apply { setPackage(packageName) }

        val stopPendingIntent = PendingIntent.getBroadcast(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val clearPendingIntent = PendingIntent.getBroadcast(
            this, 1, clearIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, "overlay_service_channel")
            .setContentTitle(getString(R.string.service_running))
            .setContentText("Drawing arrows over chess apps")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .addAction(R.mipmap.ic_launcher, getString(R.string.stop_service), stopPendingIntent)
            .addAction(R.mipmap.ic_launcher, getString(R.string.clear_arrows), clearPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, android.app.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    private fun updateScreenMetrics() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics: WindowMetrics = windowManager.currentWindowMetrics
            val bounds: Rect = metrics.bounds
            screenWidth = bounds.width()
            screenHeight = bounds.height()
        } else {
            val display = windowManager.defaultDisplay
            val metrics = android.util.DisplayMetrics()
            display.getMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
        }
    }

    private fun showOverlay() {
        // Create main arrow view
        overlayView = ArrowView(this).apply {
            updateStyle(app.getArrowColor(), app.getArrowWidthDp(), app.getArrowAlphaPercent())
            setDrawingActive(false)
            onArrowClick = { toggleDrawingMode() }
            onDrawingStateChanged = { active ->
                isDrawingActive = active
                updateButtonLabels()
            }
        }

        // Layout params for arrow view (full screen overlay)
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            getOverlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager.addView(overlayView!!, layoutParams)

        // Create control buttons
        createControlButtons()

        // Load saved geometry
        val cx = app.getOverlayCx()
        val cy = app.getOverlayCy()
        if (cx > 0 || cy > 0) {
            // Restore button positions
            CoroutineScope(Dispatchers.Main).launch {
                android.os.SystemClock.sleep(100) // Wait for layout
                restoreButtonPositions()
            }
        }
    }

    private fun getOverlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun createControlButtons() {
        val buttonSize = resources.getDimensionPixelSize(R.dimen.button_size_dp)
        val elevation = resources.getDimensionPixelSize(R.dimen.button_elevation_dp).toFloat()

        // Undo button
        undoButton = createControlButton("↺", buttonSize, elevation)
        undoButtonParams = createButtonLayoutParams(Gravity.START or Gravity.TOP).apply {
            x = (screenWidth / 4).toInt()
            y = (screenHeight / 2).toInt()
        }
        undoButton!!.setOnClickListener { onUndoClick() }
        windowManager.addView(undoButton!!, undoButtonParams!!)

        // Draw button (toggle)
        drawButton = createControlButton("✎", buttonSize, elevation)
        drawButtonParams = createButtonLayoutParams(Gravity.START or Gravity.TOP).apply {
            x = (screenWidth / 2).toInt()
            y = (screenHeight / 2).toInt()
        }
        drawButton!!.setOnClickListener { toggleDrawingMode() }
        windowManager.addView(drawButton!!, drawButtonParams!!)

        // Board button
        boardButton = createControlButton("8×8", buttonSize, elevation)
        boardButtonParams = createButtonLayoutParams(Gravity.START or Gravity.TOP).apply {
            x = (screenWidth * 3 / 4).toInt()
            y = (screenHeight / 2).toInt()
        }
        boardButton!!.setOnClickListener { onBoardClick() }
        windowManager.addView(boardButton!!, boardButtonParams!!)

        // Make buttons draggable
        makeDraggable(undoButton!!, undoButtonParams!!)
        makeDraggable(drawButton!!, drawButtonParams!!)
        makeDraggable(boardButton!!, boardButtonParams!!)

        updateButtonLabels()
    }

    private fun createControlButton(text: String, size: Int, elevation: Float): TextView {
        return TextView(this).apply {
            layoutParams = ViewGroup.LayoutParams(size, size)
            gravity = Gravity.CENTER
            this.text = text
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFFFFFFFF.toInt())
            val bg = android.graphics.drawable.GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xDD2E7D32.toInt()) // #DD2E7D32
            }
            background = bg
            setElevation(elevation)
        }
    }

    private fun createButtonLayoutParams(gravity: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            getOverlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { this.gravity = gravity }
    }

    private fun makeDraggable(button: TextView, params: WindowManager.LayoutParams) {
        button.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val newX = (params.x + event.rawX - v.width / 2f).coerceIn(0f, (screenWidth - v.width).toFloat())
                    val newY = (params.y + event.rawY - v.height / 2f).coerceIn(0f, (screenHeight - v.height).toFloat())
                    params.x = newX.toInt()
                    params.y = newY.toInt()
                    windowManager.updateViewLayout(v, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    saveButtonPositions()
                    snapButtonToEdge(v, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun snapButtonToEdge(button: View, params: WindowManager.LayoutParams) {
        val targetX = if (params.x < screenWidth / 2) 0 else screenWidth - button.width
        val targetY = params.y.coerceIn(0, screenHeight - button.height)

        ValueAnimator.ofFloat(params.x.toFloat(), targetX.toFloat()).apply {
            duration = 300L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                params.x = (animation.animatedValue as Float).toInt()
                windowManager.updateViewLayout(button, params)
            }
            start()
        }
        ValueAnimator.ofFloat(params.y.toFloat(), targetY.toFloat()).apply {
            duration = 300L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                params.y = (animation.animatedValue as Float).toInt()
                windowManager.updateViewLayout(button, params)
            }
            start()
        }
    }

    private fun saveButtonPositions() {
        val cx = undoButtonParams?.x?.toFloat() ?: 0f
        val cy = undoButtonParams?.y?.toFloat() ?: 0f
        val side = drawButtonParams?.x?.toFloat() ?: 0f // Using draw button x as side indicator
        app.setOverlayGeometry(cx, cy, side)
    }

    private fun restoreButtonPositions() {
        val cx = app.getOverlayCx()
        val cy = app.getOverlayCy()
        if (cx > 0 || cy > 0) {
            undoButtonParams?.x = cx.toInt()
            undoButtonParams?.y = cy.toInt()
            if (undoButton != null) windowManager.updateViewLayout(undoButton!!, undoButtonParams!!)

            drawButtonParams?.x = (screenWidth / 2).toInt()
            drawButtonParams?.y = cy.toInt()
            if (drawButton != null) windowManager.updateViewLayout(drawButton!!, drawButtonParams!!)

            boardButtonParams?.x = (screenWidth * 3 / 4).toInt()
            boardButtonParams?.y = cy.toInt()
            if (boardButton != null) windowManager.updateViewLayout(boardButton!!, boardButtonParams!!)
        }
    }

    private fun repositionButtons() {
        // Re-clamp button positions to new screen size
        val buttons = listOf(
            undoButton to undoButtonParams,
            drawButton to drawButtonParams,
            boardButton to boardButtonParams
        )
        for ((btn, params) in buttons) {
            if (btn != null && params != null) {
                params.x = params.x.coerceIn(0, screenWidth - btn.width)
                params.y = params.y.coerceIn(0, screenHeight - btn.height)
                windowManager.updateViewLayout(btn, params)
            }
        }
    }

    private fun updateOverlayStyle() {
        overlayView?.updateStyle(app.getArrowColor(), app.getArrowWidthDp(), app.getArrowAlphaPercent())
    }

    private fun updateButtonLabels() {
        drawButton?.text = if (isDrawingActive) "✓" else "✎"
    }

    private fun toggleDrawingMode() {
        isDrawingActive = !isDrawingActive
        overlayView?.setDrawingActive(isDrawingActive)
        updateButtonLabels()
    }

    private fun onUndoClick() {
        overlayView?.undoLastArrow()
    }

    private fun onBoardClick() {
        overlayView?.clearArrows()
    }

    private fun hideOverlay() {
        overlayView?.let { windowManager.removeView(it) }
        undoButton?.let { windowManager.removeView(it) }
        drawButton?.let { windowManager.removeView(it) }
        boardButton?.let { windowManager.removeView(it) }
    }
}