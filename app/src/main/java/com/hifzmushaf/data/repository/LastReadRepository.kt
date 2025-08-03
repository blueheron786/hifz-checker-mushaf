package com.quranhabit.data.repository

import com.quranhabit.data.dao.LastReadPositionDao
import com.quranhabit.data.entity.LastReadPosition

class LastReadRepository(private val dao: LastReadPositionDao) {
    suspend fun savePosition(surah: Int, ayah: Int, page: Int, scrollY: Int) {
        dao.upsert(LastReadPosition(
            surah = surah,
            ayah = ayah,
            page = page,
            scrollY = scrollY,
            timestamp = System.currentTimeMillis()
        ))
    }

    suspend fun getLastPosition() = dao.getLastPosition()

    suspend fun getScrollPosition(page: Int): Int? {
        return dao.getScrollPosition(page)
    }
}
