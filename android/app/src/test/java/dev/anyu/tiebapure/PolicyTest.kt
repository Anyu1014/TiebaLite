package dev.anyu.tiebapure

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import tieba.PageOuterClass.Page
import tieba.ErrorOuterClass.Error

class PolicyTest {
    @Test fun rejectCredentialAndDomainConfusion() {
        assertTrue(Policy.apiUrl("https://tieba.baidu.com/c/f/pb/page"))
        for (u in listOf("http://tieba.baidu.com", "https://tieba.baidu.com.evil.example", "https://user:pass@tieba.baidu.com", "https://tieba.baidu.com:444", "https://127.0.0.1")) assertFalse(u, Policy.apiUrl(u))
        assertFalse(Policy.cookie("x; STOKEN=y"))
        assertFalse(Policy.cookie("x\r\ny"))
        assertNull(Policy.mediaUrl("https://evil.example/a.jpg"))
    }
    @Test fun routeHandlesValidLinksAndRejectsSpoofedOrOverflowingIdentifiers() {
        assertEquals(Route("thread", "42"), Policy.route("https://tieba.baidu.com/p/42"))
        assertEquals(Route("thread", "42", extra = "7"), Policy.route("https://tieba.baidu.com/p/42?pid=7"))
        assertEquals("测试", Policy.route("${BuildConfig.APP_SCHEME}://forum/%E6%B5%8B%E8%AF%95")?.id)
        for (u in listOf("https://tieba.baidu.com.evil.example/p/1", "http://tieba.baidu.com/p/1", "https://x:y@tieba.baidu.com/p/1", "${BuildConfig.APP_SCHEME}://thread/18446744073709551615")) assertNull(u, Policy.route(u))
    }
    @Test fun signerSortsFullPairsIncludingPrefixKeys() {
        val expected = MessageDigest.getInstance("MD5").digest("a1=2a=1tiebaclient!!!".toByteArray()).joinToString("") { "%02X".format(it) }
        assertEquals(expected, Policy.sign(mapOf("a" to "1", "a1" to "2")))
    }
    @Test fun decodeIndependentWireFixtures() {
        assertEquals(2, Page.parseFrom(byteArrayOf(0x18, 0x02)).currentPage)
        assertEquals(160002, Error.parseFrom(byteArrayOf(0x08, 0x82.toByte(), 0xe2.toByte(), 0x09)).errorCode)
    }
    @Test fun signedRequestIdsRejectUnsignedOverflow() {
        assertEquals(Long.MAX_VALUE, Policy.signedId(Long.MAX_VALUE.toString()))
        for (id in listOf("0", "-1", "18446744073709551615")) assertTrue(runCatching { Policy.signedId(id) }.isFailure)
    }
    @Test fun voiceLinksUseTheProtocolHostAndRejectMalformedIdentifiers() {
        val md5 = "A".repeat(32)
        assertEquals("https://tiebac.baidu.com/c/p/voice?voice_md5=${md5.lowercase()}&play_from=pb_voice_play", Policy.voice(md5))
        assertNull(Policy.voice("../etc/passwd"))
    }
    @Test fun profileRenameDoesNotInvalidateTheAuthenticatedSession() {
        val account = Account("42", "old", "fixture-bduss", "fixture-stoken", "fixture-tbs")
        assertTrue(account.sessionMatches(account.copy(name = "new")))
        assertFalse(account.sessionMatches(account.copy(bduss = "different-session")))
        assertFalse(account.sessionMatches(null))
    }
}
