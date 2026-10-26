package dev.anyu.tiebapure

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import coil.ImageLoader
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.collect
import org.json.JSONObject

private val mediaClient = okhttp3.OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
    .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS).readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
    .addInterceptor { chain ->
        require(Policy.mediaUrl(chain.request().url.toString()) != null) { "不受信任的媒体地址" }
        chain.proceed(chain.request())
    }.build()

class MainActivity : ComponentActivity() {
    private val model: AppViewModel by viewModels()
    private val export = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching { val bytes = model.store.backup(); contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("无法打开文件"); model.inform("备份已导出") }.onFailure { runCatching { contentResolver.delete(uri, null, null) }; model.report("备份导出失败：${it.message}") }
    }
    private val restore = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val bytes = contentResolver.openInputStream(uri)?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) { val count = input.read(buffer); if (count < 0) break; require(output.size() + count <= 8 * 1024 * 1024) { "备份超过 8 MB" }; output.write(buffer, 0, count) }
                output.toByteArray()
            } ?: error("无法打开文件")
            model.store.restore(bytes); model.load()
        }.onFailure { model.report("备份导入失败：${it.message}") }
    }
    private val importFont = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lifecycleScope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val name = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else "导入字体" } ?: "导入字体"
                    model.fontStore.importFont(contentResolver.openInputStream(uri) ?: error("无法打开字体"), name)
                }
                model.refreshFonts(); model.inform("字体已导入")
            } catch (e: Exception) { model.report(e.message ?: "字体导入失败") }
        }
    }
    private val saveMediaDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val prepared = model.pendingExport
        model.pendingExport = null
        if (uri != null && prepared != null) lifecycleScope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { contentResolver.openOutputStream(uri)?.use { out -> prepared.file.inputStream().use { it.copyTo(out) } } ?: error("无法写入所选文件") }
                model.inform("媒体已保存")
            } catch (e: Exception) { runCatching { contentResolver.delete(uri, null, null) }; model.report("保存失败，请重试") }
            finally { prepared.file.delete() }
        } else prepared?.file?.delete()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        coil.Coil.setImageLoader(ImageLoader.Builder(this).okHttpClient(mediaClient).build())
        handleIntent(intent)
        setContent {
            val vm = model
            val dark = when (vm.darkMode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
            MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xff90b9ff), secondary = Color(0xff90b9ff), secondaryContainer = Color(0xff163565)) else lightColorScheme(primary = Color(0xff1769db), secondary = Color(0xff1769db), secondaryContainer = Color(0xffe2edff), surface = Color.White, surfaceContainer = Color.White, background = Color(0xfff5f6f8))) {
                AppScreen(vm, { export.launch("TiebaLite-backup.json") }, { restore.launch(arrayOf("application/json", "application/octet-stream")) })
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleIntent(intent) }
    override fun onResume() { super.onResume(); model.signAll(automatic = true) }
    fun chooseFont() { importFont.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream")) }
    fun saveMedia(block: Block) {
        lifecycleScope.launch {
            var prepared: PreparedExport? = null
            try {
                model.inform("正在准备媒体文件…")
                prepared = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { model.offline.prepareExport(block) }
                val item = prepared
                if (android.os.Build.VERSION.SDK_INT < 29) { model.pendingExport = item; saveMediaDocument.launch(item.name); prepared = null; return@launch }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val collection = when (item.folder) { "Pictures" -> android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI; "Movies" -> android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI; else -> android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI }
                    val values = android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, item.name); put(android.provider.MediaStore.MediaColumns.MIME_TYPE, item.mime); put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, item.folder + "/TiebaLite"); put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1) }
                    val uri = contentResolver.insert(collection, values) ?: error("无法创建媒体文件")
                    try {
                        contentResolver.openOutputStream(uri)?.use { out -> item.file.inputStream().use { it.copyTo(out) } } ?: error("无法写入媒体库")
                        contentResolver.update(uri, android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                    } catch (e: Exception) { contentResolver.delete(uri, null, null); throw e }
                }
                model.inform("已保存到 ${item.folder}/TiebaLite")
            } catch (e: Exception) { model.report(e.message ?: "媒体保存失败") }
            finally { prepared?.file?.delete() }
        }
    }
    private fun handleIntent(intent: Intent?) {
        val raw = intent?.dataString ?: intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (raw != null) {
            val url = Regex("(?:https://|tiebalite://)[^\\s]+").find(raw)?.value ?: raw
            Policy.route(url)?.let { model.navigate(it) } ?: model.report("这个链接不能作为贴吧帖子或贴吧打开")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AppScreen(vm: AppViewModel, export: () -> Unit, restore: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var blockDialog by remember { mutableStateOf(false) }
    var blockText by remember { mutableStateOf("") }
    var blockType by remember { mutableStateOf("keyword") }
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmDelete by remember(vm.route) { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val r = vm.route
    var listFilter by remember(r) { mutableStateOf("") }
    var readingFilter by remember(r) { mutableIntStateOf(0) }
    var managing by remember(r) { mutableStateOf(false) }
    var selected by remember(r) { mutableStateOf(emptySet<String>()) }
    var confirmBatch by remember(r) { mutableStateOf(false) }
    val isLibrary = r.kind in setOf("history", "saved", "favorites")
    val readIds = if (isLibrary) vm.store.list("position").map { it.first.removePrefix("offline:") }.toSet() else emptySet()
    val shownThreads = vm.data.threads.filter { t ->
        (!isLibrary || listFilter.isBlank() || (t.title + t.author.name + t.forum).contains(listFilter.trim(), true)) &&
            (readingFilter == 0 || readingFilter == 1 && t.id !in readIds || readingFilter == 2 && t.id in readIds)
    }
    val context = LocalContext.current
    LaunchedEffect(vm.notice) { if (vm.notice.isNotBlank()) { snackbar.showSnackbar(vm.notice); vm.clearNotice() } }
    BackHandler(enabled = r.kind !in setOf("home", "forums", "me")) { vm.back() }
    val title = when (r.kind) {
        "home" -> context.getString(dev.anyu.tiebapure.R.string.app_name)
        "forums" -> "进吧"
        "me" -> "我的"
        "forum" -> "${r.id}吧"
        "thread", "offline" -> vm.data.thread?.thread?.title ?: "帖子详情"
        "search" -> "搜索：${r.id}"
        "subposts" -> "楼中楼"
        "history" -> "浏览历史"
        "saved" -> "本地保存"
        "favorites" -> "账号收藏"
        "messages" -> if (r.extra == "at") "@我的" else "回复我的"
        "settings" -> "设置"
        "block" -> "屏蔽管理"
        "about" -> "关于 TiebaLite"
        "login" -> "登录百度账号"
        else -> r.title
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        TopAppBar(title = { Text(title, maxLines = 1) }, navigationIcon = {
            if (r.kind !in setOf("home", "forums", "me")) IconButton(onClick = { vm.back() }) { Icon(Icons.Default.ArrowBack, "返回") }
        }, actions = {
            if (r.kind !in setOf("login", "settings", "about", "me", "block")) IconButton(onClick = { vm.load() }) { Icon(Icons.Default.Refresh, "刷新") }
            if (r.kind == "thread") {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "帖子操作") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("保存整帖正文和楼中楼") }, onClick = { menu = false; vm.saveThread(0) })
                    DropdownMenuItem(text = { Text("离线保存正文和图片") }, onClick = { menu = false; vm.saveThread(1) })
                    DropdownMenuItem(text = { Text("离线保存正文和完整媒体") }, onClick = { menu = false; vm.saveThread(2) })
                    DropdownMenuItem(text = { Text("收藏到贴吧账号") }, onClick = { menu = false; vm.action { a -> vm.api.favorite(a, r.id, vm.data.posts.firstOrNull()?.id ?: "0", true); "收藏成功" } })
                    DropdownMenuItem(text = { Text("取消账号收藏") }, onClick = { menu = false; vm.action { a -> vm.api.favorite(a, r.id, "0", false); "已取消收藏" } })
                    DropdownMenuItem(text = { Text("分享帖子链接") }, onClick = { menu = false; context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "https://tieba.baidu.com/p/${r.id}"), "分享")) })
                    if (vm.account != null && vm.data.thread?.posts?.any { it.floor == 1 && it.author.id == vm.account?.uid } == true) DropdownMenuItem(text = { Text("删除本人主题") }, onClick = { menu = false; confirmDelete = true })
                }
            }
        })
    }, bottomBar = {
        if (r.kind in setOf("home", "forums", "me")) NavigationBar {
            listOf(Triple("home", "首页", Icons.Default.Home), Triple("forums", "进吧", Icons.Default.List), Triple("me", "我的", Icons.Default.Person)).forEach { (kind, label, icon) ->
                NavigationBarItem(selected = r.kind == kind, onClick = { if (r.kind == kind) vm.load() else vm.navigate(Route(kind), root = true) }, icon = { Icon(icon, label) }, label = { Text(label) })
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (vm.saving) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { Text(vm.saveProgress.ifBlank { "正在准备本地保存…" }, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); TextButton(onClick = vm::cancelSave) { Text("取消") } }
            if (vm.error.isNotBlank()) Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(vm.error, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = { vm.load() }) { Text("重试") }
                }
            }
            when (r.kind) {
                "login" -> LoginView(vm) { vm.navigate(Route("me"), root = true) }
                "profile" -> ProfileScreen(vm)
                "editprofile" -> EditProfileScreen(vm)
                "following", "followers" -> RelationshipScreen(vm)
                "compose" -> ComposerScreen(vm)
                "me" -> LazyColumn {
                    item { Text(vm.account?.name ?: "访客", Modifier.padding(20.dp), style = MaterialTheme.typography.headlineSmall) }
                    item { MenuRow(if (vm.account == null) "登录百度账号" else "退出登录") { if (vm.account == null) vm.navigate(Route("login")) else confirmLogout = true } }
                    if (vm.account != null) item { MenuRow("我的个人资料") { vm.navigate(Route("profile", vm.account!!.uid, vm.account!!.name)) } }
                    item { MenuRow("回复我的") { vm.navigate(Route("messages")) } }
                    item { MenuRow("@我的") { vm.navigate(Route("messages", extra = "at")) } }
                    item { MenuRow("账号收藏") { vm.navigate(Route("favorites")) } }
                    item { MenuRow("浏览历史") { vm.navigate(Route("history")) } }
                    item { MenuRow("本地保存") { vm.navigate(Route("saved")) } }
                    item { MenuRow("本机草稿") { vm.navigate(Route("drafts", title = "本机草稿")) } }
                    item { MenuRow("屏蔽管理") { vm.navigate(Route("block")) } }
                    item { MenuRow("设置") { vm.navigate(Route("settings")) } }
                    item { MenuRow("关于与开源许可") { vm.navigate(Route("about")) } }
                }
                "settings" -> Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Text("外观", style = MaterialTheme.typography.titleMedium)
                    Row { listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (value, label) -> FilterChip(selected = vm.darkMode == value, onClick = { vm.setSettings(theme = value) }, label = { Text(label) }, modifier = Modifier.padding(end = 8.dp)) } }
                    Text("正文字号 ${vm.fontSize.toInt()}", Modifier.padding(top = 16.dp))
                    Slider(value = vm.fontSize, onValueChange = { vm.setSettings(size = it) }, valueRange = 12f..26f, steps = 13)
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("手动加载媒体", Modifier.weight(1f)); Switch(checked = vm.manualMedia, onCheckedChange = { vm.setSettings(media = it) }) }
                    var autoSign by remember { mutableStateOf(vm.store.prefs.getBoolean("auto-sign", false)) }
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("每天首次打开时签到", Modifier.weight(1f)); Switch(checked = autoSign, onCheckedChange = { autoSign = it; vm.store.prefs.edit().putBoolean("auto-sign", it).apply() }) }
                    OutlinedButton(onClick = { (context as? MainActivity)?.chooseFont() }, modifier = Modifier.fillMaxWidth()) { Text("导入本机 TTF/OTF 阅读字体") }
                    TextButton(onClick = { vm.fontStore.select(""); vm.refreshFonts() }) { Text("使用系统阅读字体") }
                    vm.fonts.forEach { font -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { vm.fontStore.select(font.file); vm.refreshFonts() }, modifier = Modifier.weight(1f)) { Text(font.name) }
                        IconButton(onClick = { vm.fontStore.remove(font); vm.refreshFonts() }) { Icon(Icons.Default.Delete, "移除字体") }
                    } }
                    Text("备份包含历史、阅读页码、本地正文与屏蔽；不包含账号凭据、草稿和离线媒体。", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = export, Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("导出本地备份") }
                    OutlinedButton(onClick = restore, Modifier.fillMaxWidth()) { Text("合并导入本地备份") }
                }
                "about" -> AboutScreen()
                "block" -> {
                    Button(onClick = { blockDialog = true }, Modifier.padding(16.dp)) { Text("添加屏蔽") }
                    var revision by remember { mutableIntStateOf(0) }
                    val blocks = remember(revision, blockDialog) { vm.store.list("block") }
                    LazyColumn { items(blocks, key = { it.first }) { (id, j) -> Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Text("${j.optString("type")}：${j.optString("value")}", Modifier.weight(1f)); IconButton(onClick = { vm.store.remove("block", id); revision++ }) { Icon(Icons.Default.Delete, "删除屏蔽") } } } }
                }
                "drafts" -> {
                    var revision by remember { mutableIntStateOf(0) }
                    val rows = remember(vm.account?.uid, revision) { vm.store.list("draft").filter { it.second.optString("account") == vm.account?.uid && it.second.has("target") } }
                    LazyColumn {
                        if (rows.isEmpty()) item { EmptyMessage("当前账号没有本机草稿") }
                        items(rows, key = { it.first }) { (id, j) -> Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(j.optString("title").ifBlank { j.optString("text").take(60) }, Modifier.weight(1f).clickable { vm.navigate(Route("compose", j.getString("forum"), "编辑草稿", j.getString("target"))) })
                            IconButton(onClick = { vm.store.remove("draft", id); revision++ }) { Icon(Icons.Default.Delete, "删除草稿") }
                        } }
                    }
                }
                "thread", "subposts", "offline" -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    if (maxWidth >= 840.dp && vm.contextThreads.isNotEmpty()) Row(Modifier.fillMaxSize()) {
                        LazyColumn(Modifier.weight(0.38f).fillMaxHeight()) { items(vm.contextThreads, key = { it.id + ":" + it.matchedPost }) { t -> ThreadRow(t, vm.fontSize, !vm.manualMedia) { vm.navigate(Route("thread", t.id, t.title, t.matchedPost)) } } }
                        VerticalDivider()
                        Box(Modifier.weight(0.62f)) { PostScreen(vm) }
                    } else PostScreen(vm)
                }
                else -> {
                    if (isLibrary) {
                        OutlinedTextField(value = listFilter, onValueChange = { listFilter = it }, singleLine = true, label = { Text(if (r.kind == "favorites") "筛选已加载收藏" else "搜索本机列表") }, modifier = Modifier.fillMaxWidth().padding(12.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            listOf("全部", "未读", "读过").forEachIndexed { index, title -> FilterChip(selected = readingFilter == index, onClick = { readingFilter = index }, label = { Text(title) }, modifier = Modifier.padding(end = 8.dp)) }
                            TextButton(onClick = { managing = !managing; selected = emptySet() }) { Text(if (managing) "取消管理" else "批量管理") }
                        }
                        if (managing) Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { selected = shownThreads.map { it.id }.toSet() }) { Text("全选当前结果") }
                            Text("已选 ${selected.size}", Modifier.weight(1f))
                            TextButton(onClick = { confirmBatch = true }, enabled = selected.isNotEmpty()) { Text(if (r.kind == "favorites") "取消收藏" else "删除") }
                        }
                    }
                    if (r.kind in setOf("home", "forums", "forum", "search")) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = query, onValueChange = { if (it.length <= 500) query = it else vm.report("关键词最多 500 字") }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text(if (r.kind == "forums") "输入吧名" else "搜索贴吧内容") })
                            TextButton(onClick = { if (query.isNotBlank()) vm.navigate(if (r.kind == "forums") Route("forum", query.trim().removeSuffix("吧")) else Route("search", query.trim(), extra = if (r.kind == "forum") r.id else "")) }) { Text(if (r.kind == "forums") "进吧" else "搜索") }
                        }
                    }
                    if (r.kind == "forum") Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                        FilterChip(selected = vm.sort == 0 && r.extra != "good", onClick = { vm.sort = 0; vm.load() }, label = { Text("最新回复") })
                        FilterChip(selected = vm.sort == 1, onClick = { vm.sort = 1; vm.load() }, label = { Text("最新发布") }, modifier = Modifier.padding(start = 8.dp))
                        FilterChip(selected = r.extra == "good", onClick = { vm.navigate(r.copy(extra = "good")) }, label = { Text("精华") }, modifier = Modifier.padding(start = 8.dp))
                        TextButton(onClick = { vm.compose(r.id) }) { Text("发帖") }
                        TextButton(onClick = { vm.action { a -> vm.api.followForum(a, r.id, true); "已关注贴吧" } }) { Text("关注") }
                        TextButton(onClick = { vm.action { a -> vm.api.followForum(a, r.id, false); "已取消关注贴吧" } }) { Text("取消关注") }
                    }
                    LazyColumn(Modifier.weight(1f)) {
                        if (r.kind == "forums") {
                            if (vm.account != null) item { OutlinedButton(onClick = { vm.signAll() }, modifier = Modifier.padding(16.dp)) { Text("一键签到") } }
                            if (vm.data.forums.isEmpty() && !vm.loading && vm.error.isBlank()) item { EmptyMessage(if (vm.account == null) "输入吧名即可浏览，登录后显示关注的贴吧" else "还没有关注的贴吧") }
                            items(vm.data.forums, key = { it.first + ":" + it.second }) { (id, name) -> Row(Modifier.fillMaxWidth().clickable { vm.navigate(Route("forum", name)) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${name}吧", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                if (vm.account != null && id.toLongOrNull()?.let { it > 0 } == true) TextButton(onClick = { vm.action { a -> vm.api.signForum(a, id, name) } }) { Text("签到") }
                            }; HorizontalDivider() }
                        } else {
                            if (shownThreads.isEmpty() && !vm.loading && vm.error.isBlank()) item { EmptyMessage(if (listFilter.isBlank()) "暂无内容" else "没有匹配结果") }
                            items(shownThreads, key = { it.id + ":" + it.matchedPost }) { thread -> Row(verticalAlignment = Alignment.CenterVertically) {
                                if (managing) Checkbox(checked = thread.id in selected, onCheckedChange = { selected = if (it) selected + thread.id else selected - thread.id })
                                ThreadRow(thread, vm.fontSize, !vm.manualMedia) {
                                    if (managing) selected = if (thread.id in selected) selected - thread.id else selected + thread.id
                                    else if (r.kind == "saved") vm.offlineThread(thread.id)
                                    else vm.navigate(Route("thread", thread.id, thread.title, thread.matchedPost))
                                }
                            } }
                            if (vm.data.hasMore && r.kind !in setOf("history", "saved")) item { OutlinedButton(onClick = { vm.load(vm.page + 1, append = true) }, enabled = !vm.loading, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("加载下一页") } }
                        }
                    }
                }
            }
        }
    }
    if (confirmLogout) AlertDialog(onDismissRequest = { confirmLogout = false }, title = { Text("退出登录？") }, text = { Text("将清除本机凭据、网页 Cookie 和登录网页数据。") }, confirmButton = { TextButton(onClick = { confirmLogout = false; vm.logout() }) { Text("退出") } }, dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("取消") } })
    if (confirmBatch) AlertDialog(onDismissRequest = { confirmBatch = false }, title = { Text(if (r.kind == "favorites") "取消选中的账号收藏？" else "删除选中的本机记录？") }, text = { Text("将处理 ${selected.size} 条记录。${if (r.kind == "saved") "已下载的对应离线媒体也会删除。" else ""}") }, confirmButton = { TextButton(onClick = { confirmBatch = false; vm.removeEntries(r.kind, selected) { selected = emptySet(); managing = false } }) { Text("确认") } }, dismissButton = { TextButton(onClick = { confirmBatch = false }) { Text("取消") } })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("删除本人主题？") }, text = { Text("这会向贴吧提交永久删除请求。请确认当前主题确实需要删除。") }, confirmButton = { TextButton(onClick = {
        confirmDelete = false
        val target = vm.data.thread
        if (target != null) vm.action(onSuccess = { vm.navigate(Route("profile", vm.account?.uid.orEmpty(), "我的资料")) }) { a -> vm.api.deleteOwnThread(a, target); "主题删除结果已确认" }
    }) { Text("确认删除") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } })
    if (blockDialog) AlertDialog(onDismissRequest = { blockDialog = false }, title = { Text("添加屏蔽") }, text = { Column {
        Row { listOf("keyword" to "关键词", "user" to "用户", "forum" to "贴吧").forEach { (key, text) -> FilterChip(selected = blockType == key, onClick = { blockType = key }, label = { Text(text) }) } }
        OutlinedTextField(value = blockText, onValueChange = { if (it.length <= 512) blockText = it else vm.report("屏蔽内容最多 512 字") }, label = { Text("屏蔽内容") })
    } }, confirmButton = { TextButton(onClick = { if (blockText.isNotBlank()) {
        val id = blockType + ":" + java.security.MessageDigest.getInstance("SHA-256").digest(blockText.trim().toByteArray()).joinToString("") { "%02x".format(it) }
        runCatching { vm.store.put("block", id, JSONObject().put("type", blockType).put("value", blockText.trim())) }.onSuccess { blockDialog = false; blockText = "" }.onFailure { vm.report("屏蔽未能保存，请检查本机空间") }
    } }) { Text("添加") } }, dismissButton = { TextButton(onClick = { blockDialog = false }) { Text("取消") } })
}

