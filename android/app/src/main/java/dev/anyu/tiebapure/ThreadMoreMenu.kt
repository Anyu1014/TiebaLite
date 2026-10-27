package dev.anyu.tiebapure

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*

@Composable fun ThreadMoreMenu(vm: AppViewModel, onDelete: () -> Unit) {
    var expanded by remember(vm.route.id) { mutableStateOf(false) }
    var saveOptions by remember(vm.route.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val backdrop = LocalAnyuBackdrop.current
    val margin = with(LocalDensity.current) { 12.dp.roundToPx() }
    val position = remember(margin) { object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) = IntOffset(
            (anchorBounds.right - popupContentSize.width).coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)),
            anchorBounds.top.coerceIn(margin, (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin))
        )
    } }
    val link = "https://tieba.baidu.com/p/${vm.route.id}"
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.MoreVert, "帖子操作", modifier = Modifier.rotate(90f).size(18.dp)) }
        if (expanded && backdrop != null) Popup(popupPositionProvider = position, onDismissRequest = { expanded = false }, properties = PopupProperties(focusable = true)) {
            AnyuFrostedSurface(backdrop.layer, backdrop.origin, Modifier.width(248.dp), RoundedCornerShape(IOSLayout.groupRadius)) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    DropdownMenuItem(text = { Text("刷新", fontSize = 17.sp) }, leadingIcon = { Icon(Icons.Outlined.Refresh, null) }, onClick = { expanded = false; vm.load(1) })
                    DropdownMenuItem(text = { Text("保存到本地", fontSize = 17.sp) }, leadingIcon = { Icon(TiebaIcons.Save, null) }, onClick = { expanded = false; saveOptions = true })
                    DropdownMenuItem(text = { Text("复制链接", fontSize = 17.sp) }, leadingIcon = { Icon(TiebaIcons.Copy, null) }, onClick = {
                        expanded = false
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("帖子链接", link)); vm.inform("已复制链接")
                    })
                    DropdownMenuItem(text = { Text("浏览器打开", fontSize = 17.sp) }, leadingIcon = { Icon(TiebaIcons.Browser, null) }, onClick = { expanded = false; context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) })
                    DropdownMenuItem(text = { Text("分享", fontSize = 17.sp) }, leadingIcon = { Icon(TiebaIcons.Share, null) }, onClick = { expanded = false; context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link), "分享帖子")) })
                    if (vm.account != null && vm.data.posts.any { it.floor == 1 && it.author.id == vm.account?.uid }) DropdownMenuItem(text = { Text("删除本人主题", color = MaterialTheme.colorScheme.error) }, onClick = { expanded = false; onDelete() })
                }
            }
        }
    }
    if (saveOptions) AlertDialog(onDismissRequest = { saveOptions = false }, title = { Text("保存到本地", fontSize = 17.sp) }, text = {
        Column { listOf("仅保存正文和楼中楼", "保存正文和图片", "保存完整媒体").forEachIndexed { mode, title -> TextButton(onClick = { saveOptions = false; vm.saveThread(mode) }) { Text(title) } } }
    }, confirmButton = { TextButton(onClick = { saveOptions = false }) { Text("取消") } })
}
