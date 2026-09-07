package com.quickdelete.app

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

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

    // Batch delete confirmation (Android 11+): one dialog for the whole pending set
    val deleteRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onBatchDeleteConfirmed()
            // Recompute feed vs seen set after MediaStore delete.
            viewModel.loadPhotos(context)
        } else {
            viewModel.onBatchDeleteCancelled()
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

            viewModel.photos.isEmpty() && viewModel.pendingCount == 0 -> {
                EmptyScreen(
                    allPhotosSeen = viewModel.allPhotosSeen,
                    onRebrowse = { viewModel.clearSeenAndReload(context) }
                )
            }

            else -> {
                PhotoSwipeScreen(
                    viewModel = viewModel,
                    deleteRequestLauncher = deleteRequestLauncher
                )
            }
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
                        containerColor = Color(0xFF1E88E5),
                        contentColor = Color.White
                    )
                ) {
                    Text("重新浏览")
                }
            }
        }
    }
}

@Composable
fun PhotoSwipeScreen(
    viewModel: PhotoViewModel,
    deleteRequestLauncher: androidx.activity.result.ActivityResultLauncher<androidx.activity.result.IntentSenderRequest>
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val animX = remember { Animatable(0f) }
    val animY = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    val rotation = remember { Animatable(0f) }
    var gestureLocked by remember { mutableStateOf(false) }

    val currentPhoto = viewModel.getCurrentPhoto()
    val nextPhoto = viewModel.getNextPhoto()

    // Preload next image
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
                        rotationZ = rotation.value
                    )
                    .pointerInput(gestureLocked, photo.uri) {
                        if (gestureLocked) return@pointerInput
                        detectDragGestures(
                            onDragEnd = {
                                coroutineScope.launch {
                                    if (gestureLocked) return@launch
                                    val offsetX = animX.value
                                    val offsetY = animY.value
                                    val absX = offsetX.absoluteValue
                                    val absY = offsetY.absoluteValue
                                    val deleteThreshold = 200f
                                    val navThreshold = 200f

                                    when {
                                        // ONLY right-swipe deletes
                                        offsetX > deleteThreshold && absX > absY -> {
                                            gestureLocked = true
                                            val flingX = with(density) { 900.dp.toPx() }
                                            val flingY = with(density) { (-700).dp.toPx() }
                                            // Throw upper-right with "扔出去" feel
                                            launch {
                                                animX.animateTo(flingX, tween(280))
                                            }
                                            launch {
                                                animY.animateTo(flingY, tween(280))
                                            }
                                            launch {
                                                rotation.animateTo(38f, tween(280))
                                            }
                                            launch {
                                                scale.animateTo(0.55f, tween(280))
                                            }
                                            // Wait for fling roughly
                                            kotlinx.coroutines.delay(260)
                                            viewModel.stageCurrentForDelete()
                                            animX.snapTo(0f)
                                            animY.snapTo(0f)
                                            scale.snapTo(1f)
                                            rotation.snapTo(0f)
                                            gestureLocked = false
                                        }

                                        // Swipe UP = next photo
                                        offsetY < -navThreshold && absY > absX -> {
                                            gestureLocked = true
                                            val exitY = with(density) { (-1200).dp.toPx() }
                                            animY.animateTo(exitY, tween(200))
                                            viewModel.moveToNext()
                                            animX.snapTo(0f)
                                            animY.snapTo(0f)
                                            scale.snapTo(1f)
                                            rotation.snapTo(0f)
                                            gestureLocked = false
                                        }

                                        // Swipe DOWN = previous photo
                                        offsetY > navThreshold && absY > absX -> {
                                            gestureLocked = true
                                            val exitY = with(density) { 1200.dp.toPx() }
                                            animY.animateTo(exitY, tween(200))
                                            viewModel.moveToPrevious()
                                            animX.snapTo(0f)
                                            animY.snapTo(0f)
                                            scale.snapTo(1f)
                                            rotation.snapTo(0f)
                                            gestureLocked = false
                                        }

                                        // Snap back (includes left-swipe — no delete)
                                        else -> {
                                            launch { animX.animateTo(0f, tween(200)) }
                                            launch { animY.animateTo(0f, tween(200)) }
                                            launch { scale.animateTo(1f, tween(200)) }
                                            launch { rotation.animateTo(0f, tween(200)) }
                                        }
                                    }
                                }
                            },
                            onDrag = { change, dragAmount ->
                                if (gestureLocked) return@detectDragGestures
                                change.consume()
                                coroutineScope.launch {
                                    animX.snapTo(animX.value + dragAmount.x)
                                    animY.snapTo(animY.value + dragAmount.y)

                                    // Live tilt only when dragging right (delete hint)
                                    val ox = animX.value
                                    val targetRotation = if (ox > 0) {
                                        (ox / 18f).coerceIn(0f, 28f)
                                    } else {
                                        (ox / 40f).coerceIn(-8f, 0f)
                                    }
                                    rotation.snapTo(targetRotation)

                                    val targetScale =
                                        1f - (ox.coerceAtLeast(0f) / 2200f).coerceIn(0f, 0.18f)
                                    scale.snapTo(targetScale)
                                }
                            }
                        )
                    },
                contentScale = ContentScale.Fit
            )
        } ?: run {
            // Feed empty but may still have pending deletes
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = when {
                            viewModel.pendingCount > 0 ->
                                "已选 ${viewModel.pendingCount} 张，点击下方确认删除"
                            viewModel.allPhotosSeen -> "已全部浏览完"
                            else -> "暂无照片"
                        },
                        color = Color.White,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    if (viewModel.pendingCount == 0 && viewModel.allPhotosSeen) {
                        Button(
                            onClick = { viewModel.clearSeenAndReload(context) },
                            modifier = Modifier.padding(top = 20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1E88E5),
                                contentColor = Color.White
                            )
                        ) {
                            Text("重新浏览")
                        }
                    }
                }
            }
        }

        // HUD: pending + confirmed today
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 60.dp)
                .background(
                    color = Color(0x99000000),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            val pending = viewModel.pendingCount
            val text = if (pending > 0) {
                "待删 $pending · 今日已删 ${viewModel.deletedToday}"
            } else {
                "今日已删 ${viewModel.deletedToday}"
            }
            Text(
                text = text,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Floating batch-delete action
        if (viewModel.pendingCount > 0) {
            Button(
                onClick = {
                    viewModel.requestBatchDelete(context, deleteRequestLauncher)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp)
                    .fillMaxWidth(0.72f),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE53935),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "删除 ${viewModel.pendingCount} 张",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        // Bottom hints
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
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
                text = "右滑标记删除（批量确认）",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
        }
    }
}
