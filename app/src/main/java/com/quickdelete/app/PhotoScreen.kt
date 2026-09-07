package com.quickdelete.app

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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


private enum class AxisLock {
    None,
    Vertical,
    HorizontalRight,
    HorizontalLeft
}

@Composable
fun PhotoSwipeScreen(
    viewModel: PhotoViewModel,
    deleteRequestLauncher: androidx.activity.result.ActivityResultLauncher<androidx.activity.result.IntentSenderRequest>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val animX = remember { Animatable(0f) }
    val animY = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    val rotation = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    // Only lock during irreversible stage transitions (delete / vertical nav exit),
    // never during snap-back — so a new drag can interrupt and retarget.
    var gestureLocked by remember { mutableStateOf(false) }
    // Axis lock: prevents diagonal up-swipes from accidentally deleting.
    var axisLock by remember { mutableStateOf(AxisLock.None) }
    var gestureDx by remember { mutableStateOf(0f) }
    var gestureDy by remember { mutableStateOf(0f) }
    val velocityTracker = remember { VelocityTracker() }
    // ~14dp lock slop — decide axis once finger travels past this.
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
            dampingRatio = 1f, // critically damped — no bounce on cancel
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
                                // Interrupt snap-back / soft anims and take over from current values
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
                                    // ~1200 px/s feels like a deliberate fling on phone screens
                                    val flingVelocityX = 1200f
                                    val lock = axisLock

                                    when {
                                        // Delete ONLY when locked to HorizontalRight
                                        lock == AxisLock.HorizontalRight &&
                                            (offsetX > deleteThreshold || velocityX > flingVelocityX) &&
                                            offsetX > 0f &&
                                            (absX > absY * 1.5f || absY < 12f) -> {
                                            gestureLocked = true
                                            if (reduceMotion) {
                                                // Accessibility: no large travel — fade / stage instantly
                                                alpha.snapTo(0f)
                                                viewModel.stageCurrentForDelete()
                                                resetTransforms()
                                                gestureLocked = false
                                            } else {
                                                val flingX = with(density) { 1100.dp.toPx() }
                                                val flingY = with(density) { (-780).dp.toPx() }
                                                // Project from release velocity toward upper-right off-screen
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

                                        // Next/prev ONLY when locked to Vertical
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

                                        // Snap back (HorizontalLeft, None, or incomplete gestures)
                                        // Not locked: a new drag can interrupt mid-spring.
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

                                // Decide axis once travel exceeds lock slop
                                if (axisLock == AxisLock.None) {
                                    val absDx = gestureDx.absoluteValue
                                    val absDy = gestureDy.absoluteValue
                                    val travel = hypot(gestureDx, gestureDy)
                                    if (travel >= lockSlopPx || max(absDx, absDy) >= lockSlopPx) {
                                        axisLock = when {
                                            // Prefer vertical on near-ties so up-swipes win
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
                                        // Strongly damp X; only Y drives next/prev; never stage delete
                                        applyX = dragAmount.x * crossAxisDamp
                                        applyY = dragAmount.y
                                    }
                                    AxisLock.HorizontalRight -> {
                                        // Strongly damp Y; only +X can delete
                                        applyX = dragAmount.x
                                        applyY = dragAmount.y * crossAxisDamp
                                    }
                                    AxisLock.HorizontalLeft -> {
                                        // Damp Y; snap-back only on release (never delete)
                                        applyX = dragAmount.x
                                        applyY = dragAmount.y * crossAxisDamp
                                    }
                                }

                                scope.launch {
                                    animX.snapTo(animX.value + applyX)
                                    animY.snapTo(animY.value + applyY)

                                    // Live tilt only when on delete axis (right) or unlocked rightward
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

                                    // Gentle live scale (stay ≥ ~0.92); opacity hint on right drag
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