@Composable fun MenuRow(title: String, onClick: () -> Unit) { Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) { Text(title, Modifier.padding(horizontal = 20.dp, vertical = 16.dp)); HorizontalDivider() } }
@Composable fun EmptyMessage(text: String) { Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable fun ThreadRow(thread: Thread, size: Float, loadMedia: Boolean, click: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = click).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Policy.avatar(thread.author.portrait)?.let { AsyncImage(model = it, contentDescription = null, modifier = Modifier.size(30.dp).clip(androidx.compose.foundation.shape.CircleShape)); Spacer(Modifier.width(8.dp)) }
            Text(thread.author.name + if (thread.forum.isBlank()) "" else " · ${thread.forum}吧", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Text(thread.title, fontSize = size.sp, modifier = Modifier.padding(vertical = 8.dp))
        if (thread.summary.isNotBlank()) Text(thread.summary, maxLines = 3, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = (size - 2).sp)
        if (loadMedia && thread.previews.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            thread.previews.take(3).forEach { url -> AsyncImage(model = url, contentDescription = "帖子预览图", contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.weight(1f).height(105.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))) }
        }
        Text("${thread.replies} 回复", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
    }; HorizontalDivider()
}

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable fun PostScreen(vm: AppViewModel) {
    val p = vm.data.thread
    val r = vm.route
    if (vm.loading && vm.data.posts.isEmpty()) return
    key(r.kind, r.id, r.extra, vm.page) {
    val positionId = if (r.kind == "offline") "offline:${r.id}" else r.id
    val stored = remember(positionId) { vm.store.get("position", positionId) }
    val scroll = rememberLazyListState(stored?.optInt("index", 0)?.coerceIn(0, vm.data.posts.size + 2) ?: 0, stored?.optInt("offset", 0)?.coerceIn(0, 1000000) ?: 0)
    LaunchedEffect(scroll, r.kind, vm.page) {
        if (r.kind in setOf("thread", "offline")) snapshotFlow { scroll.firstVisibleItemIndex to scroll.firstVisibleItemScrollOffset }.drop(1).distinctUntilChanged().debounce(300).collect { (index, offset) ->
            try { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { vm.store.put("position", positionId, JSONObject().put("page", vm.page).put("index", index).put("offset", offset).put("total-pages", p?.pages ?: 1)) } }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { vm.report("阅读位置未能保存，请检查本机剩余空间") }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), state = scroll) {
        if (p != null) item {
            Column(Modifier.padding(16.dp)) {
                Text(p.thread.title, style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { vm.navigate(Route("forum", p.thread.forum)) }) { Text("${p.thread.forum}吧") }
                if (r.kind == "thread") Row {
                    FilterChip(selected = vm.onlyAuthor, onClick = { vm.onlyAuthor = !vm.onlyAuthor; vm.load() }, label = { Text("只看楼主") })
                    FilterChip(selected = vm.sort == 1, onClick = { vm.sort = if (vm.sort == 1) 0 else 1; vm.load() }, label = { Text("倒序") }, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        items(vm.data.posts, key = { it.id }) { post ->
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("${post.author.name} · ${post.floor}楼", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { if (post.author.id.toLongOrNull()?.let { it > 0 } == true) vm.navigate(Route("profile", post.author.id, post.author.name)) })
                SelectionContainer { Column { post.blocks.forEach { block -> ContentBlockView(block, vm) } } }
                if (r.kind == "thread") Row {
                    var liked by remember(post.id) { mutableStateOf(post.liked) }
                    TextButton(onClick = { vm.action(onSuccess = { liked = !liked }) { a -> vm.api.like(a, r.id, post.id, if (post.floor == 1) LikeObject.THREAD else LikeObject.POST, !liked); if (!liked) "已点赞" else "已取消点赞" } }) { Text(if (liked) "已赞" else "赞") }
                    if (p != null) TextButton(onClick = { vm.compose(p.thread.forum, r.id, post.id, post.floor) }) { Text("回复") }
                    if (post.subCount > 0 && p != null) TextButton(onClick = { vm.navigate(Route("subposts", r.id, title = p.thread.forum, extra = "${post.id}:${p.forumId}")) }) { Text("${post.subCount} 条楼中楼") }
                }
                if (r.kind == "offline") post.children.forEach { child ->
                    Column(Modifier.padding(start = 16.dp, top = 8.dp)) { Text(child.author.name, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium); child.blocks.forEach { ContentBlockView(it, vm) } }
                }
                if (r.kind == "subposts") Row {
                    var liked by remember(post.id) { mutableStateOf(post.liked) }
                    TextButton(onClick = { vm.action(onSuccess = { liked = !liked }) { a -> vm.api.like(a, r.id, post.id, LikeObject.SUBPOST, !liked); if (!liked) "已点赞" else "已取消点赞" } }) { Text(if (liked) "已赞" else "赞") }
                    TextButton(onClick = { vm.compose(r.title, r.id, r.extra.substringBefore(':'), subpost = post.id, replyTo = post.author.name) }) { Text("回复此条") }
                }
            }; HorizontalDivider()
        }
        if (r.kind == "thread" && p != null) item { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { vm.load((vm.page - 1).coerceAtLeast(1)) }, enabled = vm.page > 1 && !vm.loading) { Text("上一页") }
            Text("${vm.page} / ${p.pages}")
            OutlinedButton(onClick = { vm.load(vm.page + 1) }, enabled = vm.page < p.pages && !vm.loading) { Text("下一页") }
        } }
        if (r.kind == "thread" && p != null) item { Button(onClick = { vm.compose(p.thread.forum, r.id) }, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("回复帖子") } }
        if (r.kind == "subposts" && vm.data.hasMore) item { OutlinedButton(onClick = { vm.load(vm.page + 1, true) }, enabled = !vm.loading, modifier = Modifier.padding(16.dp)) { Text("更多楼中楼") } }
    }
    }
}

@Composable fun ContentBlockView(block: Block, vm: AppViewModel) {
    val trustedUrl = Policy.mediaUrl(block.url)
    val context = LocalContext.current
    val local = remember(block.localFile) { OfflineLibrary.safeFile(context, block.localFile) }
    var loaded by remember(block.url, block.localFile) { mutableStateOf(local != null || !vm.manualMedia && vm.route.kind != "offline") }
    var preview by remember(block.url) { mutableStateOf(false) }
    when {
        block.type == 1 && Policy.webpage(block.url) != null -> TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(block.url))) }) { Text(block.text.ifBlank { block.url }) }
        block.type == 4 && block.url.startsWith("user:") -> TextButton(onClick = { val id = block.url.removePrefix("user:"); if (id.toLongOrNull()?.let { it > 0 } == true) vm.navigate(Route("profile", id, block.text)) }) { Text(block.text) }
        trustedUrl != null && block.type == 2 -> AsyncImage(model = local ?: trustedUrl, contentDescription = block.text, modifier = Modifier.size(28.dp))
        trustedUrl != null && block.type in setOf(3, 20) -> {
            if (!loaded) TextButton(onClick = { loaded = true }) { Text("加载图片") }
            else AsyncImage(model = local ?: trustedUrl, contentDescription = "帖子图片，点击放大", modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp, max = 360.dp).padding(vertical = 8.dp).clickable { preview = true })
            if (preview) androidx.compose.ui.window.Dialog(onDismissRequest = { preview = false }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
                var scale by remember { mutableFloatStateOf(1f) }
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    AsyncImage(model = local ?: trustedUrl, contentDescription = "放大图片", modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale).pointerInput(Unit) { detectTransformGestures { _, _, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 6f) } })
                    TextButton(onClick = { preview = false }, modifier = Modifier.align(Alignment.TopEnd).padding(24.dp)) { Text("关闭", color = Color.White) }
                    TextButton(onClick = { (context as? MainActivity)?.saveMedia(block) }, modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)) { Text("保存图片", color = Color.White) }
                }
            }
        }
        trustedUrl != null && block.type in setOf(5, 10) -> {
            Row { TextButton(onClick = { preview = true }) { Text(if (block.type == 10) "播放语音" else "播放视频") }; TextButton(onClick = { (context as? MainActivity)?.saveMedia(block) }) { Text("保存") } }
            if (preview) androidx.compose.ui.window.Dialog(onDismissRequest = { preview = false }) { MediaPlayer(local?.toURI()?.toString() ?: block.url) }
        }
        else -> Text(block.text.ifBlank { if (block.url.isNotBlank()) block.url else "" }, fontSize = vm.fontSize.sp, fontFamily = vm.readerTypeface?.let { androidx.compose.ui.text.font.FontFamily(it) }, modifier = Modifier.padding(vertical = 3.dp))
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable fun MediaPlayer(url: String) {
    val context = LocalContext.current
    require(Policy.mediaUrl(url) != null || runCatching { java.io.File(java.net.URI(url)).canonicalPath.startsWith(java.io.File(context.filesDir, "offline").canonicalPath + "/") }.getOrDefault(false))
    val player = remember(url) { androidx.media3.exoplayer.ExoPlayer.Builder(context)
        .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(androidx.media3.datasource.DefaultDataSource.Factory(context, androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(mediaClient)))).build().apply {
        setAudioAttributes(androidx.media3.common.AudioAttributes.Builder().setUsage(androidx.media3.common.C.USAGE_MEDIA).setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        setMediaItem(androidx.media3.common.MediaItem.fromUri(url)); prepare(); playWhenReady = true
    } }
    AndroidView(factory = { androidx.media3.ui.PlayerView(it).apply { this.player = player } }, modifier = Modifier.fillMaxWidth().height(300.dp))
    DisposableEffect(player) {
        val owner = context as? androidx.lifecycle.LifecycleOwner
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) player.pause() }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer); player.release() }
    }
}

