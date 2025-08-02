package com.quranhabit.ui.reader

import android.util.Log
import androidx.core.widget.NestedScrollView
import kotlin.math.abs

class ScrollTracker {
    var isProgrammaticScroll: Boolean = false

    var onScrollStateChanged: ((Boolean) -> Unit)? = null
    var onScrollPositionChanged: ((Boolean) -> Unit)? = null
    var onScrollPositionSaved: ((Int) -> Unit)? = null

    private var scrollView: NestedScrollView? = null

    private var isScrolling = false
    private var lastScrollY: Int = 0

    fun attach(view: NestedScrollView) {
        scrollView = view

        // Restore scroll position if we have a saved one
        if (lastScrollY > 0) {
            Log.d("ScrollTracker", "Restoring scroll position to $lastScrollY")
            view.post {
                view.scrollTo(0, lastScrollY)
            }
        }

        onScrollStateChanged?.invoke(false) // Initial state is not scrolling

        view.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            // Skip tracking if this is a programmatic scroll
            if (isProgrammaticScroll) return@OnScrollChangeListener

            // Track scrolling state
            val newScrollingState = abs(scrollY - oldScrollY) < SCROLL_ERROR_MARGIN
            if (newScrollingState != isScrolling) {
                isScrolling = newScrollingState
                onScrollStateChanged?.invoke(isScrolling)
            }

            if (!newScrollingState && !isScrolling) {
                saveScrollPosition()
            }

            // Track bottom position only if not programmatic scroll
            val contentHeight = scrollView?.getChildAt(0)?.height ?: 0
            val visibleHeight = scrollView?.height ?: 0
            val newBottomState = (scrollY + visibleHeight) >= contentHeight - PIXELS_BUFFER

            if (newBottomState != isBottomReached) {
                isBottomReached = newBottomState
                Log.d("ScrollTracker", "Bottom state changed: $isBottomReached")
                onScrollPositionChanged?.invoke(isBottomReached)
            }
        })
    }

    private var isBottomReached = false
        set(value) {
            if (field != value) {
                field = value
                Log.d("ScrollTracker", "Bottom state changed: $value")
                onScrollPositionChanged?.invoke(value)
            }
        }

    fun isBottomReached(): Boolean {
        scrollView?.let {
            val contentHeight = it.getChildAt(0)?.height ?: 0
            val visibleHeight = it.height
            val scrollY = it.scrollY
            return (scrollY + visibleHeight) >= contentHeight - PIXELS_BUFFER
        }
        return false
    }

    fun getScrollY(): Int {
        return scrollView?.scrollY ?: 0
    }

    private fun saveScrollPosition() {
        lastScrollY = scrollView?.scrollY ?: 0
        onScrollPositionSaved?.invoke(lastScrollY)
    }

    fun detach() {
        saveScrollPosition()

        scrollView?.setOnScrollChangeListener(null as NestedScrollView.OnScrollChangeListener?)
        scrollView = null
        onScrollStateChanged = null
        onScrollPositionChanged = null  // Prevent stale callbacks
    }

    companion object {
        const val PIXELS_BUFFER = 16
        const val SCROLL_ERROR_MARGIN = 16 // Scrolled to 3106, then 3106 +- 16? It's a STOP yo. To save position.
    }
}