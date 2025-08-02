package com.quranhabit

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.quranhabit.data.QuranDatabase
import com.quranhabit.data.repository.LastReadRepository
import com.quranhabit.ui.hideWithAnimation
import com.quranhabit.ui.reader.QuranReaderFragment
import com.quranhabit.ui.statistics.ui.StatisticsFragment
import com.quranhabit.ui.showWithAnimation
import com.quranhabit.ui.surah.SurahListFragment
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        // Set up the toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        // Initialize default fragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, SurahListFragment())
                .commit()
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_continue -> {
                    navigateToLastReadPosition()
                    true
                }
                R.id.navigation_statistics -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.nav_host_fragment, StatisticsFragment())
                        .addToBackStack("statistics")
                        .commit()
                    true
                }
                else -> false
            }
        }
    }

    private fun navigateToLastReadPosition() {
        lifecycleScope.launch {
            try {
                val position = LastReadRepository(
                    QuranDatabase.getDatabase(this@MainActivity).lastReadPositionDao()
                ).getLastPosition()

                if (position != null) {
                    val args = Bundle().apply {
                        putInt("surahNumber", position.surah)
                        putInt("ayahNumber", position.ayah)
                        putInt("pageNumber", position.page)
                        putInt("scrollY", position.scrollY)
                        putBoolean("fromContinue", true)
                    }

                    supportFragmentManager.beginTransaction()
                        .replace(R.id.nav_host_fragment, QuranReaderFragment().apply {
                            arguments = args
                        })
                        .addToBackStack("reader")
                        .commit()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "No reading history found",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Navigation failed", e)
                Toast.makeText(
                    this@MainActivity,
                    "Error loading reading progress",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Keep your existing methods
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