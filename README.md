# 速删 - 极简照片整理应用

一个模仿抖音/TikTok 手势交互的 Android 原生照片整理应用。通过简单的滑动手势，快速浏览和删除手机中的照片。

## 功能特点

- ✨ **全屏照片浏览** - 沉浸式全屏体验
- 👆 **向上滑动** - 跳过当前照片，查看下一张
- 👈👉 **左右滑动** - 删除当前照片
- 📊 **删除统计** - 实时显示今日已删除照片数量
- ⚡ **流畅体验** - 预加载下一张图片，确保滑动流畅
- 🔒 **安全删除** - Android 11+ 采用系统安全删除机制，需用户确认

## 技术栈

- **语言**: Kotlin
- **UI 框架**: Jetpack Compose
- **最低 SDK**: Android 8.0 (API 26)
- **目标 SDK**: Android 14 (API 34)
- **图片加载**: Coil
- **权限管理**: Accompanist Permissions

## 安装说明

### 方式一：直接安装 APK（推荐）

1. 从 `artifacts/速删-debug.apk` 或 `app/build/outputs/apk/debug/app-debug.apk` 下载 APK 文件
2. 将 APK 文件传输到您的 Android 手机
3. 在手机上启用"未知来源"安装权限：
   - 设置 → 安全 → 未知来源 → 允许此来源
4. 点击 APK 文件进行安装

### 方式二：通过 Android Studio 构建

```bash
# 1. 克隆项目
git clone <your-repo-url>
cd <project-directory>

# 2. 设置 ANDROID_HOME 环境变量
export ANDROID_HOME=<your-android-sdk-path>

# 3. 构建 Debug APK
./gradlew :app:assembleDebug

# 4. APK 位置
# app/build/outputs/apk/debug/app-debug.apk
```

## 使用方法

1. **首次启动** - 应用会请求照片访问权限，请点击"允许"
2. **浏览照片** - 应用自动加载手机相册中的所有照片（按最新时间排序）
3. **操作手势**：
   - **向上滑动**：保留当前照片，查看下一张
   - **左右滑动**：删除当前照片（Android 11+ 会弹出确认对话框）
4. **查看统计** - 顶部显示"今日已删 N"的统计信息

## 权限说明

应用需要以下权限：

- **READ_MEDIA_IMAGES** (Android 13+) - 读取设备中的图片
- **READ_EXTERNAL_STORAGE** (Android 12 及以下) - 读取外部存储中的图片

应用**不会**：
- 上传您的照片到任何服务器
- 收集您的个人信息
- 访问网络

## 项目结构

```
app/src/main/
├── java/com/quickdelete/app/
│   ├── MainActivity.kt         # 应用入口
│   ├── PhotoViewModel.kt       # 照片数据管理和业务逻辑
│   └── PhotoScreen.kt          # 主界面 UI 和手势处理
├── res/
│   ├── values/
│   │   ├── strings.xml        # 中文字符串资源
│   │   ├── colors.xml         # 颜色定义
│   │   └── themes.xml         # 应用主题
│   └── mipmap-*/              # 应用图标
└── AndroidManifest.xml        # 应用配置和权限声明
```

## 已知限制

### v1 版本范围内：
- 仅支持竖屏模式（手机优先）
- 不支持相册分类浏览
- 不支持智能去重
- 不包含账号系统或社交功能
- 不包含高级设置选项

### 删除机制：
- **Android 11+**: 使用 MediaStore.createDeleteRequest()，删除前会弹出系统确认对话框
- **Android 10 及以下**: 直接删除，无额外确认

## 性能优化

- **图片预加载** - 提前加载下一张图片，减少等待时间
- **按需加载** - 使用 Coil 进行高效的图片加载和缓存
- **流畅动画** - 基于 Compose Animation API 实现流畅的滑动效果

## 构建相关

### 构建 Debug APK
```bash
./gradlew :app:assembleDebug
```

### 构建 Release APK（需要签名配置）
```bash
./gradlew :app:assembleRelease
```

### 清理构建
```bash
./gradlew clean
```

## 开发环境

- **Gradle**: 8.2
- **Android Gradle Plugin**: 8.2.2
- **Kotlin**: 1.9.22
- **Compose BOM**: 2024.02.00
- **Java**: OpenJDK 21

## 兼容性

- **最低系统**: Android 8.0 (API 26)
- **目标系统**: Android 14 (API 34)
- **测试设备**: 理论上支持所有运行 Android 8.0+ 的设备

## 许可证

本项目采用 MIT 许可证。详见 LICENSE 文件。

## 贡献

欢迎提交 Issue 和 Pull Request！

## 联系方式

如有问题或建议，欢迎通过 Issues 反馈。

---

**注意**: 这是一个 Debug 版本，仅供测试使用。正式发布版本需要进行代码签名和优化。

## APK 命名

Debug 包输出为 `dist/速删-{versionName}-debug.apk`（例如 `速删-0.1.4-debug.apk`）。GitHub Release 资源请使用同名文件，不要再用固定的 `quick-delete-debug.apk`。
