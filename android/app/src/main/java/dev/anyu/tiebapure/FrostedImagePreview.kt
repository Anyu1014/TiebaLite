package dev.anyu.tiebapure

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable fun FrostedImagePreview(model: Any?, onClose: () -> Unit, onSave: () -> Unit) {
    val layer = rememberGraphicsLayer()
    var origin by remember { mutableStateOf(Offset.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(model, "放大图片", Modifier.fillMaxSize().onGloballyPositioned { origin = it.localToScreen(Offset.Zero) }.drawWithContent {
            layer.record { this@drawWithContent.drawContent() }; drawLayer(layer)
        }.graphicsLayer(scaleX = scale, scaleY = scale).pointerInput(Unit) { detectTransformGestures { _, _, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 6f) } })
        AnyuFrostedSurface(layer, origin, Modifier.align(Alignment.TopEnd).padding(24.dp).size(48.dp), CircleShape, tintOverride = Color(0xff080f16).copy(alpha = .42f)) {
            IconButton(onClick = onClose, modifier = Modifier.fillMaxSize()) { Icon(Icons.Default.Close, "关闭图片", tint = Color.White) }
        }
        AnyuFrostedSurface(layer, origin, Modifier.align(Alignment.BottomCenter).padding(24.dp), CircleShape, tintOverride = Color(0xff080f16).copy(alpha = .42f)) {
            TextButton(onClick = onSave) { Icon(TiebaIcons.Save, null, tint = Color.White); Spacer(Modifier.width(8.dp)); Text("保存图片", color = Color.White) }
        }
    }
}
