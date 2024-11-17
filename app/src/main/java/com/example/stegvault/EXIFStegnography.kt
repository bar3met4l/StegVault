package com.example.stegvault

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class EXIFStegnography : AppCompatActivity() {
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exifstegnography)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.exifStegActivity)) { view, insets ->
            val systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBarInsets.top)
            insets
        }
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)

        // Set up the adapter for ViewPager2
        viewPager.adapter = ExifPagerAdapter(this)

        // Attach TabLayout with ViewPager2 using TabLayoutMediator
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = if (position == 0) "Encode" else "Decode"
        }.attach()

        // Optional: Custom behavior for focus change on tabs
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                for (i in 0 until tabLayout.tabCount) {
                    val tab = tabLayout.getTabAt(i)
                    if (tab != null) {
                        tab.view.alpha = if (i == position) 1f else 0.5f // Change alpha for out-of-focus tabs
                    }
                }
            }
        })
    }
}