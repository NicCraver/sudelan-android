package com.quickdelete.app

import android.Manifest
import android.app.Activity
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.SwipeVertical
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val BarBg = Color(0xFF121212)
private val Accent = Color(0xFFE53935)
private val AccentBlue = Color(0xFF1E88E5)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PhotoScreen(
    viewModel: PhotoViewModel = viewModel()
) {
    val context = LocalContext.current

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionState = rememberPermissionState(permission)

    val deleteRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onEmptyRecycleConfirmed()
            viewModel.loadPhotos(context)
        } else {
            viewModel.onEmptyRecycleCancelled()
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionState.status.isGranted) {
            permissionState.launchPermissionRequest()
        }
    }

    LaunchedEffect(permissionState.status.isGranted) {
        if (permissionState.status.isGranted) {
            viewModel.loadPhotos(context)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            !permissionState.status.isGranted -> {
                PermissionScreen(
                    showRationale = permissionState.status.shouldShowRationale,
                    onRequestPermission = { permissionState.launchPermissionRequest() }
                )
            }

            viewModel.isLoading -> {
                LoadingScreen()
            }

            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        when (viewModel.selectedTab) {
                            AppTab.Swipe -> SwipeTabContent(viewModel)
                            AppTab.Album -> AlbumTab(
                                viewModel = viewModel
                            )
                            AppTab.Recycle -> RecycleTab(
                                viewModel = viewModel,
                                onEmpty = {
                                    viewModel.requestEmptyRecycleBin(context, deleteRequestLauncher)
                                }
                            )
                            AppTab.Me -> MeTab(viewModel = viewModel)
                        }
                    }
                    SudelanBottomBar(
                        selected = viewModel.selectedTab,
                        recycleCount = viewModel.recycleCount,
                        onSelect = { viewModel.selectTab(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SudelanBottomBar(
    selected: AppTab,
    recycleCount: Int,
    onSelect: (AppTab) -> Unit
) {
    val items = listOf(
        Triple(AppTab.Swipe, "刷删", Icons.Filled.SwipeVertical),
        Triple(AppTab.Album, "相册", Icons.Filled.GridView),
        Triple(AppTab.Recycle, "回收站", Icons.Filled.Delete),
        Triple(AppTab.Me, "我的", Icons.Filled.Person)
    )
    NavigationBar(
        containerColor = BarBg,
        contentColor = Color.White,
        modifier = Modifier.navigationBarsPadding()
    ) {
        items.forEach { (tab, label, icon) ->
            val isSelected = selected == tab
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(tab) },
                icon = {
                    Box {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            modifier = Modifier.size(22.dp)
                        )
                        if (tab == AppTab.Recycle && recycleCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 6.dp, y = (-4).dp)
                                    .size(8.dp)
                                    .background(Accent, CircleShape)
                            )
                        }
                    }
                },
                label = {
                    Text(
                        text = if (tab == AppTab.Recycle && recycleCount > 0) {
                            "$label($recycleCount)"
                        } else label,
                        fontSize = 11.sp
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = Color.White,
                    unselectedIconColor = Color.White.copy(alpha = 0.55f),
                    unselectedTextColor = Color.White.copy(alpha = 0.55f),
                    indicatorColor = Color(0xFF2A2A2A)
                )
            )
        }
    }
}

@Composable
private fun SwipeTabContent(viewModel: PhotoViewModel) {
    val context = LocalContext.current
    when {
        viewModel.photos.isEmpty() -> {
            EmptyScreen(
                allPhotosSeen = viewModel.allPhotosSeen,
                onRebrowse = { viewModel.clearSeenAndReload(context) }
            )
        }
        else -> {
            PhotoSwipeScreen(viewModel = viewModel)
        }
    }
}

@Composable
fun PermissionScreen(
    showRationale: Boolean,
    onRequestPermission: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = if (showRationale) "需要访问照片权限才能帮您整理照片" else "没有照片权限无法使用",
                color = Color.White,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )

            if (showRationale) {
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("授予权限")
                }
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "加载中...",
            color = Color.White,
            fontSize = 18.sp
        )
    }
}

