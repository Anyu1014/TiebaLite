package dev.anyu.tiebapure

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import tieba.PostOuterClass.Advertisement
import tieba.PostOuterClass.Post as WirePost
import tieba.PbContentOuterClass.PbContent
import tieba.pbPage.PbPageResponseOuterClass.PbPageResponseData

class ThreadBodyRegressionTest {
    @Test fun placeholderAdvertisementDoesNotRemoveRealBodyOrReplies() {
        val placeholder = Advertisement.newBuilder().setAdvertisementId("advertisement_test1")
        val main = WirePost.newBuilder().setId(150670290200).setFloor(1)
            .addContent(PbContent.newBuilder().setText("首楼正文"))
            .setAdvertisement(placeholder).build()
        val reply = main.toBuilder().setId(150670293954).setFloor(2).build()
        val ad = main.toBuilder().setId(99).setFloor(3)
            .setAdvertisement(Advertisement.newBuilder().setAdvertisementId("real-ad")).build()
        val slot = WirePost.newBuilder().setAdvertisement(placeholder).build()
        val api = TiebaApi("fixture")
        val page = api.threadPage(PbPageResponseData.newBuilder().addAllPostList(listOf(main, reply, ad, slot)).build())
        assertEquals(listOf("150670290200", "150670293954"), page.posts.map { it.id })
        assertEquals("首楼正文", page.posts.first().blocks.single().text)
        val separateFirst = api.threadPage(PbPageResponseData.newBuilder().setFirstFloorPost(main).addPostList(reply).build())
        assertEquals(page.posts, separateFirst.posts)
    }

    @Test fun reportedPublicThreadContainsItsBody() = runBlocking {
        assumeTrue("Set RUN_BODY_PUBLIC_READ=1 for anonymous public reads", System.getenv("RUN_BODY_PUBLIC_READ") == "1")
        val page = TiebaApi("0123456789abcdef0123456789abcdef").thread("9107183498", 1, null)
        val main = page.posts.singleOrNull { it.floor == 1 }
        assertNotNull("Reported thread must retain its first floor", main)
        assertEquals("150670290200", main!!.id)
        assertTrue("Body must contain readable text", main.blocks.any { it.text.isNotBlank() })
    }
}
