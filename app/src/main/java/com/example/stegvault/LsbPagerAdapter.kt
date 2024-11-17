package com.example.stegvault

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class LsbPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 2 // Two pages: Encode and Decode

    override fun createFragment(position: Int): Fragment {
        return if (position == 0) {
            LsbEncodingFragment() // Your fragment for LSB encoding
        } else {
            LsbDecodingFragment() // Your fragment for LSB decoding
        }
    }
}