@Composable
fun EmptyScreen(
    allPhotosSeen: Boolean = false,
    onRebrowse: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = if (allPhotosSeen) "已全部浏览完" else "暂无照片",
                color = Color.White,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
            if (allPhotosSeen) {
                Text(
                    text = "相册里还有已看过的照片",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Button(
                    onClick = { onRebrowse?.invoke() },
                    modifier = Modifier.padding(top = 20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentBlue,
                        contentColor = Color.White
                    )
                ) {
                    Text("重新浏览")
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Album tab
// ---------------------------------------------------------------------------

@Composable
fun AlbumTab(viewModel: PhotoViewModel) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "相册 (${viewModel.albumPhotos.size})",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row {
                if (viewModel.albumSelectMode && viewModel.albumSelectedIds.isNotEmpty()) {
                    TextButton(onClick = { viewModel.softDeleteAlbumSelection() }) {
                        Text("移入回收站 (${viewModel.albumSelectedIds.size})", color = Accent)
                    }
                }
                TextButton(onClick = { viewModel.toggleAlbumSelectMode() }) {
                    Text(
                        text = if (viewModel.albumSelectMode) "取消" else "选择",
                        color = Color.White
                    )
                }
            }
        }

        if (viewModel.albumPhotos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无照片", color = Color.White.copy(alpha = 0.7f))
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(viewModel.albumPhotos, key = { it.id }) { photo ->
                    val selected = photo.id in viewModel.albumSelectedIds
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clickable {
                                if (viewModel.albumSelectMode) {
                                    viewModel.toggleAlbumSelection(photo.id)
                                } else {
                                    viewModel.jumpToPhotoFromAlbum(photo.id)
                                }
                            }
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(photo.uri)
                                .crossfade(true)
                                .size(400)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (viewModel.albumSelectMode) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) AccentBlue else Color.Black.copy(alpha = 0.45f)
                                    )
                                    .border(1.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Recycle tab
// ---------------------------------------------------------------------------

@Composable
fun RecycleTab(
    viewModel: PhotoViewModel,
    onEmpty: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "回收站 (${viewModel.recycleCount})",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (viewModel.recycleCount > 0) {
                    Text(
                        text = "约 ${viewModel.formatBytes(viewModel.recycleBinBytes())}",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
            }
            if (viewModel.recycleCount > 0) {
                Button(
                    onClick = onEmpty,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("清空", fontSize = 14.sp)
                }
            }
        }

        if (viewModel.recyclePhotos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.RestoreFromTrash,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("回收站为空", color = Color.White.copy(alpha = 0.7f))
                    Text(
                        "右滑或相册多选会移到这里",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(viewModel.recyclePhotos, key = { it.id }) { photo ->
                    Box(
                        modifier = Modifier.aspectRatio(1f)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(photo.uri)
                                .crossfade(true)
                                .size(400)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        TextButton(
                            onClick = { viewModel.restoreFromRecycle(photo) },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(4.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "恢复",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Me tab
// ---------------------------------------------------------------------------

@Composable
fun MeTab(viewModel: PhotoViewModel) {
    val context = LocalContext.current
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.2.0"
        } catch (_: Throwable) {
            "0.2.0"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = "我的",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 20.dp)
        )

        StatCard(
            title = "今日移入回收站",
            value = "${viewModel.softDeletedToday}"
        )
        Spacer(Modifier.height(10.dp))
        StatCard(
            title = "今日永久删除",
            value = "${viewModel.deletedToday}"
        )
        Spacer(Modifier.height(10.dp))
        StatCard(
            title = "累计永久删除",
            value = "${viewModel.deletedTotal}"
        )
        Spacer(Modifier.height(10.dp))
        StatCard(
            title = "约释放空间",
            value = viewModel.formatBytes(viewModel.bytesFreed)
        )
        Spacer(Modifier.height(10.dp))
        StatCard(
            title = "回收站待处理",
            value = "${viewModel.recycleCount}"
        )

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = { viewModel.clearSeenAndReload(context) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentBlue,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("重新浏览", fontSize = 16.sp, modifier = Modifier.padding(vertical = 4.dp))
        }

        Spacer(Modifier.weight(1f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("速删", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                text = "版本 $versionName",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = "右滑进回收站 · 清空才永久删除",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun StatCard(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp)
        Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------------------
// Swipe gestures — FROZEN from v0.1.4 (axis lock + velocity throw)
// Soft-delete only: stageCurrentForDelete → recycle bin (no MediaStore).
// ---------------------------------------------------------------------------

private enum class AxisLock {
    None,
    Vertical,
    HorizontalRight,
    HorizontalLeft
}

@Composable
fun PhotoSwipeScreen(
    viewModel: PhotoViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val animX = remember { Animatable(0f) }
    val animY = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    val rotation = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    var gestureLocked by remember { mutableStateOf(false) }
    var axisLock by remember { mutableStateOf(AxisLock.None) }
    var gestureDx by remember { mutableStateOf(0f) }
    var gestureDy by remember { mutableStateOf(0f) }
    val velocityTracker = remember { VelocityTracker() }
    val lockSlopPx = with(density) { 14.dp.toPx() }
    val crossAxisDamp = 0.08f

    val reduceMotion = remember(context) {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        } catch (_: Throwable) {
            false
        }
    }

    val snapSpring = remember {
        spring<Float>(
            dampingRatio = 1f,
            stiffness = Spring.StiffnessMedium
        )
    }
    val throwSpring = remember {
        spring<Float>(
            dampingRatio = 0.86f,
            stiffness = 280f
        )
    }

    val currentPhoto = viewModel.getCurrentPhoto()
    val nextPhoto = viewModel.getNextPhoto()

    if (nextPhoto != null) {
        DisposableEffect(nextPhoto.uri) {
            val imageLoader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(nextPhoto.uri)
                .build()
            imageLoader.enqueue(request)
            onDispose { }
        }
    }

    suspend fun resetTransforms() {
        animX.snapTo(0f)
        animY.snapTo(0f)
        scale.snapTo(1f)
        rotation.snapTo(0f)
        alpha.snapTo(1f)
    }

    suspend fun snapBack(velocityX: Float, velocityY: Float) {
        coroutineScope {
            launch {
                animX.animateTo(0f, animationSpec = snapSpring, initialVelocity = velocityX)
            }
            launch {
                animY.animateTo(0f, animationSpec = snapSpring, initialVelocity = velocityY)
            }
            launch {
                scale.animateTo(1f, animationSpec = snapSpring)
            }
            launch {
                rotation.animateTo(0f, animationSpec = snapSpring)
            }
            launch {
                alpha.animateTo(1f, animationSpec = snapSpring)
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        currentPhoto?.let { photo ->
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(photo.uri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .offset {
                        IntOffset(animX.value.roundToInt(), animY.value.roundToInt())
                    }
                    .graphicsLayer(
                        scaleX = scale.value,
                        scaleY = scale.value,
                        rotationZ = rotation.value,
                        alpha = alpha.value
                    )
                    .pointerInput(gestureLocked, photo.uri, reduceMotion) {
                        if (gestureLocked) return@pointerInput
                        detectDragGestures(
                            onDragStart = {
                                velocityTracker.resetTracking()
                                axisLock = AxisLock.None
                                gestureDx = 0f
                                gestureDy = 0f
                                scope.launch {
                                    animX.stop()
                                    animY.stop()
                                    scale.stop()
                                    rotation.stop()
                                    alpha.stop()
                                }
                            },
                            onDragEnd = {
                                scope.launch {
                                    if (gestureLocked) return@launch
                                    val velocity = velocityTracker.calculateVelocity()
                                    val velocityX = velocity.x
                                    val velocityY = velocity.y
                                    val offsetX = animX.value
                                    val offsetY = animY.value
                                    val absX = offsetX.absoluteValue
                                    val absY = offsetY.absoluteValue
                                    val deleteThreshold = 200f
                                    val navThreshold = 200f
                                    val flingVelocityX = 1200f
                                    val lock = axisLock

                                    when {
                                        lock == AxisLock.HorizontalRight &&
                                            (offsetX > deleteThreshold || velocityX > flingVelocityX) &&
                                            offsetX > 0f &&
                                            (absX > absY * 1.5f || absY < 12f) -> {
                                            gestureLocked = true
                                            if (reduceMotion) {
                                                alpha.snapTo(0f)
                                                viewModel.stageCurrentForDelete()
                                                resetTransforms()
                                                gestureLocked = false
                                            } else {
                                                val flingX = with(density) { 1100.dp.toPx() }
                                                val flingY = with(density) { (-780).dp.toPx() }
                                                val targetX = max(offsetX + velocityX * 0.22f, flingX)
                                                val targetY = min(offsetY + velocityY * 0.22f, flingY)
                                                val throwRot = (
                                                    rotation.value +
                                                        (velocityX / 3200f).coerceIn(0f, 10f)
                                                    ).coerceIn(0f, 32f)
                                                val throwScale = 0.90f

                                                coroutineScope {
                                                    launch {
                                                        animX.animateTo(
                                                            targetX,
                                                            animationSpec = throwSpring,
                                                            initialVelocity = velocityX
                                                        )
                                                    }
                                                    launch {
                                                        animY.animateTo(
                                                            targetY,
                                                            animationSpec = throwSpring,
                                                            initialVelocity = velocityY
                                                        )
                                                    }
                                                    launch {
                                                        rotation.animateTo(
                                                            throwRot,
                                                            animationSpec = throwSpring,
                                                            initialVelocity = velocityX / 80f
                                                        )
                                                    }
                                                    launch {
                                                        scale.animateTo(
                                                            throwScale,
                                                            animationSpec = throwSpring
                                                        )
                                                    }
                                                    launch {
                                                        alpha.animateTo(
                                                            0f,
                                                            animationSpec = throwSpring
                                                        )
                                                    }
                                                }
                                                viewModel.stageCurrentForDelete()
                                                resetTransforms()
                                                gestureLocked = false
                                            }
                                        }

                                        lock == AxisLock.Vertical &&
                                            offsetY < -navThreshold -> {
                                            gestureLocked = true
                                            if (reduceMotion) {
                                                viewModel.moveToNext()
                                                resetTransforms()
                                            } else {
                                                val exitY = with(density) { (-900).dp.toPx() }
                                                val projected = min(
                                                    offsetY + velocityY * 0.12f,
                                                    exitY
                                                )
                                                animY.animateTo(
                                                    projected,
                                                    animationSpec = tween(
                                                        durationMillis = 160,
                                                        easing = FastOutSlowInEasing
                                                    ),
                                                    initialVelocity = velocityY
                                                )
                                                viewModel.moveToNext()
                                                resetTransforms()
                                            }
                                            gestureLocked = false
                                        }

                                        lock == AxisLock.Vertical &&
                                            offsetY > navThreshold -> {
                                            gestureLocked = true
                                            if (reduceMotion) {
                                                viewModel.moveToPrevious()
                                                resetTransforms()
                                            } else {
                                                val exitY = with(density) { 900.dp.toPx() }
                                                val projected = max(
                                                    offsetY + velocityY * 0.12f,
                                                    exitY
                                                )
                                                animY.animateTo(
                                                    projected,
                                                    animationSpec = tween(
                                                        durationMillis = 160,
                                                        easing = FastOutSlowInEasing
                                                    ),
                                                    initialVelocity = velocityY
                                                )
                                                viewModel.moveToPrevious()
                                                resetTransforms()
                                            }
                                            gestureLocked = false
                                        }

                                        else -> {
                                            snapBack(velocityX, velocityY)
                                        }
                                    }
                                    axisLock = AxisLock.None
                                    gestureDx = 0f
                                    gestureDy = 0f
                                }
                            },
                            onDrag = { change, dragAmount ->
                                if (gestureLocked) return@detectDragGestures
                                change.consume()
                                velocityTracker.addPosition(
                                    change.uptimeMillis,
                                    change.position
                                )
                                gestureDx += dragAmount.x
                                gestureDy += dragAmount.y

                                if (axisLock == AxisLock.None) {
                                    val absDx = gestureDx.absoluteValue
                                    val absDy = gestureDy.absoluteValue
                                    val travel = hypot(gestureDx, gestureDy)
                                    if (travel >= lockSlopPx || max(absDx, absDy) >= lockSlopPx) {
                                        axisLock = when {
                                            absDy >= absDx * 1.15f -> AxisLock.Vertical
                                            gestureDx > 0f && absDx > absDy -> AxisLock.HorizontalRight
                                            gestureDx < 0f -> AxisLock.HorizontalLeft
                                            else -> AxisLock.Vertical
                                        }
                                    }
                                }

                                val applyX: Float
                                val applyY: Float
                                when (axisLock) {
                                    AxisLock.None -> {
                                        applyX = dragAmount.x
                                        applyY = dragAmount.y
                                    }
                                    AxisLock.Vertical -> {
                                        applyX = dragAmount.x * crossAxisDamp
                                        applyY = dragAmount.y
                                    }
                                    AxisLock.HorizontalRight -> {
                                        applyX = dragAmount.x
                                        applyY = dragAmount.y * crossAxisDamp
                                    }
                                    AxisLock.HorizontalLeft -> {
                                        applyX = dragAmount.x
                                        applyY = dragAmount.y * crossAxisDamp
                                    }
                                }

                                scope.launch {
                                    animX.snapTo(animX.value + applyX)
                                    animY.snapTo(animY.value + applyY)

                                    val ox = animX.value
                                    val showDeleteHint =
                                        axisLock == AxisLock.HorizontalRight ||
                                            (axisLock == AxisLock.None && ox > 0f)
                                    val targetRotation = when {
                                        showDeleteHint && ox > 0f ->
                                            (ox / 18f).coerceIn(0f, 28f)
                                        ox < 0f ->
                                            (ox / 40f).coerceIn(-8f, 0f)
                                        else -> 0f
                                    }
                                    rotation.snapTo(targetRotation)

                                    val hintX = if (showDeleteHint) ox.coerceAtLeast(0f) else 0f
                                    val targetScale =
                                        1f - (hintX / 4000f).coerceIn(0f, 0.08f)
                                    scale.snapTo(targetScale)
                                    val targetAlpha =
                                        1f - (hintX / 1800f).coerceIn(0f, 0.18f)
                                    alpha.snapTo(targetAlpha)
                                }
                            }
                        )
                    },
                contentScale = ContentScale.Fit
            )
        } ?: run {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = when {
                            viewModel.allPhotosSeen -> "已全部浏览完"
                            else -> "暂无照片"
                        },
                        color = Color.White,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    if (viewModel.allPhotosSeen) {
                        Button(
                            onClick = { viewModel.clearSeenAndReload(context) },
                            modifier = Modifier.padding(top = 20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentBlue,
                                contentColor = Color.White
                            )
                        ) {
                            Text("重新浏览")
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp)
                .background(
                    color = Color(0x99000000),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            val text = if (viewModel.recycleCount > 0) {
                "回收站 ${viewModel.recycleCount} · 今日永久 ${viewModel.deletedToday}"
            } else {
                "今日永久 ${viewModel.deletedToday}"
            }
            Text(
                text = text,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "上滑下一张 · 下滑上一张",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "右滑移入回收站",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
        }
    }
}
