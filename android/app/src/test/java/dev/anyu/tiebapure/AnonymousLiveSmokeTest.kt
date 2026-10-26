package dev.anyu.tiebapure

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class AnonymousLiveSmokeTest {
    @Test fun anonymousReadJourney() = runBlocking {
        assumeTrue("Set RUN_ANONYMOUS_LIVE_SMOKE=1 to call public Tieba endpoints", System.getenv("RUN_ANONYMOUS_LIVE_SMOKE") == "1")
        val api = TiebaApi("0123456789abcdef0123456789abcdef")
        val home = api.home(null, 1)
        assertTrue("Home should return real threads", home.isNotEmpty())
        val detail = api.thread(home.first().id, 1, null)
        assertTrue(detail.posts.isNotEmpty())
        assertTrue(detail.thread.id.toLong() > 0)
        val search = api.search("旅行", 1)
        assertTrue("Search should return real threads", search.isNotEmpty())
        val forum = api.forum("旅行", 1)
        assertTrue("Forum should return real threads", forum.isNotEmpty())
    }
}
