package dev.anyu.tiebapure

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun commentLikeCount(count: Long): String = when {
    count >= 1_000_000 -> String.format(Locale.ROOT, "%.1fm", count / 1_000_000.0)
    count >= 1_000 -> String.format(Locale.ROOT, "%.1fk", count / 1_000.0)
    else -> count.coerceAtLeast(0).toString()
}

internal fun commentMetadata(timestamp: Long, region: String): String {
    val date = if (timestamp in 1..253402300799L) Instant.ofEpochSecond(timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd")) else ""
    return listOf(date, region.removePrefix("IP属地：").removePrefix("IP属地:").trim()).filter(String::isNotBlank).joinToString("  ")
}

@Composable fun ReplyControls(vm: AppViewModel) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(false to "全部回复", true to "只看楼主").forEach { (value, label) ->
            Text(label, fontSize = 15.sp, fontWeight = if (vm.onlyAuthor == value) FontWeight.SemiBold else FontWeight.Normal, color = if (vm.onlyAuthor == value) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.heightIn(min = 44.dp).wrapContentHeight().clickable { if (vm.onlyAuthor != value) { vm.onlyAuthor = value; vm.load(1) } }.padding(end = 16.dp))
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.width(142.dp)) {
            listOf(2 to "热门", 0 to "正序", 1 to "倒序").forEach { (value, label) ->
                Box(Modifier.weight(1f).height(44.dp).background(if (vm.sort == value) MaterialTheme.colorScheme.surface else Color.Transparent, RoundedCornerShape(50)).clickable { if (vm.sort != value) { vm.sort = value; vm.load(1) } }.semantics { selected = vm.sort == value; role = Role.RadioButton }, contentAlignment = Alignment.Center) {
                    Text(label, fontSize = 14.sp, fontWeight = if (vm.sort == value) FontWeight.SemiBold else FontWeight.Normal, color = if (vm.sort == value) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable fun ReplyCommentRow(vm: AppViewModel, post: Post) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val route = vm.route
    val offline = route.kind == "offline"
    var liked by remember(post.id, post.liked) { mutableStateOf(post.liked) }
    var expanded by remember(post.id) { mutableStateOf(false) }
    val count = post.likes?.let { (it + if (liked == post.liked) 0 else if (liked) 1 else -1).coerceAtLeast(0) }
    val openUser = { if (post.author.id.toLongOrNull()?.let { it > 0 } == true) vm.navigate(Route("profile", post.author.id, post.author.name)) }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 12.dp).semantics { contentDescription = "回复：${post.author.name}" }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clickable(onClick = openUser), contentAlignment = Alignment.CenterStart) { IOSAvatar(post.author.name, post.author.portrait, diameter = 36.dp) }
            Spacer(Modifier.width(4.dp))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(post.author.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).clickable(onClick = openUser))
                if (post.authorLevel > 0) Text(if (post.authorLevelName.isBlank()) "Lv.${post.authorLevel}" else "${post.authorLevel} ${post.authorLevelName.replace("\n", " ")}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp).widthIn(max = 110.dp).background(Color(0xFFFFA448).copy(alpha = .2f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp))
            }
            Row(Modifier.heightIn(min = 44.dp).clickable(enabled = !offline && vm.likesEnabled) {
                val desired = !liked
                vm.action(onSuccess = { liked = desired }) { account -> vm.api.like(account, route.id, post.id, if (route.kind == "subposts") LikeObject.SUBPOST else LikeObject.POST, desired); if (desired) "已点赞" else "已取消点赞" }
            }.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ThumbUp, if (liked) "取消点赞" else "点赞", tint = if (liked) IOSLayout.blue else secondary, modifier = Modifier.size(18.dp))
                Text(count?.let(::commentLikeCount) ?: "赞", fontSize = 14.sp, color = if (liked) IOSLayout.blue else secondary, modifier = Modifier.padding(start = 5.dp))
            }
        }
        Column(Modifier.padding(start = 48.dp)) {
            SelectionContainer { Column { post.blocks.forEach { ContentBlockView(it, vm) } } }
            val metadata = commentMetadata(post.timestamp, post.ipAddress)
            if (metadata.isNotBlank()) Text(metadata, fontSize = 12.sp, color = secondary, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
            if (post.children.isNotEmpty() || !offline && post.subCount > 0 && route.kind == "thread") {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    post.children.take(if (expanded) Int.MAX_VALUE else 3).forEach { child ->
                        Text(buildAnnotatedString {
                            withStyle(SpanStyle(color = secondary)) { append(child.author.name + "：") }
                            append(child.blocks.joinToString("") { it.text.ifBlank { if (it.type == 3 || it.type == 20) "[图片]" else "[媒体]" } })
                        }, fontSize = 15.sp, lineHeight = 21.sp, maxLines = if (offline) Int.MAX_VALUE else 6, overflow = TextOverflow.Ellipsis)
                    }
                    if (offline && post.children.size > 3) Text(if (expanded) "收起回复" else "展开全部${post.children.size}条本地回复", color = IOSLayout.blue, fontSize = 13.sp, modifier = Modifier.heightIn(min = 44.dp).wrapContentHeight().clickable { expanded = !expanded })
                    else if (!offline && post.subCount > 0 && route.kind == "thread") Text("查看全部${post.subCount}条回复 ›", color = IOSLayout.blue, fontSize = 13.sp, modifier = Modifier.heightIn(min = 44.dp).wrapContentHeight().clickable {
                        vm.data.thread?.let { vm.navigate(Route("subposts", route.id, title = it.thread.forum, extra = "${post.id}:${it.forumId}")) }
                    })
                }
            }
            if (!offline && vm.repliesEnabled) TextButton(onClick = {
                if (route.kind == "subposts") vm.compose(route.title, route.id, route.extra.substringBefore(':'), subpost = post.id, replyTo = post.author.name)
                else vm.data.thread?.let { vm.compose(it.thread.forum, route.id, post.id, post.floor) }
            }) { Text("回复") }
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = .5.dp)
}
