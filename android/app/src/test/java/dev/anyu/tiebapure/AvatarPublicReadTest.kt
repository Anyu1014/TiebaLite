package dev.anyu.tiebapure

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class AvatarPublicReadTest {
    @Test fun publicAuthorAndProfileReturnANicknameAndImageResponse() = runBlocking {
        assumeTrue(System.getenv("RUN_AVATAR_PUBLIC_READ") == "1")
        val api = TiebaApi("0123456789abcdef0123456789abcdef")
        val author = api.forum("奥比交易", 1).first { it.author.id.toLongOrNull()?.let { n -> n > 0 } == true }.author
        assertTrue(author.name.isNotBlank() && author.name != "贴吧用户")
        assertTrue(author.portrait.isNotBlank())
        val forumInfo = api.forumInfo("奥比交易")
        assertTrue(forumInfo.avatar.isNotBlank())
        val profile = api.profile(author.id, null)
        assertEquals(author.id, profile.user.id)
        assertTrue(profile.user.name.isNotBlank() && profile.user.name != "贴吧用户")
        val url = Policy.avatar(profile.user.portrait) ?: error("Missing valid profile avatar")
        OkHttpClient().newCall(Request.Builder().url(url).build()).execute().use { response ->
            assertTrue(response.isSuccessful)
            assertTrue(response.header("Content-Type").orEmpty().startsWith("image/"))
            val body = response.body!!
            assertTrue(body.contentLength() in 1..8L * 1024 * 1024)
            val bytes = body.bytes()
            assertTrue(bytes.size > 100)
            assertTrue((bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte()) || String(bytes.copyOfRange(1, 4)) == "PNG" || String(bytes.copyOfRange(0, 4)) == "RIFF")
        }
    }
}
