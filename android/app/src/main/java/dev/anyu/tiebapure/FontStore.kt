package dev.anyu.tiebapure

import android.content.Context
import android.graphics.Typeface
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

data class ReaderFont(val file: String, val name: String)

class FontStore(private val context: Context) {
    private val root = File(context.filesDir, "reader-fonts").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("reader-fonts", Context.MODE_PRIVATE)
    fun list(): List<ReaderFont> = JSONArray(prefs.getString("fonts", "[]")).objects().map { ReaderFont(it.getString("file"), it.getString("name")) }.filter { typeface(it.file) != null }
    fun selected(): String = prefs.getString("selected", "").orEmpty()
    fun select(file: String) { require(file.isEmpty() || typeface(file) != null); check(prefs.edit().putString("selected", file).commit()) }
    fun typeface(file: String): Typeface? {
        if (!file.matches(Regex("[a-f0-9]{64}\\.(ttf|otf)"))) return null
        val font = File(root, file)
        if (!font.isFile || java.nio.file.Files.isSymbolicLink(font.toPath()) || font.length() !in 1..20L * 1024 * 1024) return null
        return runCatching { Typeface.Builder(font).build() }.getOrNull()
    }
    fun importFont(input: InputStream, displayName: String): ReaderFont {
        val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
        input.use { stream -> while (true) { val n = stream.read(buffer); if (n < 0) break; require(out.size() + n <= 20 * 1024 * 1024) { "字体不能超过 20 MB" }; out.write(buffer, 0, n) } }
        val bytes = out.toByteArray()
        require(bytes.size >= 4) { "字体文件为空或损坏" }
        val extension = when {
            bytes.copyOfRange(0, 4).contentEquals(byteArrayOf(0, 1, 0, 0)) -> "ttf"
            bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "OTTO" -> "otf"
            else -> error("仅支持真实的 TTF/OTF 字体文件")
        }
        val fileName = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } + ".$extension"
        val current = list()
        current.firstOrNull { it.file == fileName }?.let { select(fileName); return it }
        require(current.size < 20) { "最多导入 20 款字体，请先移除部分字体" }
        val file = File(root, fileName)
        try {
            file.writeBytes(bytes)
            require(typeface(fileName) != null) { "系统无法解析这份字体" }
            val entry = ReaderFont(fileName, displayName.trim().replace(Regex("[\\r\\n]"), " ").take(200).ifBlank { "导入字体" })
            val array = JSONArray().apply { (current + entry).forEach { put(JSONObject().put("file", it.file).put("name", it.name)) } }
            check(prefs.edit().putString("fonts", array.toString()).putString("selected", fileName).commit()) { "字体配置保存失败" }
            return entry
        } catch (e: Exception) { file.delete(); throw e }
    }
    fun remove(font: ReaderFont) {
        val remaining = list().filterNot { it.file == font.file }
        val array = JSONArray().apply { remaining.forEach { put(JSONObject().put("file", it.file).put("name", it.name)) } }
        val editor = prefs.edit().putString("fonts", array.toString())
        if (selected() == font.file) editor.remove("selected")
        check(editor.commit())
        if (font.file.matches(Regex("[a-f0-9]{64}\\.(ttf|otf)"))) File(root, font.file).delete()
    }
}
