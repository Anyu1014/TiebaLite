package dev.anyu.tiebapure

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.pulltorefresh.*

internal fun ScreenData.appendTo(previous: ScreenData): ScreenData {
    val merged = copy(
        threads = (previous.threads + threads).distinctBy { it.id to it.matchedPost },
        posts = (previous.posts + posts).distinctBy { it.id },
        users = (previous.users + users).distinctBy { it.id }
    )
    // shortcut: a fully blocked page stops pagination; track server cursors if hidden pages must be skipped.
    val progressed = merged.threads.size > previous.threads.size || merged.posts.size > previous.posts.size || merged.users.size > previous.users.size
    return merged.copy(hasMore = hasMore && progressed)
}

internal fun threadReadingPosition(index: Int, postIds: List<String>, pages: Map<String, Int>, fallbackPage: Int): Pair<Int, Int> {
    if (postIds.isEmpty()) return fallbackPage to 0
    if (index <= 0) return (pages[postIds.first()] ?: fallbackPage) to 0
    val postIndex = (index - 1).coerceAtMost(postIds.lastIndex)
    val page = pages[postIds[postIndex]] ?: fallbackPage
    return page to (postIds.take(postIndex).count { pages[it] == page } + 1)
}

@Composable fun AutomaticPageFooter(vm: AppViewModel) {
    LaunchedEffect(vm.route, vm.page, vm.loading, vm.error) { vm.loadNextPage() }
    Box(Modifier.fillMaxWidth().heightIn(min = 52.dp), contentAlignment = Alignment.Center) {
        if (vm.loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        else if (vm.error.isNotBlank()) TextButton(onClick = vm::retryLoad) { Text("加载失败，点击重试") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PullRefreshContent(vm: AppViewModel, modifier: Modifier, topInset: Dp, content: @Composable BoxScope.() -> Unit) {
    if (vm.route.kind == "offline") { Box(modifier, content = content); return }
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = vm.refreshing,
        onRefresh = { if (!vm.loading) vm.load(1) },
        modifier = modifier,
        state = state,
        indicator = { PullToRefreshDefaults.Indicator(state, vm.refreshing, Modifier.align(Alignment.TopCenter).padding(top = topInset)) },
        content = content
    )
}