@Composable fun AboutScreen() {
    val context = LocalContext.current
    var license by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("TiebaLite", style = MaterialTheme.typography.headlineMedium)
        Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", Modifier.padding(vertical = 12.dp))
        Text("基于 infinityf4p/TiebaPure-iOS 的独立 Android 移植版。原项目采用 GPL-3.0-only，本衍生项目保留原作者、版权与许可。与百度及贴吧官方无隶属、授权或认可关系。")
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/infinityf4p/TiebaPure-iOS"))) }) { Text("查看原项目源码") }
        TextButton(onClick = { license = context.assets.open("LICENSE").bufferedReader().use { it.readText() } }) { Text("查看 GPL-3.0-only") }
        TextButton(onClick = { license = context.assets.open("THIRD_PARTY_NOTICES.md").bufferedReader().use { it.readText() } }) { Text("第三方依赖与版权材料") }
        TextButton(onClick = { license = context.assets.open("licenses/Apache-2.0.txt").bufferedReader().use { it.readText() } }) { Text("Apache-2.0 许可证") }
        TextButton(onClick = { license = context.assets.open("licenses/Protobuf-BSD-3-Clause.txt").bufferedReader().use { it.readText() } }) { Text("Protobuf BSD-3-Clause 许可证") }
        if (license.isNotBlank()) SelectionContainer { Text(license, style = MaterialTheme.typography.bodySmall) }
    }
}
