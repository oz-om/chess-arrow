package com.gr.chessarrows

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

class OnboardingActivity : AppCompatActivity() {

    private lateinit var pager: ViewPager2
    private lateinit var dotsContainer: LinearLayout
    private lateinit var btnBack: com.google.android.material.button.MaterialButton
    private lateinit var btnNext: com.google.android.material.button.MaterialButton
    private lateinit var btnGetStarted: com.google.android.material.button.MaterialButton

    private val pages = listOf(
        OnboardingPage("♟️", R.string.onboarding_page_1, R.color.overlay_green, R.string.onboarding_title_1),
        OnboardingPage("🔐", R.string.onboarding_page_2, R.color.overlay_green, R.string.onboarding_title_2),
        OnboardingPage("🛡️", R.string.onboarding_page_3, R.color.overlay_green, R.string.onboarding_title_3),
        OnboardingPage("🔔", R.string.onboarding_page_4, R.color.overlay_green, R.string.onboarding_title_4),
        OnboardingPage("👆", R.string.onboarding_page_5, R.color.overlay_green, R.string.onboarding_title_5),
        OnboardingPage("🎛️", R.string.onboarding_page_6, R.color.overlay_green, R.string.onboarding_title_6)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        pager = findViewById(R.id.onboardingPager)
        dotsContainer = findViewById(R.id.dotsContainer)
        btnBack = findViewById(R.id.btnBack)
        btnNext = findViewById(R.id.btnNext)
        btnGetStarted = findViewById(R.id.btnGetStarted)

        pager.adapter = OnboardingPagerAdapter(pages)
        pager.offscreenPageLimit = 1

        setupDots()
        updateUI(0)

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateUI(position)
                updateDots(position)
            }
        })

        btnBack.setOnClickListener { pager.currentItem = pager.currentItem - 1 }
        btnNext.setOnClickListener { pager.currentItem = pager.currentItem + 1 }
        btnGetStarted.setOnClickListener { finishOnboarding() }
    }

    private fun setupDots() {
        dotsContainer.removeAllViews()
        for (i in pages.indices) {
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.button_size_dp) / 4,
                    resources.getDimensionPixelSize(R.dimen.button_size_dp) / 4
                )
                setBackgroundResource(R.drawable.dot_indicator)
                if (i > 0) {
                    (layoutParams as LinearLayout.LayoutParams).setMarginStart(12)
                }
            }
            dotsContainer.addView(dot)
        }
    }

    private fun updateDots(selected: Int) {
        for (i in 0 until dotsContainer.childCount) {
            val dot = dotsContainer.getChildAt(i)
            val isSelected = i == selected
            dot.setBackgroundResource(if (isSelected) R.drawable.dot_indicator_selected else R.drawable.dot_indicator)
            val params = dot.layoutParams as LinearLayout.LayoutParams
            params.width = if (isSelected) resources.getDimensionPixelSize(R.dimen.button_size_dp) / 2 else resources.getDimensionPixelSize(R.dimen.button_size_dp) / 4
            params.height = if (isSelected) resources.getDimensionPixelSize(R.dimen.button_size_dp) / 2 else resources.getDimensionPixelSize(R.dimen.button_size_dp) / 4
            dot.layoutParams = params
        }
    }

    private fun updateUI(position: Int) {
        val isLast = position == pages.lastIndex
        val isFirst = position == 0

        btnBack.visibility = if (isFirst) View.GONE else View.VISIBLE
        btnNext.visibility = if (isLast) View.GONE else View.VISIBLE
        btnGetStarted.visibility = if (isLast) View.VISIBLE else View.GONE
    }

    private fun finishOnboarding() {
        val app = application as ChessArrowsApp
        app.setOnboardingDone(true)

        // Broadcast style updated
        val intent = Intent("com.example.chessarrowoverlay.ACTION_STYLE_UPDATED")
        intent.setPackage(packageName)
        sendBroadcast(intent)

        // Start main activity
        val mainIntent = Intent(this, MainActivity::class.java)
        mainIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(mainIntent)
        finish()
    }

    private data class OnboardingPage(
        val emoji: String,
        val bodyRes: Int,
        val colorRes: Int,
        val titleRes: Int
    )

    private inner class OnboardingPagerAdapter(
        private val pages: List<OnboardingPage>
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<OnboardingPagerAdapter.PageViewHolder>() {

        inner class PageViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.icon)
            val title: TextView = view.findViewById(R.id.title)
            val body: TextView = view.findViewById(R.id.body)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_onboarding_page, parent, false)
            return PageViewHolder(view)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            val page = pages[position]
            holder.title.text = page.emoji
            holder.body.text = getString(page.bodyRes)
            holder.icon.contentDescription = getString(page.titleRes)
            val color = ContextCompat.getColor(this@OnboardingActivity, page.colorRes)
            holder.title.setTextColor(color)
        }

        override fun getItemCount() = pages.size
    }
}