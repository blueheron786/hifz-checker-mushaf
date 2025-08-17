package com.hifzmushaf.ui.surah

data class Surah(
    val number: Int,
    val englishName: String,
    val arabicName: String,
    val startPage: Int = 1 // Starting page in the standard 604-page Mushaf
)