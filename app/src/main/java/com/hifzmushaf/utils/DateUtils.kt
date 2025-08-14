package com.hifzmushaf.utils

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object DateUtils {
    fun getTodayDate(): String = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
}
