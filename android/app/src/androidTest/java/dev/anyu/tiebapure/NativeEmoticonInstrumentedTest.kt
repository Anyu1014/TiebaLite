package dev.anyu.tiebapure

import android.content.Intent
import android.text.Spanned
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeEmoticonInstrumentedTest {
    @Test fun nativeArtworkEditingRoundTripAndInlineDisplay() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val activity = inst.startActivitySync(Intent(inst.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        val state = NativeEmoticonEditorState()
        val raw = "你好🙂#(酷)[哈哈](#滑稽)[未知]"
        try {
            inst.runOnMainSync {
                activity.setContent {
                    var body by remember { mutableStateOf(raw) }
                    MaterialTheme {
                        androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) {
                            NativeEmoticonText(TiebaEmoticon.parts("正文：$raw"))
                            Spacer(Modifier.height(20.dp))
                            NativeEmoticonText(TiebaEmoticon.parts("评论：#(哈哈)和#(酷)都是行内表情"))
                            NativeEmoticonEditor(body, { body = it }, state, Modifier.fillMaxWidth().height(240.dp))
                        }
                        }
                    }
                }
            }
            inst.waitForIdleSync()
            val deadline = android.os.SystemClock.uptimeMillis() + 5000
            while (state.view == null && android.os.SystemClock.uptimeMillis() < deadline) android.os.SystemClock.sleep(100)
            inst.runOnMainSync {
                val view = requireNotNull(state.view)
                val text = requireNotNull(view.text)
                assertEquals("你好🙂\uFFFC\uFFFC\uFFFC[未知]", text.toString())
                assertEquals(raw, ComposerEmoticonText.raw(text))
                assertEquals(3, text.getSpans(0, text.length, ComposerEmoticonSpan::class.java).size)
                assertEquals(5, ComposerEmoticonText.displayOffset(text, "你好🙂#(酷)".length))
                view.setSelection(5)
                view.insertToken("#(爱心)")
                assertEquals("你好🙂#(酷)#(爱心)[哈哈](#滑稽)[未知]", ComposerEmoticonText.raw(text))
                text.delete(4, 5)
                assertEquals("你好🙂#(爱心)[哈哈](#滑稽)[未知]", ComposerEmoticonText.raw(text))
                assertEquals("#(爱心)", ComposerEmoticonText.raw(android.text.SpannableStringBuilder(text, 4, 5)))
            }
            lateinit var input: android.view.inputmethod.InputConnection
            fun ime(step: () -> Unit) { inst.runOnMainSync(step); inst.waitForIdleSync() }
            ime {
                val view = requireNotNull(state.view)
                input = requireNotNull(view.onCreateInputConnection(android.view.inputmethod.EditorInfo()))
                view.setRaw("")
            }
            repeat(20) {
                ime { input.setComposingText("ni", 1) }
                ime { input.setComposingText("nihao", 1) }
                ime { input.commitText("你好", 1); input.finishComposingText() }
            }
            ime {
                val view = requireNotNull(state.view)
                assertEquals("你好".repeat(20), ComposerEmoticonText.raw(requireNotNull(view.text)))
                view.setRaw("开头#(酷)")
            }
            ime { input.commitText("表情后继续", 1) }
            ime {
                val view = requireNotNull(state.view)
                assertEquals("开头#(酷)表情后继续", ComposerEmoticonText.raw(requireNotNull(view.text)))
                view.setSelection(3)
                input.deleteSurroundingText(1, 0)
            }
            ime { assertEquals("开头表情后继续", ComposerEmoticonText.raw(requireNotNull(state.view?.text))) }
            inst.waitForIdleSync()
            val screenshot = inst.uiAutomation.takeScreenshot()
            java.io.File(inst.targetContext.getExternalFilesDir(null), "native-emoticon-check.png").outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            screenshot.recycle()
        } finally { inst.runOnMainSync { activity.finish() } }
    }
}
