package dev.anyu.tiebapure

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AvatarIdentityRegressionTest {
    @Test fun portraitTokensUseTheSameWorkingCdnAsIOS() {
        assertEquals("https://himg.bdimg.com/sys/portrait/item/tb.1.fixture", Policy.avatar("tb.1.fixture?cache=1"))
    }
    @Test fun storedLegacyAvatarUrlsMigrateToTheWorkingCdn() {
        assertEquals("https://himg.bdimg.com/sys/portrait/item/tb.1.fixture", Policy.avatar("https://tb.himg.baidu.com/sys/portrait/item/tb.1.fixture"))
    }
    @Test fun loginDisplayNameAcceptsServiceAliases() {
        for (key in listOf("user_name", "user_nickname", "show_nickname")) {
            val response = JSONObject().put("name_show", " ").put(key, " 测试昵称 ")
            assertEquals(key, "测试昵称", response.userDisplayName())
        }
    }
    @Test fun emptyInlineAuthorUsesTheForumUserList() {
        val thread = JSONObject("""{"id":"7","title":"post","author":{},"author_id":42}""")
        val listed = JSONObject("""{"id":42,"name_show":"帖子作者","portrait":"tb.1.fixture"}""")
        val result = TiebaApi("fixture").jsonThread(thread, "测试", mapOf("42" to listed))
        assertEquals("帖子作者", result.author.name)
        assertEquals("tb.1.fixture", result.author.portrait)
    }
    @Test fun oldAuthenticatedAccountsNeverRenderABlankName() {
        val account = Account.parse(Account("42", "", "fixture-bduss", "fixture-stoken", "fixture-tbs").json())
        assertTrue(account.name.isNotBlank())
    }
    @Test fun profileIdentityRepairCannotChangeOrReplaceTheSession() {
        val old = Account("42", "", "fixture-bduss", "fixture-stoken", "fixture-tbs")
        val repaired = old.withIdentity(User("42", "真实昵称", "tb.1.fixture"))
        assertEquals("真实昵称", repaired.name)
        assertEquals("tb.1.fixture", repaired.portrait)
        assertTrue(old.sessionMatches(repaired))
        assertSame(old, old.withIdentity(User("43", "其他账号", "other")))
        assertEquals(repaired, repaired.withIdentity(User("42", " ", "")))
    }
    @Test fun secondaryPortraitFieldsAreNotDropped() {
        val url = "https://himg.bdimg.com/sys/portrait/item/abc"
        assertEquals(url, JSONObject().put("portrait_url", url).userPortrait())
        val userWire = byteArrayOf(0x10, 0x07, 0xda.toByte(), 0x01, url.length.toByte()) + url.toByteArray()
        val user = tieba.UserOuterClass.User.parseFrom(userWire)
        val post = tieba.PostOuterClass.Post.newBuilder().setId(7).setAuthor(user).build()
        assertEquals(url, TiebaApi("fixture").post(post).author.portrait)
    }
    @Test fun forumEntriesPreserveLogosAndAcceptLegacyRecentRecords() {
        val logo = "https://imgsrc.baidu.com/forum/pic/item/logo.png"
        val forum = forumEntry(JSONObject().put("forum_id", "7").put("forum_name", "测试").put("avatar", logo))
        assertEquals(ForumEntry("7", "测试", logo), forum)
        assertEquals(ForumEntry("", "旧记录", ""), forumEntry(JSONObject().put("name", "旧记录")))
    }
    @Test fun avatarMigrationKeepsTransportAndHostValidation() {
        assertEquals("https://himg.bdimg.com/sys/portrait/item/abc", Policy.avatar("http://tb.himg.baidu.com/sys/portrait/item/abc"))
        for (url in listOf("https://himg.bdimg.com.evil.invalid/a", "https://user:pass@himg.bdimg.com/a", "https://himg.bdimg.com:444/a", "file:///private/a", "../a")) assertNull(url, Policy.avatar(url))
    }
}
