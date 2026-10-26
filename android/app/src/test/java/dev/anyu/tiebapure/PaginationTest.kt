package dev.anyu.tiebapure

import org.junit.Assert.*
import org.junit.Test

class PaginationTest {
    private fun thread(id: String) = Thread(id, id, "测试吧")
    @Test fun appendsWithoutLosingEarlierRows() {
        val result = ScreenData(threads = listOf(thread("2"), thread("3")), hasMore = true)
            .appendTo(ScreenData(threads = listOf(thread("1"), thread("2"))))
        assertEquals(listOf("1", "2", "3"), result.threads.map { it.id })
        assertTrue(result.hasMore)
    }
    @Test fun duplicateOrEmptyPagesStopAutomaticRequests() {
        val old = ScreenData(threads = listOf(thread("1")))
        assertFalse(ScreenData(threads = old.threads, hasMore = true).appendTo(old).hasMore)
        assertFalse(ScreenData(hasMore = true).appendTo(old).hasMore)
        assertEquals(old.threads, ScreenData().appendTo(old).threads)
    }
    @Test fun readingPositionUsesVisiblePageInsteadOfLatestFetchedPage() {
        val ids = listOf("a", "b", "c", "d")
        val pages = mapOf("a" to 1, "b" to 1, "c" to 2, "d" to 2)
        assertEquals(1 to 0, threadReadingPosition(0, ids, pages, 2))
        assertEquals(1 to 2, threadReadingPosition(2, ids, pages, 2))
        assertEquals(2 to 1, threadReadingPosition(3, ids, pages, 2))
        assertEquals(2 to 2, threadReadingPosition(99, ids, pages, 2))
        assertEquals(4 to 0, threadReadingPosition(0, emptyList(), emptyMap(), 4))
    }
    @Test fun replyPagesAppendAndHonorServerEnd() {
        fun post(id: String) = Post(id, 1, User(), emptyList())
        val result = ScreenData(posts = listOf(post("b"), post("c")), hasMore = false)
            .appendTo(ScreenData(posts = listOf(post("a"), post("b"))))
        assertEquals(listOf("a", "b", "c"), result.posts.map { it.id })
        assertFalse(result.hasMore)
    }
}
