package com.quranhabit.ui

import android.view.View
import com.google.android.material.bottomnavigation.BottomNavigationView

fun BottomNavigationView.showWithAnimation() {
    if (visibility == View.VISIBLE) return
    visibility = View.VISIBLE
    animate()
        .translationY(0f)
        .setDuration(300)
        .start()
}

fun BottomNavigationView.hideWithAnimation() {
    if (visibility == View.GONE) return
    animate()
        .translationY(height.toFloat())
        .setDuration(300)
        .withEndAction { visibility = View.GONE }
        .start()
}