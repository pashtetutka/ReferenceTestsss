package ru.mirea.reference.data

import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryTest {
    @Test
    fun newestUniqueItemIsFirstAndListIsLimitedToTen() {
        val current = (1L..10L).map { SearchHistoryItem(it, "Термин $it", "Категория") }
        val updated = mergeHistory(SearchHistoryItem(5, "Обновлённый термин", "Категория"), current)
        assertEquals(5L, updated.first().termId)
        assertEquals(10, updated.size)
        assertEquals(1, updated.count { it.termId == 5L })
    }
}

