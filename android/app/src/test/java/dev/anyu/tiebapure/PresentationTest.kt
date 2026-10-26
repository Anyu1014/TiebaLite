package dev.anyu.tiebapure

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class PresentationTest {
    @Test fun oldBackupKeepsUnknownLikesUnknown() {
        val thread = threadFromJson(JSONObject("""{"id":"42","title":"old","author":{}}"""))
        assertNull(thread.likes)
        assertEquals("", thread.forumAvatar)
        assertEquals(0, thread.timestamp)
    }
    @Test fun headerMetadataRoundTripsWithoutChangingTheThread() {
        val value = Thread("42", "title", "旅行", User("7", "name"), likes = 981, forumAvatar = "https://tb.himg.baidu.com/x.png", timestamp = 1792987200, collected = true)
        assertEquals(value, threadFromJson(value.json()))
    }
    @Test fun dateTextMatchesIOSAcrossDayAndYearBoundaries() {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = Instant.parse("2026-10-26T04:00:00Z").epochSecond
        assertEquals("刚刚", readerDateText(now - 30, now, zone))
        assertEquals("5分钟前", readerDateText(now - 300, now, zone))
        assertEquals("10:00", readerDateText(now - 7200, now, zone))
        assertEquals("昨天 12:00", readerDateText(now - 86400, now, zone))
        assertEquals("2025-12-31", readerDateText(Instant.parse("2025-12-31T00:00:00Z").epochSecond, now, zone))
    }
    @Test fun missingOrOutOfRangeDatesNeverReachTheFormatter() {
        assertEquals("", readerDateText(0))
        assertEquals("", readerDateText(Long.MAX_VALUE))
        assertEquals("", readerDateText(Long.MIN_VALUE))
    }
}
