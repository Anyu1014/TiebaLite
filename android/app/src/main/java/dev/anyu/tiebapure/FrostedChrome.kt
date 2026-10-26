package dev.anyu.tiebapure

import android.os.Build
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.layer.*
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*

object IOSLayout {
    val blue = Color(0xff007aff)
    val background = Color(0xfff2f2f7)
    val barWidth = 280.dp
    val barHeight = 62.dp
    val navigationHeight = 44.dp
    val cardRadius = 8.dp
    val cardGap = 12.dp
    val feedInset = 12.dp
    val cardInset = 16.dp
    val scrollClearance = 112.dp
    const val blur = 20f
    const val saturation = 1.2f
}

/** Anyu src/index.css .auth-shell--glass: blur(20px) saturate(120%), tint .42, highlight .16. */
@Composable fun AnyuFrostedSurface(
    backdrop: GraphicsLayer,
    origin: Offset,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(50),
    floating: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    var position by remember { mutableStateOf(Offset.Zero) }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val density = androidx.compose.ui.platform.LocalDensity.current
    val effect = remember(density) {
        if (Build.VERSION.SDK_INT >= 31) {
            val matrix = android.graphics.ColorMatrix().apply { setSaturation(IOSLayout.saturation) }
            android.graphics.RenderEffect.createChainEffect(
                android.graphics.RenderEffect.createColorFilterEffect(android.graphics.ColorMatrixColorFilter(matrix)),
                android.graphics.RenderEffect.createBlurEffect(IOSLayout.blur * density.density, IOSLayout.blur * density.density, android.graphics.Shader.TileMode.CLAMP)
            ).asComposeRenderEffect()
        } else null
    }
    // shortcut: API26–30 use a solid frost tint; GPU backdrop blur requires API31.
    val tint = if (effect == null) MaterialTheme.colorScheme.surface else if (dark) Color(0xff080f16).copy(alpha = 0.72f) else Color.White.copy(alpha = 0.42f)
    Box(modifier.then(if (floating) Modifier.shadow(12.dp, shape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f)) else Modifier).clip(shape).onGloballyPositioned { position = it.positionInRoot() }) {
        if (effect != null) Canvas(Modifier.matchParentSize().graphicsLayer { renderEffect = effect }) {
            translate(origin.x - position.x, origin.y - position.y) { drawLayer(backdrop) }
        }
        Box(Modifier.matchParentSize().background(tint))
        if (floating) Box(Modifier.matchParentSize().border(0.5.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.15f))), shape))
        content()
    }
}

@Composable fun FrostedAppShell(
    title: String,
    root: String,
    showTabs: Boolean,
    showBack: Boolean,
    showSearch: Boolean,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onTab: (String) -> Unit,
    actions: @Composable RowScope.() -> Unit,
    snackbar: @Composable () -> Unit,
    titleContent: (@Composable () -> Unit)? = null,
    feedback: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val layer = rememberGraphicsLayer()
    var origin by remember { mutableStateOf(Offset.Zero) }
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = maxOf(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(), WindowInsets.ime.asPaddingValues().calculateBottomPadding())
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }.drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        }) {
            content(PaddingValues(top = status + IOSLayout.navigationHeight, bottom = if (showTabs) maxOf(IOSLayout.scrollClearance, bottom + IOSLayout.barHeight + 16.dp) else bottom + 12.dp))
        }
        AnyuFrostedSurface(layer, origin, Modifier.fillMaxWidth(), RoundedCornerShape(0.dp), floating = false) {
            Box(Modifier.fillMaxWidth().height(status + IOSLayout.navigationHeight).padding(top = status)) {
                if (titleContent == null) Text(title, Modifier.align(Alignment.Center).padding(horizontal = if (showBack) 96.dp else 64.dp), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                else Box(Modifier.align(Alignment.CenterStart).padding(start = 56.dp, end = 156.dp)) { titleContent() }
                if (showBack) IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp).size(44.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f), CircleShape)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", modifier = Modifier.size(22.dp)) }
                Row(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).background(if (titleContent != null) MaterialTheme.colorScheme.surface.copy(alpha = 0.35f) else Color.Transparent, CircleShape), verticalAlignment = Alignment.CenterVertically) {
                    if (showSearch) IconButton(onClick = onSearch, modifier = Modifier.size(44.dp).background(if (titleContent == null) MaterialTheme.colorScheme.surface.copy(alpha = 0.35f) else Color.Transparent, CircleShape)) { Icon(Icons.Default.Search, "搜索", modifier = Modifier.size(22.dp)) }
                    actions()
                }
            }
        }
        Column(Modifier.align(Alignment.TopCenter).padding(top = status + IOSLayout.navigationHeight).fillMaxWidth()) { feedback() }
        if (showTabs) AnyuFrostedSurface(layer, origin, Modifier.align(Alignment.BottomCenter).padding(start = 20.dp, end = 20.dp, bottom = bottom + 8.dp).widthIn(max = IOSLayout.barWidth).fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(4.dp).heightIn(min = IOSLayout.barHeight - 8.dp)) {
                listOf(Triple("home", "首页", Icons.Default.Home), Triple("forums", "进吧", Icons.Default.List), Triple("me", "我的", Icons.Default.AccountCircle)).forEach { (kind, label, icon) ->
                    val selected = root == kind
                    Column(Modifier.weight(1f).heightIn(min = 54.dp).clip(RoundedCornerShape(50)).background(if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent).selectable(selected = selected, role = Role.Button, onClick = { onTab(kind) }).padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        val color = if (selected) IOSLayout.blue else MaterialTheme.colorScheme.onSurface
                        if (kind == "forums") Canvas(Modifier.size(23.dp)) {
                            val gap = 2.dp.toPx(); val edge = (size.width - gap) / 2
                            for (x in 0..1) for (y in 0..1) drawRoundRect(color, Offset(x * (edge + gap), y * (edge + gap)), androidx.compose.ui.geometry.Size(edge, edge), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                        } else Icon(icon, null, tint = color, modifier = Modifier.size(23.dp))
                        Spacer(Modifier.height(3.dp))
                        Text(label, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) IOSLayout.blue else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = if (showTabs) bottom + 84.dp else bottom + 12.dp)) { snackbar() }
    }
}

@Composable fun <T> IOSSegmentedControl(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)).padding(2.dp)) {
        options.forEach { (value, label) -> Box(Modifier.weight(1f).clip(RoundedCornerShape(7.dp)).background(if (selected == value) MaterialTheme.colorScheme.surface else Color.Transparent).selectable(selected == value, role = Role.Tab, onClick = { onSelect(value) }).heightIn(min = 32.dp).padding(horizontal = 8.dp, vertical = 5.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 13.sp, fontWeight = if (selected == value) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
        } }
    }
}

@Composable fun IOSSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String? = null) {
    val offset by animateDpAsState(if (checked) 22.dp else 2.dp, label = "switch")
    Box(Modifier.sizeIn(minWidth = 51.dp, minHeight = 44.dp).semantics { if (label != null) contentDescription = label }.toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange), contentAlignment = Alignment.Center) {
        Box(Modifier.size(51.dp, 31.dp).clip(CircleShape).background(if (checked) IOSLayout.blue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f))) {
            Box(Modifier.offset(x = offset, y = 2.dp).size(27.dp).shadow(2.dp, CircleShape).background(Color.White, CircleShape))
        }
    }
}
