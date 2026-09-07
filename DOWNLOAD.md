# 📦 APK 下载说明

## 可用的 APK 文件

「速删」Android 应用的可安装 APK 已准备好下载：

### 文件信息
- **文件名**: `quick-delete-debug.apk` / `速删-debug.apk`
- **大小**: 9.2 MB (9,560,016 bytes)
- **类型**: Android 安装包 (APK)
- **签名**: Debug keystore（用于测试）
- **MD5**: `1ffc76214ac93f12f32559cd4ced0a5c`
- **SHA256**: `3e269638811f13052c367aca860d3ff85bfab2ba03a7db84a6a2a6221ffede8e`

### 下载位置

APK 文件位于以下位置：

1. **Cursor Artifacts** (推荐):
   - `/opt/cursor/artifacts/quick-delete-debug.apk`
   - 实际路径: `/cursor/stores/bc-328671a7-089e-419c-8ad3-0c364639d7cd/artifacts/quick-delete-debug.apk`

2. **Git 仓库**:
   - `/workspace/artifacts/quick-delete-debug.apk` (ASCII 文件名)
   - `/workspace/artifacts/速删-debug.apk` (中文文件名)

### 如何下载

#### 方法一：从 Cursor Cloud Agent
如果您是通过 Cursor Cloud Agent 访问的，APK 文件应该可以通过 artifacts 系统下载。

#### 方法二：克隆 Git 仓库
```bash
git clone https://origin.cursor.com/git/nic-ai/tmp-a3946572a19e3259.git
cd tmp-a3946572a19e3259/artifacts
# 文件: quick-delete-debug.apk 或 速删-debug.apk
```

### 安装步骤

1. **下载 APK** 到您的 Android 手机
2. **启用"未知来源"** 安装权限
3. **点击 APK 文件** 安装
4. **授予照片权限** 首次启动时
5. **开始使用** 滑动整理照片！

详细安装说明请参考: [INSTALL.md](INSTALL.md)

### 验证文件完整性

下载后，您可以验证 APK 的完整性：

```bash
# MD5 校验
md5sum quick-delete-debug.apk
# 应该输出: 1ffc76214ac93f12f32559cd4ced0a5c

# SHA256 校验
sha256sum quick-delete-debug.apk
# 应该输出: 3e269638811f13052c367aca860d3ff85bfab2ba03a7db84a6a2a6221ffede8e
```

### 系统要求

- **最低版本**: Android 8.0 (API 26)
- **推荐版本**: Android 11+ (更好的删除体验)
- **架构**: 支持所有架构 (ARM, ARM64, x86, x86_64)
- **存储空间**: 至少 20 MB 可用空间

### 安全提示

✅ **安全确认**:
- 此 APK 由官方构建
- 使用 Gradle 标准构建流程
- 校验和已提供，可验证文件完整性
- 应用不联网，不收集数据
- 源代码完全开源可审查

⚠️ **注意**:
- 这是 **Debug 版本**，用于测试
- Release 版本需要额外的代码签名
- 仅从官方渠道下载 APK

### 相关文档

- [README.md](README.md) - 项目介绍
- [INSTALL.md](INSTALL.md) - 详细安装指南
- [DEVELOPMENT.md](DEVELOPMENT.md) - 开发者指南

---

**Cloud Agent Run**: [bc-328671a7-089e-419c-8ad3-0c364639d7cd](https://cursor.com/agents/bc-328671a7-089e-419c-8ad3-0c364639d7cd)

如有问题，请查阅文档或提交 Issue。
