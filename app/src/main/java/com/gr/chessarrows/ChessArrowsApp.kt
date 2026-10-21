package com.gr.chessarrows

import android.app.Application
import android.content.Context
import android.content.SharedPreferences

class ChessArrowsApp : Application() {

    companion object {
        private const val PREFS_NAME = "arrow_prefs"
        private const val KEY_COLOR = "color"
        private const val KEY_WIDTH_DP = "width_dp"
        private const val KEY_ALPHA_PERCENT = "alpha_percent"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_CX = "cx"
        private const val KEY_CY = "cy"
        private const val KEY_SIDE = "side"

        private const val DEFAULT_COLOR = 0xFF2E7D32.toInt()
        private const val DEFAULT_WIDTH_DP = 8
        private const val DEFAULT_ALPHA_PERCENT = 80
    }

    private lateinit var prefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getPrefs(): SharedPreferences = prefs

    // Arrow style getters/setters
    fun getArrowColor(): Int = prefs.getInt(KEY_COLOR, DEFAULT_COLOR)
    fun setArrowColor(color: Int) {
        prefs.edit().putInt(KEY_COLOR, color).apply()
    }

    fun getArrowWidthDp(): Int = prefs.getInt(KEY_WIDTH_DP, DEFAULT_WIDTH_DP)
    fun setArrowWidthDp(widthDp: Int) {
        prefs.edit().putInt(KEY_WIDTH_DP, widthDp).apply()
    }

    fun getArrowAlphaPercent(): Int = prefs.getInt(KEY_ALPHA_PERCENT, DEFAULT_ALPHA_PERCENT)
    fun setArrowAlphaPercent(alphaPercent: Int) {
        prefs.edit().putInt(KEY_ALPHA_PERCENT, alphaPercent).apply()
    }

    // Onboarding
    fun isOnboardingDone(): Boolean = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
    fun setOnboardingDone(done: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, done).apply()
    }

    // Overlay geometry
    fun getOverlayCx(): Float = prefs.getFloat(KEY_CX, 0f)
    fun getOverlayCy(): Float = prefs.getFloat(KEY_CY, 0f)
    fun getOverlaySide(): Float = prefs.getFloat(KEY_SIDE, 0f)

    fun setOverlayGeometry(cx: Float, cy: Float, side: Float) {
        prefs.edit()
            .putFloat(KEY_CX, cx)
            .putFloat(KEY_CY, cy)
            .putFloat(KEY_SIDE, side)
            .apply()
    }

    fun clearOverlayGeometry() {
        prefs.edit()
            .remove(KEY_CX)
            .remove(KEY_CY)
            .remove(KEY_SIDE)
            .apply()
    }
}