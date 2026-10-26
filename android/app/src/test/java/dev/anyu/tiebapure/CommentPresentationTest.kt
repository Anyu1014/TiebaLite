package dev.anyu.tiebapure

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import tieba.PostOuterClass

class CommentPresentationTest {
    @Test fun independentWireFixtureMapsCommentMetadataAndPreview() {
        val hex = javaClass.getResourceAsStream("/comment_post.hex")!!.bufferedReader().use { it.readText().trim() }
        val post = TiebaApi("fixture").post(PostOuterClass.Post.parseFrom(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()))
        assertEquals("42", post.id)
        assertEquals(4, post.authorLevel)
        assertEquals("Layer1", post.authorLevelName)
        assertEquals("贵州", post.ipAddress)
        assertEquals(1792843200, post.timestamp)
        assertEquals(2000L, post.likes)
        assertTrue(post.liked)
        assertEquals(17, post.subCount)
        assertEquals("回复预览", post.children.single().blocks.single().text)
        assertEquals(post, postFromJson(post.json()))
    }
    @Test fun legacyOfflineRepliesRemainReadableWithUnknownMetadata() {
        val post = postFromJson(JSONObject("""{"id":"1","floor":2,"name":"old","blocks":[],"children":[]} """))
        assertNull(post.likes)
        assertEquals(0, post.authorLevel)
        assertEquals("", commentMetadata(post.timestamp, post.ipAddress))
    }
    @Test fun countsAndRegionsUseCompactPresentationWithoutInventingDates() {
        assertEquals("968", commentLikeCount(968))
        assertEquals("2.0k", commentLikeCount(2000))
        assertEquals("1.0m", commentLikeCount(1_000_000))
        assertEquals("贵州", commentMetadata(0, "IP属地：贵州"))
        assertEquals("", commentMetadata(Long.MAX_VALUE, ""))
    }
}
