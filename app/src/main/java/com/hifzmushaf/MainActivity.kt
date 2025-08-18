package com.hifzmushaf

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.hifzmushaf.data.QuranDatabase
import com.hifzmushaf.data.repository.LastReadRepository
import com.hifzmushaf.ui.hideWithAnimation
import com.hifzmushaf.ui.reader.QuranImageReaderFragment
import com.hifzmushaf.ui.showWithAnimation
import com.hifzmushaf.ui.surah.SurahListFragment
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private var lastPage: Int? = null
    
    private val lastReadRepo by lazy {
        LastReadRepository(QuranDatabase.getDatabase(this).lastReadPositionDao())
    }

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
        lifecycleScope.launch {
            try {
                val lastPosition = lastReadRepo.getLastPosition()
                val args = Bundle().apply {
                    if (lastPosition != null) {
                        putInt("surahNumber", lastPosition.surah)
                        putInt("ayahNumber", lastPosition.ayah)
                        putInt("pageNumber", lastPosition.page)
                        putInt("scrollY", lastPosition.scrollY)
                        putBoolean("fromContinue", true)
                    } else {
                        // If no last position, default to first page
                        putInt("pageNumber", lastPage ?: 0)
                    }
                }
                supportFragmentManager.beginTransaction()
                    .replace(R.id.nav_host_fragment, QuranImageReaderFragment().apply {
                        arguments = args
                    })
                    .addToBackStack("reader")
                    .commit()
            } catch (e: Exception) {
                // Fallback to old behavior if database fails
                val args = Bundle().apply {
                    putInt("pageNumber", lastPage ?: 0)
                }
                supportFragmentManager.beginTransaction()
                    .replace(R.id.nav_host_fragment, QuranImageReaderFragment().apply {
                        arguments = args
                    })
                    .addToBackStack("reader")
                    .commit()
            }
        }
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