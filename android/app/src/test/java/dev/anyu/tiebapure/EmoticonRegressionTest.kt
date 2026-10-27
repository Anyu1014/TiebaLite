package dev.anyu.tiebapure

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import tieba.PbContentOuterClass.PbContent

class EmoticonRegressionTest {
    private val api = TiebaApi("fixture")
    @Test fun mapsNativeNamesCodesAndAllWireSourcesWithoutChangingPhotos() {
        val native = "https://tb2.bdstatic.com/tb/editor/images/client/image_emoticon25.png"
        val blocks = api.content(listOf(
            PbContent.newBuilder().setType(0).setText("文字#(酷)🙂[未知]").build(),
            PbContent.newBuilder().setType(2).setText("(_ImageToUpload_)").setC("哈哈").build(),
            PbContent.newBuilder().setType(3).setSrc(native).build(),
            PbContent.newBuilder().setType(3).setSrc("https://imgsrc.baidu.com/forum/product.jpg").build()))
        assertEquals(listOf(0, 2, 0, 2, 2, 3), blocks.map { it.type })
        assertEquals("🙂[未知]", blocks[2].text)
        assertTrue(blocks[3].url.endsWith("image_emoticon2.png"))
        assertEquals(native, blocks[4].url)
        assertEquals("https://imgsrc.baidu.com/forum/product.jpg", blocks.last().url)
        assertNull(TiebaEmoticon.nameFromURL("https://evil.test/tb/editor/images/client/image_emoticon25.png"))
        assertNull(TiebaEmoticon.nameFromURL("https://tb2.bdstatic.com.evil.test/tb/editor/images/client/image_emoticon25.png"))
        assertNull(TiebaEmoticon.imageName("image_emoticon1000"))
    }
    @Test fun summariesPreserveNativeArtCodesAndUploadMarkersNeedActualMedia() {
        assertEquals("#(哈哈)#(酷)", api.jsonSummary(JSONArray("""[{"type":2,"c":"哈哈"},{"type":0,"text":"#(酷)"}]""")))
        val marker = PbContent.newBuilder().setText("(_ImageToUpload_)\n正文").build()
        assertEquals("(_ImageToUpload_)\n正文", api.content(listOf(marker)).single().text)
        val image = PbContent.newBuilder().setType(3).setSrc("https://imgsrc.baidu.com/forum/product.jpg").build()
        assertEquals("\n正文", api.content(listOf(marker, image)).first().text)
    }
}
