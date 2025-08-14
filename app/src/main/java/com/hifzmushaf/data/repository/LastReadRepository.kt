package com.hifzmushaf.data.repository

import com.hifzmushaf.data.dao.LastReadPositionDao
import com.hifzmushaf.data.entity.LastReadPosition

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
