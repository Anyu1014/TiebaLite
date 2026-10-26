package dev.anyu.tiebapure

import android.app.Application
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ScreenData(val threads: List<Thread> = emptyList(), val thread: ThreadPage? = null, val posts: List<Post> = emptyList(), val forums: List<Pair<String, String>> = emptyList(), val profile: Profile? = null, val users: List<User> = emptyList(), val hasMore: Boolean = false)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val store = LocalStore(app)
    val api = TiebaApi(
        store.prefs.getString("client-id", null) ?: UUID.randomUUID().toString().replace("-", "").also { store.prefs.edit().putString("client-id", it).apply() },
        app.resources.displayMetrics.widthPixels, app.resources.displayMetrics.heightPixels, app.resources.displayMetrics.density.toDouble(),
        android.os.Build.VERSION.RELEASE, android.os.Build.MODEL, android.os.Build.MANUFACTURER
    )
    val offline = OfflineLibrary(app, api, store)
    val fontStore = FontStore(app)
    var readerTypeface by mutableStateOf(fontStore.typeface(fontStore.selected())); private set
    var fonts by mutableStateOf(fontStore.list()); private set
    var pendingExport: PreparedExport? = null
    var account by mutableStateOf<Account?>(null); private set
    var route by mutableStateOf(Route()); private set
    var data by mutableStateOf(ScreenData()); private set
    var contextThreads by mutableStateOf<List<Thread>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var refreshing by mutableStateOf(false); private set
    var submitting by mutableStateOf(false); private set
    var saving by mutableStateOf(false); private set
    var saveProgress by mutableStateOf(""); private set
    var signing by mutableStateOf(false); private set
    var error by mutableStateOf(""); private set
    var notice by mutableStateOf(""); private set
    var page by mutableIntStateOf(1); private set
    var postPages by mutableStateOf<Map<String, Int>>(emptyMap()); private set
    private var retryPage = 1
    private var retryAppend = false
    fun retryLoad() = load(retryPage, retryAppend)
    fun loadNextPage() {
        if (!loading && error.isBlank() && data.hasMore) load(page + 1, append = true)
    }
    var sort by mutableIntStateOf(0)
    var defaultReplySort by mutableIntStateOf(store.prefs.getInt("reader-reply-sort", 2).coerceIn(0, 2)); private set
    fun selectDefaultReplySort(value: Int) { require(value in 0..2); store.prefs.edit().putInt("reader-reply-sort", value).apply(); defaultReplySort = value }
    var onlyAuthor by mutableStateOf(false)
    var darkMode by mutableStateOf(store.prefs.getString("theme", "system") ?: "system")
    var fontSize by mutableFloatStateOf(store.prefs.getFloat("font-size", 17f))
    var manualMedia by mutableStateOf(store.prefs.getBoolean("manual-media", false))
    var newThreadsEnabled by mutableStateOf(store.prefs.getBoolean("allow-new-threads", true)); private set
    var repliesEnabled by mutableStateOf(store.prefs.getBoolean("allow-replies", false)); private set
    var likesEnabled by mutableStateOf(store.prefs.getBoolean("allow-likes", true)); private set
    fun setContentPreferences(newThreads: Boolean = newThreadsEnabled, replies: Boolean = repliesEnabled, likes: Boolean = likesEnabled) {
        store.prefs.edit().putBoolean("allow-new-threads", newThreads).putBoolean("allow-replies", replies).putBoolean("allow-likes", likes).apply()
        newThreadsEnabled = newThreads; repliesEnabled = replies; likesEnabled = likes
    }

    private val stack = mutableListOf<Route>()
    private var loadJob: Job? = null
    private var saveJob: Job? = null
    private val actions = mutableSetOf<Job>()
    private var generation = 0
    private var accountGeneration = 0
    private var loginJob: Job? = null
    private data class HomeSnapshot(val data: ScreenData, val page: Int, val error: String, val retryPage: Int, val retryAppend: Boolean)
    private var homeSnapshot: HomeSnapshot? = null

    init {
        runCatching { store.loadAccount() }.onSuccess { account = it }.onFailure { error = "本机登录资料无法解密，请重新登录" }
        load()
    }
    fun report(message: String) { error = message.take(300) }
    fun inform(message: String) { notice = message }
    fun refreshFonts() { fonts = fontStore.list(); readerTypeface = fontStore.typeface(fontStore.selected()) }
    fun clearNotice() { notice = "" }
    fun navigate(next: Route, root: Boolean = false) {
        if (route.kind == "home" && (!loading || data.threads.isNotEmpty())) homeSnapshot = HomeSnapshot(data, page, error, retryPage, retryAppend)
        if (next.kind == "forum" && next.id.trim().length in 1..100) store.put("forum", next.id, JSONObject().put("name", next.id))
        if (next.kind == "thread" && data.threads.isNotEmpty()) contextThreads = data.threads
        if (root) contextThreads = emptyList()
        if (root) stack.clear() else if (!(route.kind in setOf("search", "searchinput") && next.kind in setOf("search", "searchinput"))) stack.add(route)
        route = next
        if (next.kind == "home") homeSnapshot?.let { cached ->
            loadJob?.cancel(); generation++
            data = cached.data; page = cached.page; error = cached.error; loading = false; refreshing = false
            sort = 0; onlyAuthor = false; postPages = emptyMap()
            retryPage = cached.retryPage; retryAppend = cached.retryAppend
            return
        }
        page = if (next.kind == "thread" && next.extra.isBlank()) store.get("position", next.id)?.optInt("page", 1)?.coerceAtLeast(1) ?: 1 else 1
        sort = if (next.kind == "thread" && next.extra.isBlank() && store.get("position", next.id) == null) defaultReplySort else 0
        onlyAuthor = false
        data = ScreenData()
        load()
    }
    fun back(): Boolean {
        if (stack.isEmpty()) return false
        route = stack.removeAt(stack.lastIndex)
        data = ScreenData(); page = 1; sort = 0; onlyAuthor = false; load(); return true
    }
    fun load(requestedPage: Int = page, append: Boolean = false) {
        loadJob?.cancel()
        retryPage = requestedPage; retryAppend = append
        val current = ++generation
        val r = route; val a = account; val order = sort; val lz = onlyAuthor
        refreshing = !append && requestedPage == 1 && (data.threads.isNotEmpty() || data.posts.isNotEmpty())
        loading = true; error = ""
        loadJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    when (r.kind) {
                        "home" -> ScreenData(threads = api.home(a, requestedPage))
                        "forum" -> ScreenData(threads = api.forum(r.id, requestedPage, order, r.extra == "good"))
                        "search" -> ScreenData(threads = api.search(r.id, requestedPage, r.extra))
                        "thread" -> {
                            val p = api.thread(r.id, requestedPage, a, order == 1, lz, r.extra.takeIf(String::isNotBlank), hot = order == 2)
                            store.put("history", r.id, p.thread.json())
                            val position = store.get("position", r.id)?.takeIf { it.optInt("page") == p.page } ?: JSONObject()
                            if (r.extra.isNotBlank() && requestedPage <= 1) position.put("index", (p.posts.indexOfFirst { it.id == r.extra } + 1).coerceAtLeast(0)).put("offset", 0)
                            store.put("position", r.id, position.put("page", p.page))
                            ScreenData(thread = p, posts = p.posts, hasMore = p.page < p.pages)
                        }
                        "subposts" -> api.subposts(r.id, r.extra.substringBefore(':'), r.extra.substringAfter(':'), requestedPage, a).let { ScreenData(posts = it, hasMore = it.size >= 10) }
                        "forums" -> {
                            val local = store.list("forum").map { it.first to it.second.getString("name") }
                            val followed = if (a != null) api.followedForums(a) else emptyList()
                            ScreenData(forums = followed + local.filterNot { pair -> followed.any { it.second == pair.second } })
                        }
                        "history", "saved" -> ScreenData(threads = store.list(r.kind).map { threadFromJson(it.second) })
                        "favorites" -> ScreenData(threads = api.favorites(a ?: error("请先登录"), requestedPage))
                        "messages" -> ScreenData(threads = api.messages(a ?: error("请先登录"), r.extra == "at", requestedPage))
                        "profile", "editprofile" -> ScreenData(profile = api.profile(r.id, a))
                        "userthreads" -> ScreenData(threads = api.userThreads(r.id, a, requestedPage))
                        "following", "followers" -> ScreenData(users = api.relationships(r.id, a, r.kind == "followers", requestedPage))
                        else -> ScreenData()
                    }.let { result ->
                        val more = when (r.kind) {
                            "home", "forum", "search", "favorites", "messages", "userthreads" -> result.threads.isNotEmpty()
                            "following", "followers" -> result.users.isNotEmpty()
                            else -> result.hasMore
                        }
                        result.copy(hasMore = more, threads = result.threads.filterNot(store::blocked).distinctBy { it.id to it.matchedPost }, users = result.users.distinctBy { it.id }, posts = result.posts.filterNot { post -> store.blocked(Thread(post.id, post.blocks.joinToString("") { it.text }, result.thread?.thread?.forum ?: r.title, post.author)) }.map { post -> post.copy(children = post.children.filterNot { child -> store.blocked(Thread(child.id, child.blocks.joinToString("") { it.text }, result.thread?.thread?.forum ?: r.title, child.author)) }) }.distinctBy { it.id })
                    }
                }
                if (current == generation) {
                    val incomingPostPages = result.posts.associate { it.id to (result.thread?.page ?: requestedPage) }
                    postPages = if (append) incomingPostPages + postPages else incomingPostPages
                    data = if (append) result.appendTo(data) else result
                    page = result.thread?.page ?: requestedPage
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (current == generation) { error = e.message?.take(240) ?: "加载失败，请重试"; if (e is SessionExpired) logout() } }
            finally { if (current == generation) { loading = false; refreshing = false } }
        }
    }
    fun action(onSuccess: () -> Unit = {}, block: suspend (Account) -> String) {
        val a = account ?: run { report("请先登录百度账号"); return }
        val session = accountGeneration
        lateinit var job: Job
        job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = withContext(Dispatchers.IO) { block(a) }
                if (session == accountGeneration && a.sessionMatches(account)) { onSuccess(); notice = result }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (session == accountGeneration && a.sessionMatches(account)) { error = e.message?.take(240) ?: "操作失败"; if (e is SessionExpired) logout() } }
            finally { actions.remove(job) }
        }
        actions.add(job); job.start()
    }
    fun completeLogin(bduss: String, stoken: String, onSuccess: () -> Unit) {
        if (loginJob?.isActive == true) return
        loading = true
        loginJob = viewModelScope.launch {
            try {
                val a = withContext(Dispatchers.IO) { api.login(bduss, stoken) }
                actions.toList().forEach { it.cancelAndJoin() }; actions.clear(); saveJob?.cancelAndJoin(); loadJob?.cancelAndJoin(); generation++
                withContext(Dispatchers.IO) { store.saveAccount(a) }
                accountGeneration++
                homeSnapshot = null
                account = a; notice = "登录成功"; onSuccess(); load()
                signing = false; submitting = false
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { report(e.message ?: "登录验证失败") }
            finally { loading = false }
        }
    }
    fun logout() {
        viewModelScope.launch {
            actions.toList().forEach { it.cancelAndJoin() }; actions.clear()
            loadJob?.cancelAndJoin(); saveJob?.cancelAndJoin(); generation++
            try {
                withContext(Dispatchers.IO) { store.saveAccount(null) }
                accountGeneration++
                account = null
                submitting = false
                signing = false
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                WebStorage.getInstance().deleteAllData()
                navigate(Route("me"), root = true)
                homeSnapshot = null
                notice = "已退出登录"
            } catch (e: Exception) { report("退出清理失败，请重试") }
        }
    }
    fun saveThread(mode: Int = 0) {
        val p = data.thread ?: return
        if (saving) return
        val active = account
        saving = true
        saveJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { offline.capture(p.thread.id, active, mode) { progress -> withContext(Dispatchers.Main) { saveProgress = progress } } }
                notice = when (mode) { 1 -> "正文、楼中楼和图片已保存"; 2 -> "正文、楼中楼及媒体已保存"; else -> "整帖正文和楼中楼已保存，媒体仍需联网" }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { report(e.message ?: "保存失败，已有保存保持不变") }
            finally { saving = false; saveProgress = "" }
        }
    }
    fun cancelSave() { saveJob?.cancel() }
    fun removeEntries(kind: String, ids: Set<String>, onSuccess: () -> Unit) {
        require(kind in setOf("history", "saved", "favorites") && ids.size <= 1000)
        val requestedRoute = route
        if (kind == "favorites") action(onSuccess = { onSuccess(); if (route == requestedRoute) load() }) { a ->
            ids.forEach { api.favorite(a, it, "0", false) }
            "已取消 ${ids.size} 个账号收藏"
        } else viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { ids.forEach { if (kind == "saved") offline.delete(it) else store.remove(kind, it) } }
                onSuccess(); if (route == requestedRoute) load()
                notice = "已删除 ${ids.size} 条本机记录"
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { report(e.message ?: "部分记录未能删除，请刷新确认") }
        }
    }
    fun modifyProfile(name: String, intro: String, sex: Int) {
        var updated: Account? = null
        var profile: Profile? = null
        action(onSuccess = {
            val fresh = checkNotNull(updated)
            store.saveAccount(fresh)
            account = fresh
            data = data.copy(profile = profile)
        }) { a ->
            api.updateProfile(a, name, intro, sex)
            val verified = api.profile(a.uid, a)
            check(verified.user.name == name.trim()) { "资料已提交，但昵称尚未核实，请刷新确认" }
            updated = a.copy(name = verified.user.name, portrait = verified.user.portrait)
            profile = verified
            "资料修改已确认"
        }
    }
    fun offlineThread(id: String) {
      try {
        val j = store.get("saved", id) ?: return
        val t = threadFromJson(j)
        fun visible(post: Post) = !store.blocked(Thread(post.id, post.blocks.joinToString("") { it.text }, t.forum, post.author))
        val posts = j.getJSONArray("posts").objects().map(::postFromJson).filter(::visible).map { it.copy(children = it.children.filter(::visible)) }
        stack.add(route); route = Route("offline", id, t.title); data = ScreenData(thread = ThreadPage(t, j.optString("forum-id"), posts, j.optInt("page", 1), j.optInt("pages", 1)), posts = posts)
      } catch (e: Exception) { report(e.message ?: "离线正文损坏，请重新保存") }
    }
    fun setSettings(theme: String = darkMode, size: Float = fontSize, media: Boolean = manualMedia) {
        darkMode = theme; fontSize = size.coerceIn(12f, 26f); manualMedia = media
        store.prefs.edit().putString("theme", theme).putFloat("font-size", fontSize).putBoolean("manual-media", media).apply()
    }
    fun signAll(automatic: Boolean = false) {
        val a = account ?: return
        if (signing) return
        if (automatic && !store.prefs.getBoolean("auto-sign", false)) return
        val date = java.time.LocalDate.now().toString()
        if (automatic && store.prefs.getString("sign-cycle-${a.uid}", "") == date) return
        signing = true
        action {
          try {
            var success = 0; var failed = 0
            for ((id, name) in api.followedForums(it)) {
                currentCoroutineContext().ensureActive()
                val key = "signed-${a.uid}-$date-$id"
                if (store.prefs.getBoolean(key, false)) continue
                try { api.signForum(it, id, name); store.prefs.edit().putBoolean(key, true).apply(); success++ }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { failed++ }
                delay(300)
            }
            if (failed == 0) store.prefs.edit().putString("sign-cycle-${a.uid}", date).apply()
            "签到完成：$success 个成功，$failed 个失败"
          } finally { withContext(NonCancellable + Dispatchers.Main) { signing = false } }
        }
    }

    fun compose(forum: String, tid: String? = null, pid: String? = null, floor: Int = 1, subpost: String? = null, replyTo: String = "") {
        if (tid == null && !newThreadsEnabled || tid != null && !repliesEnabled) { report("请先在设置中开启" + if (tid == null) "允许发帖" else "允许回帖"); return }
        if (account == null) { report("请先登录百度账号"); return }
        val target = JSONObject().put("floor", floor).put("reply-to", replyTo)
        if (tid != null) target.put("tid", tid)
        if (pid != null) target.put("pid", pid)
        if (subpost != null) target.put("subpost", subpost)
        navigate(Route("compose", forum, if (tid == null) "发布文字主题" else "回复", target.toString()))
    }
    fun submitDraft(title: String, text: String, imageUris: List<String>, draftKey: String) {
        if (submitting) return
        val a = account ?: run { report("请先登录"); return }
        val r = route
        val target = JSONObject(r.extra)
        val tid = target.optString("tid").takeIf(String::isNotBlank)
        if (tid == null && !newThreadsEnabled || tid != null && !repliesEnabled) { report("当前内容发送已在设置中关闭，草稿保持不变"); return }
        if (text.graphemes() > 10000 || (tid == null && (title.isBlank() || title.graphemes() > 64))) { report("主题标题限 64 字，正文限 10000 字"); return }
        if (text.isBlank() && imageUris.isEmpty()) { report("请输入正文或选择回复图片"); return }
        submitting = true
        action { active ->
            try {
                val images = imageUris.mapIndexed { index, raw ->
                    val uri = android.net.Uri.parse(raw)
                    require(uri.scheme == "content") { "图片来源不受支持，请重新选择" }
                    val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                        val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                        while (true) { val n = input.read(buffer); if (n < 0) break; require(out.size() + n <= 10 * 1024 * 1024) { "单张图片不能超过 10 MB" }; out.write(buffer, 0, n) }
                        out.toByteArray()
                    } ?: error("无法读取回复图片，请重新选择")
                    val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    require(options.outWidth in 1..20000 && options.outHeight in 1..20000 && options.outWidth.toLong() * options.outHeight <= 80000000) { "图片尺寸无效或过大" }
                    api.uploadReplyImage(active, bytes, tid ?: error("新主题不支持附图"), index)
                }
                val prefixName = target.optString("reply-to").trim()
                val body = if (target.has("subpost") && prefixName.isNotBlank() && prefixName.toByteArray().size <= 512 && prefixName.none { it == ',' || it == ')' || it.code < 32 }) "回复 $prefixName : $text" else text
                val resultTid = api.submit(active, r.id, title, body, tid, target.optString("pid").takeIf(String::isNotBlank), target.optInt("floor", 1), target.optString("subpost").takeIf(String::isNotBlank), images)
                withContext(Dispatchers.Main) {
                    if (account == active) {
                        store.remove("draft", draftKey)
                        navigate(Route("thread", resultTid))
                    }
                }
                "提交成功"
            } finally { withContext(NonCancellable + Dispatchers.Main) { submitting = false } }
        }
    }
}

fun String.graphemes(): Int {
    val iterator = android.icu.text.BreakIterator.getCharacterInstance().apply { setText(this@graphemes) }
    var count = 0
    iterator.first()
    while (iterator.next() != android.icu.text.BreakIterator.DONE) count++
    return count
}
