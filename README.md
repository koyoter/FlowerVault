# FlowerVault（获取密码）

一款极简的 Android 密码派生工具：输入主密码 + 站点标识，即点即得 16 位密码并复制到剪贴板。

基于 Kotlin + Jetpack Compose Material3 构建，数据经 Android Keystore AES-GCM 加密后存于 Room（SQLite）。

## 功能

- **首次门禁**：未设置主密码时强制先进入设置页；支持**多主密码**，侧滑抽屉可新增 / 切换 / 删除（级联删除其记录），条目显示备注名与掩码
- **单页主界面**：顶部常驻搜索框（实时过滤、大小写不敏感）；中部历史记录列表（按复制次数降序，含复制 / 删除按钮）；底部输入框默认聚焦，生成后密码进剪贴板并退出应用
- **历史记录**：自动记录每次输入，重复输入合并累加；点条目文本自动填入输入框；"复制"即用当前主密码重新生成并复制
- **安全**：主密码与站点文本均以 Keystore AES-GCM 加密入库；各主密码数据完全隔离

## 密码算法

HmacMD5 派生方案（`HmacMD5(站点标识, 主密码)` 三轮变换 + 大小写规则），输出固定 16 位，无需联网、无云端存储。

## 版本与发布

由 GitHub Actions 自动打包签名发布（[.github/workflows/release.yml](.github/workflows/release.yml)）：

- 打 `v*` 格式的 semver tag（如 `v0.1.0`、`v1.2.3-beta.1`）即触发构建并创建 Release，产物为 `FlowerVault-<版本>.apk`
- `versionCode = MAJOR * 10000 + MINOR * 100 + PATCH`
- 手动触发（workflow_dispatch）产出开发版本 artifact，不入 Release

### 签名配置（仓库 Secrets）

| Secret | 说明 |
| --- | --- |
| `SIGNING_KEYSTORE` | keystore 文件的 Base64 内容 |
| `SIGNING_KEYSTORE_PASSWORD` | keystore 密码 |
| `SIGNING_KEY_ALIAS` | key 别名 |
| `SIGNING_KEY_PASSWORD` | key 密码（未配置时回退为 keystore 密码） |

未配置时回退临时 debug 密钥，产出的 APK 无法被正式签名版本覆盖安装。

## 环境要求

- Android 8.0（API 26）及以上
- 构建需 JDK 17+、Android SDK 35、Gradle 8.14（CI 自动准备）
