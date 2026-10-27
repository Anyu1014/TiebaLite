package dev.anyu.tiebapure

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object TiebaEmoticon {
    val names = linkedMapOf(
        1 to "呵呵",
        2 to "哈哈",
        3 to "吐舌",
        4 to "啊",
        5 to "酷",
        6 to "怒",
        7 to "开心",
        8 to "汗",
        9 to "泪",
        10 to "黑线",
        11 to "鄙视",
        12 to "不高兴",
        13 to "真棒",
        14 to "钱",
        15 to "疑问",
        16 to "阴险",
        17 to "吐",
        18 to "咦",
        19 to "委屈",
        20 to "花心",
        21 to "呼~",
        22 to "笑眼",
        23 to "冷",
        24 to "太开心",
        25 to "滑稽",
        26 to "勉强",
        27 to "狂汗",
        28 to "乖",
        29 to "睡觉",
        30 to "惊哭",
        31 to "生气",
        32 to "惊讶",
        33 to "喷",
        34 to "爱心",
        35 to "心碎",
        36 to "玫瑰",
        37 to "礼物",
        38 to "彩虹",
        39 to "星星月亮",
        40 to "太阳",
        41 to "钱币",
        42 to "灯泡",
        43 to "茶杯",
        44 to "蛋糕",
        45 to "音乐",
        46 to "haha",
        47 to "胜利",
        48 to "大拇指",
        49 to "弱",
        50 to "OK",
        77 to "沙发",
        78 to "手纸",
        79 to "香蕉",
        80 to "便便",
        81 to "药丸",
        82 to "红领巾",
        83 to "蜡烛",
        84 to "三道杠",
        89 to "噗"
    )
    private val aliases = buildMap {
        names.forEach { (id, name) -> put(name, id); put("小$name", id) }
        put("呵", 1); put("笑", 2); put("大笑", 2); put("高兴", 7); put("笑脸", 7)
        put("黑头", 10); put("黑脸", 10); put("黑头高兴", 7); put("黑头开心", 7); put("黑头笑", 2)
    }
    val tokens = Regex("""#\(([^)\n]+)\)|\(#([^)\n]+)\)|\[([^\]\n]{1,32})\]""")
    fun imageName(code: String): String? {
        val value = code.trim().removeSurrounding("#(", ")").removeSurrounding("(#", ")").removeSurrounding("[", "]")
        if (value == "image_emoticon") return "image_emoticon1"
        if (value.matches(Regex("image_emoticon[1-9][0-9]{0,2}"))) return value
        return aliases[value]?.let { "image_emoticon$it" }
    }
    fun nameFromURL(raw: String): String? {
        val url = raw.replaceFirst("http://", "https://").toHttpUrlOrNull() ?: return null
        if (!url.isHttps || url.host != "tb2.bdstatic.com" || url.port != 443 || url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        val file = url.encodedPath.removePrefix("/tb/editor/images/client/").removeSuffix(".png")
        return file.takeIf { url.encodedPath == "/tb/editor/images/client/$it.png" && imageName(it) == it }
    }
    fun url(name: String): String? = imageName(name)?.let { "https://tb2.bdstatic.com/tb/editor/images/client/$it.png" }
    fun label(code: String): String = imageName(code)?.removePrefix("image_emoticon")?.toIntOrNull()?.let { names[it] } ?: code
    fun parts(text: String): List<Block> {
        val result = mutableListOf<Block>(); var start = 0
        tokens.findAll(text).forEach { match ->
            val name = imageName(match.value) ?: return@forEach
            if (match.range.first > start) result += Block(0, text.substring(start, match.range.first))
            result += Block(2, match.value, url(name).orEmpty())
            start = match.range.last + 1
        }
        if (start < text.length) result += Block(0, text.substring(start))
        return result
    }
    fun normalize(block: Block): List<Block> {
        val name = if (block.type == 2) imageName(block.text) ?: nameFromURL(block.url) else if (block.type in setOf(3, 20)) nameFromURL(block.url) else null
        if (name != null) return listOf(block.copy(type = 2, url = url(name).orEmpty()))
        return if (block.type in setOf(0, 9, 27)) parts(block.text) else listOf(block)
    }
    fun summary(blocks: List<Block>): String = blocks.joinToString("") {
        if (it.type == 2) imageName(it.text)?.let { name -> "#(" + label(name) + ")" } ?: nameFromURL(it.url)?.let { name -> "#(" + label(name) + ")" } ?: it.text else it.text
    }
}
