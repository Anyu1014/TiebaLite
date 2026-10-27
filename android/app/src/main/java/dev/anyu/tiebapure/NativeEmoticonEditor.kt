package dev.anyu.tiebapure

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.Drawable
import android.text.*
import android.text.style.ImageSpan
import android.view.inputmethod.BaseInputConnection
import android.widget.EditText
import androidx.compose.runtime.Composable
import androidx.compose.foundation.border
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.viewinterop.AndroidView
import coil.imageLoader
import coil.request.ImageRequest

class ComposerEmoticonSpan(drawable: Drawable, val token: String) : ImageSpan(drawable, ALIGN_BASELINE)

object ComposerEmoticonText {
    fun raw(text: Spanned): String = buildString {
        var index = 0
        while (index < text.length) {
            val span = text.getSpans(index, index + 1, ComposerEmoticonSpan::class.java).firstOrNull()
            if (span != null) { append(span.token); index = text.getSpanEnd(span) }
            else { append(text[index]); index++ }
        }
    }
    fun rawOffset(text: Spanned, offset: Int): Int = raw(SpannableStringBuilder(text, 0, offset.coerceIn(0, text.length))).length
    fun displayOffset(text: Spanned, rawOffset: Int): Int {
        var raw = 0; var index = 0
        while (index < text.length && raw < rawOffset) {
            val span = text.getSpans(index, index + 1, ComposerEmoticonSpan::class.java).firstOrNull()
            raw += span?.token?.length ?: 1
            index = span?.let { text.getSpanEnd(it) } ?: index + 1
        }
        return index
    }
}

class NativeComposerEditText(context: Context) : EditText(context) {
    var onRawChange: (String) -> Unit = {}
    private val artwork = mutableMapOf<String, Drawable>()
    private val requested = mutableSetOf<String>()
    private var rendering = false
    init {
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 17f)
        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        gravity = android.view.Gravity.TOP
        minLines = 8
        hint = "正文，最多 10000 字"
        contentDescription = "正文，最多 10000 字"
        background = null
        val inset = (12 * resources.displayMetrics.density).toInt()
        setPadding(inset, inset, inset, inset)
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (rendering || s == null) return
                onRawChange(ComposerEmoticonText.raw(s))
                post { refresh() }
            }
        })
    }
    override fun onTextContextMenuItem(id: Int): Boolean {
        if (id == android.R.id.paste || id == android.R.id.pasteAsPlainText) {
            val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
            val value = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context) ?: return true
            text?.replace(minOf(selectionStart, selectionEnd).coerceAtLeast(0), maxOf(selectionStart, selectionEnd).coerceAtLeast(0), value.toString())
            return true
        }
        if (id == android.R.id.copy || id == android.R.id.cut) {
            val start = minOf(selectionStart, selectionEnd).coerceAtLeast(0)
            val end = maxOf(selectionStart, selectionEnd).coerceAtLeast(start)
            val selected = SpannableStringBuilder(text, start, end)
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("贴吧正文", ComposerEmoticonText.raw(selected)))
            if (id == android.R.id.cut) text?.delete(start, end)
            return true
        }
        return super.onTextContextMenuItem(id)
    }
    fun insertToken(token: String) {
        val editable = text ?: return
        editable.replace(minOf(selectionStart, selectionEnd).coerceAtLeast(0), maxOf(selectionStart, selectionEnd).coerceAtLeast(0), token)
        refresh()
    }
    fun setRaw(raw: String) {
        if (text?.let(ComposerEmoticonText::raw) == raw) return
        rendering = true
        setText(raw); setSelection(length())
        rendering = false
        refresh()
    }
    private fun refresh() {
        val editable = text ?: return
        if (rendering || BaseInputConnection.getComposingSpanStart(editable) >= 0) return
        val raw = ComposerEmoticonText.raw(editable)
        val start = ComposerEmoticonText.rawOffset(editable, selectionStart.coerceAtLeast(0))
        val end = ComposerEmoticonText.rawOffset(editable, selectionEnd.coerceAtLeast(0))
        val rendered = SpannableStringBuilder(raw)
        TiebaEmoticon.tokens.findAll(raw).toList().asReversed().forEach { match ->
            val name = TiebaEmoticon.imageName(match.value) ?: return@forEach
            var drawable = artwork[name]
            if (drawable == null) {
                drawable = runCatching { context.assets.open("tieba-emoticons/$name.png").use { Drawable.createFromStream(it, name) } }.getOrNull()
                if (drawable != null) artwork[name] = drawable
                else if (requested.add(name)) context.imageLoader.enqueue(ImageRequest.Builder(context).data(TiebaEmoticon.url(name)).target(onSuccess = { artwork[name] = it; post { refresh() } }).build())
            }
            drawable ?: return@forEach
            val size = (textSize * 1.2f).toInt()
            drawable.setBounds(0, 0, size, size)
            rendered.replace(match.range.first, match.range.last + 1, "\uFFFC")
            rendered.setSpan(ComposerEmoticonSpan(drawable, match.value), match.range.first, match.range.first + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (rendered.toString() == editable.toString() && rendered.getSpans(0, rendered.length, ComposerEmoticonSpan::class.java).size == editable.getSpans(0, editable.length, ComposerEmoticonSpan::class.java).size) return
        rendering = true
        editable.replace(0, editable.length, rendered)
        setSelection(ComposerEmoticonText.displayOffset(editable, start), ComposerEmoticonText.displayOffset(editable, end))
        rendering = false
    }
}

class NativeEmoticonEditorState { var view: NativeComposerEditText? = null }

@Composable fun NativeEmoticonEditor(text: String, onChange: (String) -> Unit, state: NativeEmoticonEditorState, modifier: Modifier) {
    val foreground = MaterialTheme.colorScheme.onSurface.toArgb()
    val hint = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    AndroidView(modifier = modifier.then(Modifier.border(androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))), factory = { NativeComposerEditText(it).also { state.view = it } }, update = { view ->
        state.view = view
        view.onRawChange = onChange
        view.setTextColor(foreground); view.setHintTextColor(hint)
        view.setRaw(text)
    })
}
