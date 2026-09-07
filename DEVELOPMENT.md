# 开发指南

## 环境要求

- **Java**: OpenJDK 11+（推荐 21）
- **Android SDK**: API 26-34
- **Gradle**: 8.2+
- **IDE**: Android Studio Hedgehog 或更高版本（可选）

## 快速开始

### 1. 克隆项目
```bash
git clone <your-repo-url>
cd <project-directory>
```

### 2. 配置 Android SDK
确保 `ANDROID_HOME` 环境变量已设置：
```bash
export ANDROID_HOME=/path/to/android-sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$PATH
export PATH=$ANDROID_HOME/platform-tools:$PATH
```

### 3. 构建项目

#### Debug 版本（无签名）
```bash
./gradlew :app:assembleDebug
```
输出位置：`app/build/outputs/apk/debug/app-debug.apk`

#### Release 版本（需签名配置）
```bash
./gradlew :app:assembleRelease
```

#### 清理构建
```bash
./gradlew clean
```

### 4. 安装到设备
```bash
# 通过 ADB 安装
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 或使用 Gradle
./gradlew installDebug
```

## 项目结构

```
.
├── app/
│   ├── build.gradle.kts          # 应用级 Gradle 配置
│   ├── proguard-rules.pro        # ProGuard 混淆规则
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── java/com/quickdelete/app/
│           │   ├── MainActivity.kt        # 应用入口
│           │   ├── PhotoViewModel.kt      # 数据和业务逻辑
│           │   └── PhotoScreen.kt         # UI 和交互
│           └── res/
│               ├── values/
│               │   ├── strings.xml       # 中文字符串
│               │   ├── colors.xml        # 颜色定义
│               │   └── themes.xml        # 主题样式
│               └── mipmap-*/            # 应用图标
├── build.gradle.kts              # 项目级 Gradle 配置
├── settings.gradle.kts           # Gradle 设置
├── gradle.properties             # Gradle 属性
└── artifacts/                    # 编译产物（APK）
```

## 核心代码说明

### MainActivity.kt
应用入口，设置全屏沉浸式和 Compose 内容。

### PhotoViewModel.kt
- `loadPhotos()`: 从 MediaStore 加载照片列表
- `deleteCurrentPhoto()`: 处理照片删除（兼容不同 Android 版本）
- `moveToNext()`: 切换到下一张照片
- 管理删除计数和当前索引

### PhotoScreen.kt
- `PhotoScreen`: 主界面 Composable，处理权限请求
- `PhotoSwipeScreen`: 核心手势交互和图片显示
- 实现向上/左右滑动手势识别
- 预加载下一张图片提升性能

## 依赖库

```kotlin
// 核心
implementation("androidx.core:core-ktx:1.12.0")
implementation("androidx.activity:activity-compose:1.8.2")

// Compose
implementation(platform("androidx.compose:compose-bom:2024.02.00"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")
implementation("androidx.compose.foundation:foundation")

// Lifecycle
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

// 图片加载
implementation("io.coil-kt:coil-compose:2.5.0")

// 权限管理
implementation("com.google.accompanist:accompanist-permissions:0.34.0")
```

## 常用 Gradle 任务

```bash
# 查看所有任务
./gradlew tasks

# 构建所有变体
./gradlew assemble

# 运行 lint 检查
./gradlew lint

# 查看依赖树
./gradlew :app:dependencies

# 清理 + 构建
./gradlew clean assembleDebug

# 生成 APK 并安装
./gradlew installDebug
```

## 调试技巧

### 查看日志
```bash
# 过滤应用日志
adb logcat | grep QuickDelete

# 清除旧日志
adb logcat -c
```

### 查看应用信息
```bash
# 查看已安装的应用
adb shell pm list packages | grep quickdelete

# 查看应用详细信息
adb shell dumpsys package com.quickdelete.app
```

### 卸载应用
```bash
adb uninstall com.quickdelete.app
```

## 性能优化建议

1. **图片加载优化**
   - Coil 已配置自动内存缓存
   - 预加载下一张图片减少等待时间

2. **手势优化**
   - 使用 Compose Animation API 保证流畅动画
   - 避免在手势处理中进行重量级操作

3. **内存管理**
   - 使用 ContentResolver 按需查询，避免一次加载所有照片元数据
   - 图片采用 fit 模式，不加载超出屏幕的分辨率

## 发布清单

在发布 Release 版本前：

- [ ] 配置签名密钥（keystore）
- [ ] 启用 ProGuard/R8 代码混淆
- [ ] 更新版本号和版本名称
- [ ] 测试所有核心功能
- [ ] 验证权限请求流程
- [ ] 测试不同 Android 版本兼容性
- [ ] 准备应用图标和启动图
- [ ] 编写更新日志

## 添加新功能

### 示例：添加删除确认对话框

1. 在 `PhotoScreen.kt` 添加对话框状态：
```kotlin
var showDeleteDialog by remember { mutableStateOf(false) }
```

2. 实现对话框 UI：
```kotlin
if (showDeleteDialog) {
    AlertDialog(
        onDismissRequest = { showDeleteDialog = false },
        title = { Text("确认删除？") },
        confirmButton = { 
            TextButton(onClick = {
                viewModel.deleteCurrentPhoto(...)
                showDeleteDialog = false
            }) { Text("删除") }
        }
    )
}
```

3. 在手势处理中触发：
```kotlin
when {
    absX > 200 && absX > absY -> {
        showDeleteDialog = true
    }
}
```

## 贡献指南

1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add some amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 开启 Pull Request

## 常见问题

### Q: 构建失败，提示找不到 Android SDK
A: 确保 `ANDROID_HOME` 环境变量正确设置，并安装了所需的 SDK Platform 和 Build Tools。

### Q: Gradle 同步很慢
A: 
1. 使用镜像源加速（阿里云、腾讯云）
2. 启用 Gradle 守护进程：`org.gradle.daemon=true`
3. 增加堆内存：`org.gradle.jvmargs=-Xmx4096m`

### Q: 如何更改应用包名？
A: 
1. 修改 `app/build.gradle.kts` 中的 `namespace` 和 `applicationId`
2. 重命名 Kotlin 包目录和文件中的 package 声明
3. 更新 AndroidManifest.xml

## 技术支持

如有开发相关问题，欢迎提交 Issue 或查看 README.md。

---

Happy Coding! 🚀
