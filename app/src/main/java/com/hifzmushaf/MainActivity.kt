package com.hifzmushaf

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.hifzmushaf.ui.hideWithAnimation
import com.hifzmushaf.ui.reader.QuranReaderFragment
import com.hifzmushaf.ui.showWithAnimation
import com.hifzmushaf.ui.surah.SurahListFragment

class MainActivity : AppCompatActivity() {
    private var lastPage: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, SurahListFragment())
                .commit()
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        // Only one nav item: open reader at last page
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_continue -> {
                    openReaderAtLastPage()
                    true
                }
                else -> false
            }
        }
    }

    private fun openReaderAtLastPage() {
        val page = lastPage ?: 1 // default to first page
        val args = Bundle().apply {
            putInt("pageNumber", page)
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, QuranReaderFragment().apply {
                arguments = args
            })
            .addToBackStack("reader")
            .commit()
    }

    fun rememberPage(page: Int) {
        lastPage = page
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            super.onBackPressed()
        }
    }

    fun setBottomNavVisibility(visible: Boolean) {
        findViewById<BottomNavigationView>(R.id.bottom_nav).apply {
            if (visible) showWithAnimation() else hideWithAnimation()
        }
    }
}