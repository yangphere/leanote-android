# Android 平台基线版本与验证证据

查询与验证日期：2026-09-09。

## 版本矩阵

| 组件 | 采用版本 | 依据 |
|---|---:|---|
| JDK | 17 | AGP 9.4 的最低 JDK 为 17。 |
| Gradle Wrapper | 9.6.0 | AGP 9.4 的兼容 Gradle 为 9.6。 |
| Android Gradle Plugin | 9.4.0 | 官方发布说明标明最高支持 API 37、Gradle 9.6、JDK 17 和 Build Tools 36.0.0。 |
| Kotlin / Compose Compiler plugin | 2.3.21 | AGP 9+ 使用内置 Kotlin；Compose Compiler Gradle plugin 与 Kotlin 版本一致。 |
| KSP | 2.3.11 | 与当前内置 Kotlin 构建链配套，供 Hilt 编译器使用。 |
| Compose BOM | 2026.08.00 | Android 官方 Compose BOM 当前稳定版本；Compose 1.12 起要求 compileSdk 37 和 AGP 9。 |
| Hilt | 2.60.1 | 当前稳定 Dagger/Hilt 版本，通过 KSP 接入。 |
| SDK | min 34 / target 36 / compile 37 | 产品 ADR 固定的平台边界。 |

官方来源：

- Android Gradle Plugin 9.4.0 release notes: <https://developer.android.com/build/releases/agp-9-4-0-release-notes>
- Migrate to built-in Kotlin: <https://developer.android.com/build/migrate-to-built-in-kotlin>
- Compose dependencies and compiler setup: <https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler>
- Compose BOM mapping: <https://developer.android.com/develop/ui/compose/bom/bom-mapping>
- Dagger releases: <https://github.com/google/dagger/releases>
- KSP releases: <https://github.com/google/ksp/releases>

## 迁移兼容桥

- Support Library 已统一迁移为 AndroidX。应用依赖仓库仅保留 `google()`、`mavenCentral()` 和 HTTPS JitPack；`settings.gradle` 的 `pluginManagement` 另使用 Gradle Plugin Portal 解析 Gradle 插件，不把它表述为应用依赖仓库。
- 旧 DBFlow 仅升级到仍可解析的 4.2.4 并做编译 API 适配；数据库替换属于后续 Room 任务。
- ButterKnife 10.2.3 仍要求最终编译期 R 常量和 JDK compiler exports，因此暂时启用 `android.nonFinalResIds=false`、`android.enableAppCompileTimeRClass=false` 和显式 `--add-exports`。这些选项在 AGP 10 会移除，必须在升级 AGP 10 前删除 ButterKnife 兼容桥。
- Jetifier 仅用于尚未完成 AndroidX 发布的旧依赖，同样是 AGP 10 前必须清理的过渡项。
- Android 14 图片选择使用系统 Photo Picker；不再在启动时请求已失效的外部存储、相机或电话权限。
- 新版 Joda 对 RFC 3339 解析更严格。时间解析保留目标服务既有的一位数时区 offset 兼容，并将超过毫秒精度的小数秒截到毫秒；发送时始终按 UTC 输出 `Z`，不会把本地时间误标为 UTC。
- 已移除 Bugly upgrade/Beta 组件与应用内 APK 安装权限；仅保留固定版本 `com.tencent.bugly:crashreport:4.1.9.3`，关闭 native crash monitor，并从最终打包中排除 `libBugly_Native.so`。

## 已执行验证

- `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-configuration-cache`：成功；20 个 JVM 测试全部通过，lint 为 0 errors / 284 warnings。
- `.\gradlew.bat assembleDebug`：连续执行成功，第二次复用配置缓存。
- `.\gradlew.bat assembleRelease`（无发布签名材料）：按预期在 `verifyReleaseSigning` 明确失败，未降级为未签名 release 产物。
- APK 元数据：`com.leanote.android`、`minSdk 34`、`targetSdk 36`、`compileSdk 37`；debug APK 由 Android Debug 证书签名。

## 模拟器实测

- API 34 / Android 14 Google APIs x86_64：先卸载应用再安装 debug APK，`LaunchState: COLD`，最终进入旧实现的 `SignInActivity`，无 `AndroidRuntime` 崩溃。
- API 36 / Android 16 Google APIs x86_64：从空白 AVD 安装 debug APK，冷启动进入 `SignInActivity`，无 `AndroidRuntime` 崩溃。
- 两个 AVD 均由调试 root 启动未导出的 `PlatformSmokeActivity`；Hilt Activity 和 Compose 首屏成功渲染，点击“运行检查”后 StateFlow 状态从 0 更新为 1，返回后恢复 `SignInActivity`。
- 两个 AVD 均成功打开系统 `android.provider.action.PICK_IMAGES` 图片选择器并返回 `SignInActivity`。这验证平台入口和返回路径，不替代后续编辑器任务对选取文件复制、正文插入和失败清理的端到端测试。

## 未关闭证据

- 没有受保护发布密钥，尚未验证 release APK/AAB 的生产签名；签名配置只有在 keystore 与 `KEY_ALIAS`、`KEY_PWD`、`KEYSTORE_PWD` 同时存在时才启用，缺失任一项时 release 构建会明确失败而不会产生未签名产物。
- `ApiProvider` 中既有的全信任证书与恒真主机名校验仍然存在。它不是平台构建迁移引入的兼容项，但在网络安全任务移除并完成 `https://me.xiqi.site/` 回归前，不能宣称发布安全边界已经关闭。
- API 34/36 的既有模拟器记录只证明安装、冷启动、调试 Compose/Hilt 入口和系统 Picker 打开/返回；尚未在真实内容导入中验证跨配置重建、正文插入、关系删除与上传，也未完成 API 35+ 手势/三键导航下的系统栏与 IME、API 36 大屏旋转/调整尺寸和真正的 predictive-back 手势证据。
- debug APK 已通过 16 KB ZIP 对齐检查；最终包中未发现 Bugly native 库，仅保留 AndroidX libandroidx.graphics.path.so。对四个 ABI 的 ELF PT_LOAD 对齐检查均为 0x4000，但仍缺少受保护 release APK/AAB 与真实 16 KB page-size 运行目标证据。
- 本任务没有执行 Room、同步、Leanote API、WebView/编辑器架构或业务页面迁移，也没有使用真实服务测试账号。
