package com.gr.chessarrows

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var app: ChessArrowsApp
    private lateinit var preview: ArrowPreviewView
    private lateinit var swatchesContainer: LinearLayout
    private lateinit var widthSeekBar: SeekBar
    private lateinit var alphaSeekBar: SeekBar
    private lateinit var modeToggle: MaterialButtonToggleGroup
    private lateinit var serviceSwitch: SwitchMaterial
    private lateinit var statusText: TextView
    private lateinit var stateLabel: TextView
    private lateinit var serviceCard: MaterialCardView
    private lateinit var boardHelpButton: MaterialButton
    private lateinit var adContainer: FrameLayout

    private val colorSwatches = intArrayOf(
        0xFF2E7D32.toInt(), // Green
        0xFF1565C0.toInt(), // Blue
        0xFFC62828.toInt(), // Red
        0xFFEF6C00.toInt(), // Orange
        0xFF6A1B9A.toInt(), // Purple
        0xFF00695C.toInt(), // Teal
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        app = application as ChessArrowsApp

        preview = findViewById(R.id.preview)
        swatchesContainer = findViewById(R.id.swatches)
        widthSeekBar = findViewById(R.id.widthSeekBar)
        alphaSeekBar = findViewById(R.id.alphaSeekBar)
        modeToggle = findViewById(R.id.modeToggle)
        serviceSwitch = findViewById(R.id.serviceSwitch)
        statusText = findViewById(R.id.statusText)
        stateLabel = findViewById(R.id.stateLabel)
        serviceCard = findViewById(R.id.serviceCard)
        boardHelpButton = findViewById(R.id.boardHelpButton)
        adContainer = findViewById(R.id.adContainer)

        // Initialize UI with current settings
        updatePreview()
        setupSwatches()
        setupSeekBars()
        setupModeToggle()
        setupServiceSwitch()
        setupHelpButton()

        // Check onboarding
        if (!app.isOnboardingDone()) {
            startOnboarding()
        } else {
            updateServiceUI()
        }
    }

    override fun onResume() {
        super.onResume()
        updatePreview()
        updateServiceUI()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun startOnboarding() {
        val intent = Intent(this, OnboardingActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun updatePreview() {
        preview.updateStyle(
            app.getArrowColor(),
            app.getArrowWidthDp(),
            app.getArrowAlphaPercent()
        )
    }

    private fun setupSwatches() {
        swatchesContainer.removeAllViews()
        val currentColor = app.getArrowColor()

        for (color in colorSwatches) {
            val swatch = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.button_size_dp),
                    resources.getDimensionPixelSize(R.dimen.button_size_dp)
                ).apply { setMarginEnd(12) }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    setStroke(4, if (color == currentColor) 0xFFFFFFFF.toInt() else 0x00000000.toInt())
                }
                setOnClickListener {
                    app.setArrowColor(color)
                    updatePreview()
                    setupSwatches() // Refresh to update selection
                    broadcastStyleUpdate()
                }
            }
            swatchesContainer.addView(swatch)
        }
    }

    private fun setupSeekBars() {
        widthSeekBar.progress = app.getArrowWidthDp()
        widthSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    app.setArrowWidthDp(progress)
                    updatePreview()
                    broadcastStyleUpdate()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        alphaSeekBar.progress = app.getArrowAlphaPercent()
        alphaSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    app.setArrowAlphaPercent(progress)
                    updatePreview()
                    broadcastStyleUpdate()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupModeToggle() {
        modeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                // Mode changed - could store mode preference here
                broadcastStyleUpdate()
            }
        }
    }

    private fun setupServiceSwitch() {
        serviceSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startOverlayService()
            } else {
                stopOverlayService()
            }
        }
    }

    private fun setupHelpButton() {
        boardHelpButton.setOnClickListener {
            showHelpDialog()
        }
    }

    private fun startOverlayService() {
        // Check overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            showOverlayPermissionDialog()
            serviceSwitch.isChecked = false
            return
        }

        // Check notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            // Service will start after permission granted
            return
        }

        val intent = Intent(this, OverlayService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        stopService(intent)
        updateServiceUI()
    }

    private fun updateServiceUI() {
        val isRunning = isServiceRunning()
        serviceSwitch.isChecked = isRunning
        statusText.text = if (isRunning) getString(R.string.service_running) else getString(R.string.service_stopped)
        stateLabel.text = if (isRunning) getString(R.string.service_state_on) else getString(R.string.service_state_off)
        stateLabel.setBackgroundColor(if (isRunning) ContextCompat.getColor(this, R.color.overlay_green) else 0xFF9E9E9E.toInt())
    }

    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (service.service.className == OverlayService::class.java.name) {
                return true
            }
        }
        return false
    }

    private fun showOverlayPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.overlay_permission_needed))
            .setMessage(getString(R.string.overlay_permission_required))
            .setPositiveButton("Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showHelpDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.how_it_works_title))
            .setMessage(getString(R.string.how_it_works))
            .setPositiveButton("OK", null)
            .show()
    }

    private fun broadcastStyleUpdate() {
        val intent = Intent("com.example.chessarrowoverlay.ACTION_STYLE_UPDATED")
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (serviceSwitch.isChecked) {
                startOverlayService()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1002) { // Overlay permission request
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                if (serviceSwitch.isChecked) {
                    startOverlayService()
                }
            }
        }
    }
}